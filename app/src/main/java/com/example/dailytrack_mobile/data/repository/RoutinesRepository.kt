package com.example.dailytrack_mobile.data.repository

import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.local.routines.RoutinesSnapshot
import com.example.dailytrack_mobile.data.local.routines.RoutinesStore
import com.example.dailytrack_mobile.data.remote.api.DailyTrackApi
import com.example.dailytrack_mobile.data.remote.dto.RoutineArchiveRequestDto
import com.example.dailytrack_mobile.data.remote.dto.RoutineCheckInDto
import com.example.dailytrack_mobile.data.remote.dto.RoutineDto
import com.example.dailytrack_mobile.data.remote.dto.RoutineRequestDto
import com.example.dailytrack_mobile.data.remote.dto.SaveRoutineCheckInsRequestDto
import com.example.dailytrack_mobile.domain.routines.CheckIn
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.IntervalUnit
import com.example.dailytrack_mobile.domain.routines.Routine
import com.example.dailytrack_mobile.domain.routines.RoutineEngine
import com.example.dailytrack_mobile.domain.routines.RoutineKind
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in person's routines and check-ins. The phone's copy is the source
 * for everything shown: an answer is saved on the phone first, queued, and sent
 * when the server can be reached, so ticking works offline and the nightly
 * notification never waits on the network. Demo mode keeps its own copy in
 * memory and never touches the server or the real one.
 */
