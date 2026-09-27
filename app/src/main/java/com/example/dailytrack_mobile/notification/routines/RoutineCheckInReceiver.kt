package com.example.dailytrack_mobile.notification.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.data.repository.RoutinesRepository
import com.example.dailytrack_mobile.data.repository.engine
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

/**
 * The nightly alarm (start the check-in), the notification's answer buttons,
 * and boot / app updates (set the alarm again). An answer is saved on the
 * phone and the notification moves on straight away; sending it to the server
 * comes after and may wait for a connection.
 */
@AndroidEntryPoint
class RoutineCheckInReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: RoutinesRepository
    @Inject lateinit var settings: RoutineCheckInSettings
    @Inject lateinit var scheduler: RoutineCheckInScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val result = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CHECK_IN -> {
                        scheduler.schedule(settings.current())
                        // Best effort: an offline phone asks from its own copy.
                        withTimeoutOrNull(SYNC_TIMEOUT_MS) { repository.refresh() }
                        RoutineCheckInNotifier.ask(appContext, repository.load().engine(), LocalDate.now())
                    }
                    ACTION_ANSWER -> answer(appContext, intent)
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                        scheduler.schedule(settings.current())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Routine check-in step failed", e)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun answer(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L)
        val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val status = CheckInStatus.from(intent.getStringExtra(EXTRA_STATUS))
        if (routineId < 0 || date == null || status == null) return
        repository.setCheckIn(routineId, date, status)
        RoutineCheckInNotifier.afterAnswer(context, repository.load().engine(), date)
        withTimeoutOrNull(SYNC_TIMEOUT_MS) { repository.flush() }
    }

    companion object {
        const val ACTION_CHECK_IN = "com.example.dailytrack_mobile.ACTION_ROUTINE_CHECK_IN"
        const val ACTION_ANSWER = "com.example.dailytrack_mobile.ACTION_ROUTINE_ANSWER"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_DATE = "date"
        const val EXTRA_STATUS = "status"

        private const val TAG = "RoutineCheckIn"
        private const val SYNC_TIMEOUT_MS = 8_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
