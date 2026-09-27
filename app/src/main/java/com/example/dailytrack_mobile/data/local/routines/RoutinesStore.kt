package com.example.dailytrack_mobile.data.local.routines

import android.content.Context
import com.example.dailytrack_mobile.data.remote.dto.RoutineCheckInDto
import com.example.dailytrack_mobile.data.remote.dto.RoutineDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the phone knows about one person's routines. */
@JsonClass(generateAdapter = true)
data class RoutinesSnapshot(
    /** Whose routines these are; a copy left by someone else is never shown. */
    @Json(name = "owner") val owner: String? = null,
    @Json(name = "routines") val routines: List<RoutineDto> = emptyList(),
    /** Answers as the phone sees them: the server's, with [pending] applied on top. */
    @Json(name = "checkins") val checkins: List<RoutineCheckInDto> = emptyList(),
    /** Answers not yet on the server, oldest first. A null status clears that day. */
    @Json(name = "pending") val pending: List<RoutineCheckInDto> = emptyList(),
    @Json(name = "synced_at") val syncedAt: Long? = null
)

/**
 * Keeps each person's snapshot in its own file, so the nightly check-in works
 * offline and from a cold start, and answers not yet sent survive until they
 * reach the server, even if someone else signs in on this phone meanwhile.
 */
@Singleton
class RoutinesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    moshi: Moshi
) {
    private val adapter = moshi.adapter(RoutinesSnapshot::class.java)

    /** The file only names a hash; the owner inside is what's checked. */
    private fun fileFor(owner: String) =
        File(context.filesDir, "routines_${owner.hashCode().toUInt().toString(16)}.json")

    fun read(owner: String): RoutinesSnapshot? = runCatching {
        fileFor(owner).takeIf { it.exists() }?.readText()?.let { adapter.fromJson(it) }
    }.getOrNull()?.takeIf { it.owner == owner }

    /** Written to a temporary file first, so a crash mid-write can't corrupt the copy. */
    fun write(owner: String, snapshot: RoutinesSnapshot) {
        runCatching {
            val file = fileFor(owner)
            val temp = File(context.filesDir, "${file.name}.tmp")
            temp.writeText(adapter.toJson(snapshot))
            if (!temp.renameTo(file)) {
                file.writeText(adapter.toJson(snapshot))
                temp.delete()
            }
        }
    }

    fun clear(owner: String) {
        runCatching { fileFor(owner).delete() }
    }
}