@Singleton
class RoutinesRepository @Inject constructor(
    private val api: DailyTrackApi,
    private val store: RoutinesStore,
    private val authManager: AuthManager,
    private val demoModeManager: DemoModeManager
) {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow<RoutinesSnapshot?>(null)

    /** Null until [load] has run for whoever is signed in. */
    val snapshot: StateFlow<RoutinesSnapshot?> = _snapshot.asStateFlow()

    private var loadedFor: String? = null
    private var demoSnapshot: RoutinesSnapshot? = null

    /** The phone's copy for whoever is signed in (or the demo), without the network. */
    suspend fun load(): RoutinesSnapshot = mutex.withLock { currentLocked() }

    /**
     * [owner]'s routines, shared with you: fetched fresh, never stored, so your own
     * copy, alarms, check-ins and widget never see them.
     */
    suspend fun fetchShared(owner: String): Result<RoutinesSnapshot> {
        if (!awaitAuth()) return Result.failure(Exception("Not signed in"))
        return call { api.getSharedRoutines(owner) }.mapCatching { body ->
            if (!body.success) throw Exception(body.message ?: "Couldn't load their routines")
            RoutinesSnapshot(owner = owner, routines = body.routines, checkins = body.checkins, syncedAt = System.currentTimeMillis())
        }
    }

    /**
     * Sends queued answers, then fetches everything. On failure the phone's copy
     * stays as it was, queued answers included.
     */
    suspend fun refresh(): Result<Unit> {
        if (isDemo()) {
            load()
            return Result.success(Unit)
        }
        flush()
        val owner = load().owner ?: return Result.failure(Exception("Not signed in"))
        if (!awaitAuth()) return Result.failure(Exception("Not signed in"))
        val response = call { api.getRoutines() }
        return response.mapCatching { body ->
            if (!body.success) throw Exception(body.message ?: "Couldn't load routines")
            mutex.withLock {
                val current = currentLocked()
                if (current.owner != owner) return@withLock
                // Queued answers win over what the server had.
                val queued = current.pending.map { key(it) }.toSet()
                val merged = body.checkins.filterNot { key(it) in queued } +
                    current.pending.filter { it.status != null }
                saveLocked(
                    RoutinesSnapshot(
                        owner = owner,
                        routines = body.routines,
                        checkins = merged,
                        pending = current.pending,
                        syncedAt = System.currentTimeMillis()
                    )
                )
            }
        }.onFailure { error ->
            // Access to Routines was taken away: forget the copy so nothing stale is shown.
            if (error is AccessLostException) mutex.withLock {
                store.clear(owner)
                saveLocked(RoutinesSnapshot(owner = owner))
            }
        }
    }

    /**
     * Records an answer (null clears it) on the phone straight away and queues
     * it. Call [flush] afterwards to send it.
     */
    suspend fun setCheckIn(routineId: Long, date: LocalDate, status: CheckInStatus?, note: String? = null) =
        setCheckIns(listOf(RoutineAnswer(routineId, date, status, note)))

    /** Several answers at once, e.g. filling in the days before a routine was added. */
    suspend fun setCheckIns(answers: List<RoutineAnswer>) {
        if (answers.isEmpty()) return
        mutex.withLock {
            val current = currentLocked()
            val entries = answers.map { answer ->
                RoutineCheckInDto(
                    routineId = answer.routineId,
                    date = answer.date.toString(),
                    status = answer.status?.key,
                    note = answer.note?.trim()?.take(MAX_NOTE)?.takeIf { it.isNotEmpty() }
                )
            }
            val keys = entries.map { key(it) }.toSet()
            val checkins = current.checkins.filterNot { key(it) in keys } + entries.filter { it.status != null }
            val pending = if (loadedFor == DEMO) emptyList()
            else current.pending.filterNot { key(it) in keys } + entries
            saveLocked(current.copy(checkins = checkins, pending = pending))
        }
    }

    /**
     * Sends queued answers, in batches the server accepts. Whatever the server
     * took (or can never take) leaves the queue.
     */
    suspend fun flush(): Result<Unit> {
        if (isDemo()) return Result.success(Unit)
        val queued = load().pending
        if (queued.isEmpty()) return Result.success(Unit)
        if (!awaitAuth()) return Result.failure(Exception("Not signed in"))
        for (sending in queued.chunked(MAX_BATCH)) {
            val result = call { api.saveRoutineCheckIns(SaveRoutineCheckInsRequestDto(sending)) }
                .mapCatching { body ->
                    if (!body.success) throw Exception(body.message ?: "Couldn't save check-ins")
                    mutex.withLock {
                        val current = currentLocked()
                        // Only what was sent: an answer changed meanwhile is a new entry and stays.
                        saveLocked(current.copy(pending = current.pending.filterNot { it in sending }))
                    }
                }
            if (result.isFailure) return result
        }
        return Result.success(Unit)
    }

    suspend fun create(request: RoutineRequestDto): Result<RoutineDto> {
        if (isDemo()) return mutex.withLock {
            val current = currentLocked()
            val routine = request.toDto(
                id = (current.routines.maxOfOrNull { it.id } ?: 0L) + 1,
                sortOrder = (current.routines.maxOfOrNull { it.sortOrder } ?: -1) + 1
            ).copy(createdAt = LocalDate.now().toString())
            saveLocked(current.copy(routines = current.routines + routine))
            Result.success(routine)
        }
        if (!awaitAuth()) return Result.failure(Exception("Not signed in"))
        return call { api.createRoutine(request) }
            .mapCatching { it.routine ?: throw Exception(it.message ?: "Couldn't create the routine") }
            .onSuccess { routine -> replaceRoutine(routine) }
    }

    suspend fun update(id: Long, request: RoutineRequestDto): Result<RoutineDto> {
        if (isDemo()) return mutex.withLock {
            val current = currentLocked()
            val existing = current.routines.firstOrNull { it.id == id }
                ?: return@withLock Result.failure(Exception("Routine not found"))
            val routine = request.toDto(id, existing.sortOrder)
                .copy(archived = existing.archived, createdAt = existing.createdAt)
            saveLocked(current.copy(routines = current.routines.map { if (it.id == id) routine else it }))
            Result.success(routine)
        }
        if (!awaitAuth()) return Result.failure(Exception("Not signed in"))
        return call { api.updateRoutine(id, request) }
            .mapCatching { it.routine ?: throw Exception(it.message ?: "Couldn't save the routine") }
            .onSuccess { routine -> replaceRoutine(routine) }
    }

    suspend fun setArchived(id: Long, archived: Boolean): Result<RoutineDto> {
        if (isDemo()) return mutex.withLock {
            val current = currentLocked()
            val existing = current.routines.firstOrNull { it.id == id }
                ?: return@withLock Result.failure(Exception("Routine not found"))
            val routine = existing.copy(archived = archived)
            saveLocked(current.copy(routines = current.routines.map { if (it.id == id) routine else it }))
            Result.success(routine)
        }
        if (!awaitAuth()) return Result.failure(Exception("Not signed in"))
        return call { api.setRoutineArchived(id, RoutineArchiveRequestDto(archived)) }
            .mapCatching { it.routine ?: throw Exception(it.message ?: "Couldn't update the routine") }
            .onSuccess { routine -> replaceRoutine(routine) }
    }

    /** Deletes the routine and its whole history. */
    suspend fun delete(id: Long): Result<Unit> {
        if (!isDemo()) {
            if (!awaitAuth()) return Result.failure(Exception("Not signed in"))
            val result = call { api.deleteRoutine(id) }
                .mapCatching { if (!it.success) throw Exception(it.message ?: "Couldn't delete the routine") }
            if (result.isFailure) return result
        }
        mutex.withLock {
            val current = currentLocked()
            saveLocked(
                current.copy(
                    routines = current.routines.filterNot { it.id == id },
                    checkins = current.checkins.filterNot { it.routineId == id },
                    pending = current.pending.filterNot { it.routineId == id }
                )
            )
        }
        return Result.success(Unit)
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private suspend fun replaceRoutine(routine: RoutineDto) = mutex.withLock {
        val current = currentLocked()
        val routines = if (current.routines.any { it.id == routine.id }) {
            current.routines.map { if (it.id == routine.id) routine else it }
        } else {
            current.routines + routine
        }
        saveLocked(current.copy(routines = routines))
    }

    /** Loads the right copy if who's signed in (or demo mode) changed. Caller holds [mutex]. */
    private suspend fun currentLocked(): RoutinesSnapshot {
        val scope = if (isDemo()) DEMO else authManager.userEmailFlow.first()?.trim()?.lowercase()
        val loaded = _snapshot.value
        if (loaded != null && scope == loadedFor) return loaded
        val snapshot = when (scope) {
            null -> RoutinesSnapshot()
            DEMO -> demoSnapshot ?: DemoRoutines.seed(LocalDate.now()).also { demoSnapshot = it }
            else -> withContext(Dispatchers.IO) { store.read(scope) } ?: RoutinesSnapshot(owner = scope)
        }
        loadedFor = scope
        _snapshot.value = snapshot
        return snapshot
    }

    /** Caller holds [mutex]. */
    private suspend fun saveLocked(snapshot: RoutinesSnapshot) {
        _snapshot.value = snapshot
        when (val owner = loadedFor) {
            null -> Unit
            DEMO -> demoSnapshot = snapshot
            else -> withContext(Dispatchers.IO) { store.write(owner, snapshot) }
        }
    }

    private suspend fun isDemo(): Boolean = demoModeManager.isDemoModeEnabled()

    /**
     * Waits briefly for the saved sign-in token. From a cold start (the nightly
     * alarm) the network layer's in-memory copy fills in a moment after launch.
     */
    private suspend fun awaitAuth(): Boolean {
        if (authManager.getCachedToken() != null) return true
        withTimeoutOrNull(3_000) { authManager.authTokenFlow.first() } ?: return false
        repeat(40) {
            if (authManager.getCachedToken() != null) return true
            delay(50)
        }
        return authManager.getCachedToken() != null
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val response = block()
        val body = response.body()
        when {
            response.isSuccessful && body != null -> Result.success(body)
            response.code() == 403 -> Result.failure(AccessLostException(serverMessage(response)))
            else -> Result.failure(Exception(serverMessage(response) ?: "Server error (${response.code()})"))
        }
    } catch (e: CancellationException) {
        throw e // a timeout or a closed screen, not a failure to report
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun serverMessage(response: Response<*>): String? = response.errorBody()?.string()?.let {
        runCatching { org.json.JSONObject(it).optString("message") }.getOrNull()?.takeIf { m -> m.isNotBlank() }
    }

    private class AccessLostException(message: String?) : Exception(message ?: "You don't have access to Routines")

    private fun key(entry: RoutineCheckInDto) = entry.routineId to entry.date

    private companion object {
        const val DEMO = "demo"
        const val MAX_NOTE = 120

        /** Under the server's 500-per-request limit. */
        const val MAX_BATCH = 400
    }
}

/** An answer to record; a null [status] clears that day. */
data class RoutineAnswer(
    val routineId: Long,
    val date: LocalDate,
    val status: CheckInStatus?,
    val note: String? = null
)

// ── Mapping ──────────────────────────────────────────────────────────────────

fun RoutineDto.toRoutine(): Routine? = runCatching {
    Routine(
        id = id,
        name = name,
        emoji = emoji,
        kind = RoutineKind.from(kind),
        schedule = RoutineSchedule.from(schedule),
        days = days,
        target = target,
        every = every,
        unit = IntervalUnit.from(unit),
        startDate = LocalDate.parse(startDate),
        endDate = endDate?.let(LocalDate::parse),
        sortOrder = sortOrder,
        archived = archived,
        createdOn = createdAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    )
}.getOrNull()

fun RoutineCheckInDto.toCheckIn(): CheckIn? {
    val parsed = CheckInStatus.from(status) ?: return null
    return runCatching { CheckIn(routineId, LocalDate.parse(date), parsed, note) }.getOrNull()
}

fun RoutinesSnapshot.engine(): RoutineEngine =
    RoutineEngine(routines.mapNotNull { it.toRoutine() }, checkins.mapNotNull { it.toCheckIn() })

private fun RoutineRequestDto.toDto(id: Long, sortOrder: Int) = RoutineDto(
    id = id,
    name = name,
    emoji = emoji.ifBlank { null },
    kind = kind,
    schedule = schedule,
    days = days,
    target = target,
    every = every,
    unit = unit,
    startDate = startDate,
    endDate = endDate.ifBlank { null },
    sortOrder = sortOrder
)
