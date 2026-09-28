package com.example.dailytrack_mobile.notification.routines

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.dailytrack_mobile.R
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings.AlarmKind
import com.example.dailytrack_mobile.data.repository.RoutinesRepository
import com.example.dailytrack_mobile.data.repository.engine
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Rings a Routines alarm like the clock does: the chosen sound on the alarm
 * volume (so it rings on silent), vibration, and a screen over the lock screen.
 * The sound is played here rather than by a notification so the alarm screen
 * can silence it the moment it's opened, and so any sound can be chosen.
 *
 * Silenced or timed out, it leaves an ordinary quiet notification behind, so the
 * routine (or tonight's check-in) can still be answered from the shade.
 */
@AndroidEntryPoint
class RoutineAlarmService : Service() {

    @Inject lateinit var repository: RoutinesRepository

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focus: AudioFocusRequest? = null
    private var alarm: Alarm? = null

    private val timeout = Runnable { silence() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> Alarm.from(intent)?.let(::ring) ?: stopSelf()
            ACTION_SILENCE -> silence()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlayback()
        ringing = null
        super.onDestroy()
    }

    private fun ring(next: Alarm) {
        stopPlayback()
        alarm = next
        ensureChannel(this)
        try {
            val notification = ringingNotification(this, next)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(RING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(RING_ID, notification)
            }
        } catch (e: Exception) {
            // Not allowed to ring from here: fall back to a ringing notification.
            Log.w(TAG, "Couldn't start the alarm in the foreground", e)
            RoutineReminderNotifier.ringWithoutService(this, next)
            stopSelf()
            return
        }
        ringing = next
        startSound(next)
        if (next.vibrate) startVibration()
        handler.postDelayed(timeout, RING_FOR_MS)
    }

    /** Stops the ringing but leaves something to answer from the shade. */
    private fun silence() {
        val current = alarm
        stopPlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        ringing = null
        alarm = null
        if (current != null && !current.test) leaveQuietNotification(current)
        stopSelf()
    }

    private fun leaveQuietNotification(alarm: Alarm) {
        val context = applicationContext
        if (alarm.kind == AlarmKind.CHECK_IN) {
            scope.launch {
                runCatching { RoutineCheckInNotifier.ask(context, repository.load().engine(), alarm.localDate, quiet = true) }
            }
        } else {
            RoutineReminderNotifier.showQuiet(context, alarm.routineId, alarm.localDate, alarm.title, alarm.text)
        }
    }

    // ── Sound and vibration ──────────────────────────────────────────────────

    private fun startSound(alarm: Alarm) {
        val uri = alarm.sound?.let(Uri::parse) ?: return // silent: vibration only
        requestFocus()
        if (!play(uri, alarm.gentle)) {
            AlarmSounds.defaultAlarm(this)?.takeIf { it != uri }?.let { play(it, alarm.gentle) }
        }
    }

    private fun play(uri: Uri, gentle: Boolean): Boolean = try {
        val next = MediaPlayer().apply {
            setAudioAttributes(ALARM_AUDIO)
            setDataSource(this@RoutineAlarmService, uri)
            isLooping = true
            setVolume(if (gentle) GENTLE_START else 1f, if (gentle) GENTLE_START else 1f)
            setOnPreparedListener { prepared ->
                if (player === prepared) {
                    prepared.start()
                    if (gentle) fadeIn(prepared)
                }
            }
            prepareAsync()
        }
        player = next
        true
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't play $uri", e)
        false
    }

    /** Eases the volume up to full over [RoutineAlarmSettings.GENTLE_RAMP_MS]. */
    private fun fadeIn(target: MediaPlayer) {
        val started = SystemClock.elapsedRealtime()
        handler.post(object : Runnable {
            override fun run() {
                if (player !== target) return
                val t = ((SystemClock.elapsedRealtime() - started).toFloat() / RoutineAlarmSettings.GENTLE_RAMP_MS).coerceIn(0f, 1f)
                val volume = GENTLE_START + (1f - GENTLE_START) * t * t
                runCatching { target.setVolume(volume, volume) }
                if (t < 1f) handler.postDelayed(this, 500)
            }
        })
    }

    private fun startVibration() {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700, 1100), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            device.vibrate(effect, ALARM_AUDIO)
        }
        vibrator = device
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ALARM_AUDIO)
            .build()
        runCatching { getSystemService(AudioManager::class.java)?.requestAudioFocus(request) }
        focus = request
    }

    private fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        focus?.let { request -> runCatching { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request) } }
        focus = null
    }

    /** One alarm to ring, carried in the intent so the service needn't look anything up. */
    data class Alarm(
        val kind: AlarmKind,
        /** The routine for a reminder; -1 for the check-in or a test. */
        val routineId: Long,
        val date: String,
        val emoji: String?,
        val name: String,
        val text: String,
        /** The sound to play, or null for silent (vibration only). */
        val sound: String?,
        val vibrate: Boolean,
        val gentle: Boolean,
        val snoozeMinutes: Int,
        val test: Boolean = false
    ) {
        val localDate: LocalDate get() = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now() }
        val title: String get() = listOfNotNull(emoji?.takeIf { it.isNotBlank() }, name).joinToString(" ")

        fun putInto(intent: Intent): Intent = intent
            .putExtra(EXTRA_KIND, kind.name)
            .putExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, routineId)
            .putExtra(RoutineCheckInReceiver.EXTRA_DATE, date)
            .putExtra(EXTRA_EMOJI, emoji)
            .putExtra(EXTRA_NAME, name)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_SOUND, sound)
            .putExtra(EXTRA_VIBRATE, vibrate)
            .putExtra(EXTRA_GENTLE, gentle)
            .putExtra(EXTRA_SNOOZE, snoozeMinutes)
            .putExtra(EXTRA_TEST, test)

        companion object {
            fun from(intent: Intent): Alarm? {
                val kind = runCatching { AlarmKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrNull() ?: return null
                return Alarm(
                    kind = kind,
                    routineId = intent.getLongExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, -1L),
                    date = intent.getStringExtra(RoutineCheckInReceiver.EXTRA_DATE) ?: LocalDate.now().toString(),
                    emoji = intent.getStringExtra(EXTRA_EMOJI),
                    name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
                    text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                    sound = intent.getStringExtra(EXTRA_SOUND),
                    vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true),
                    gentle = intent.getBooleanExtra(EXTRA_GENTLE, false),
                    snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE, 10),
                    test = intent.getBooleanExtra(EXTRA_TEST, false)
                )
            }

            /** An alarm with the sound and behaviour from [settings]. */
            fun of(
                context: Context,
                settings: RoutineAlarmSettings.Settings,
                kind: AlarmKind,
                routineId: Long,
                date: LocalDate,
                emoji: String?,
                name: String,
                text: String,
                test: Boolean = false
            ) = Alarm(
                kind = kind,
                routineId = routineId,
                date = date.toString(),
                emoji = emoji,
                name = name,
                text = text,
                sound = AlarmSounds.resolve(context, settings.soundFor(kind))?.toString(),
                vibrate = settings.vibrate,
                gentle = settings.gentle,
                snoozeMinutes = settings.snoozeMinutes,
                test = test
            )
        }
    }

    companion object {
        private const val TAG = "RoutineAlarm"
        private const val ACTION_RING = "com.example.dailytrack_mobile.ACTION_ROUTINE_ALARM_RING"
        private const val ACTION_SILENCE = "com.example.dailytrack_mobile.ACTION_ROUTINE_ALARM_SILENCE"
        private const val RING_CHANNEL_ID = "routine_alarm_ringing"
        private const val RING_ID = 45_000
        private const val GENTLE_START = 0.08f
        /** Like a clock alarm, it gives up after a while rather than ringing forever. */
        const val RING_FOR_MS = 5 * 60_000L

        internal const val EXTRA_KIND = "alarm_kind"
        internal const val EXTRA_EMOJI = "alarm_emoji"
        internal const val EXTRA_NAME = "alarm_name"
        internal const val EXTRA_TEXT = "alarm_text"
        private const val EXTRA_SOUND = "alarm_sound"
        private const val EXTRA_VIBRATE = "alarm_vibrate"
        private const val EXTRA_GENTLE = "alarm_gentle"
        internal const val EXTRA_SNOOZE = "alarm_snooze"
        internal const val EXTRA_TEST = "alarm_test"

        private val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** What's ringing right now, if anything. */
        @Volatile
        var ringing: Alarm? = null
            private set

        /** Starts ringing. False when Android wouldn't let it start, so the caller can fall back. */
        fun ring(context: Context, alarm: Alarm): Boolean = try {
            ContextCompat.startForegroundService(context, alarm.putInto(Intent(context, RoutineAlarmService::class.java).setAction(ACTION_RING)))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start the alarm", e)
            false
        }

        /** Stops the sound; the alarm stays answerable from a quiet notification. */
        fun silence(context: Context) {
            if (ringing == null) return
            try {
                context.startService(Intent(context, RoutineAlarmService::class.java).setAction(ACTION_SILENCE))
            } catch (e: Exception) {
                stop(context)
            }
        }

        /** Stops it altogether: answered, snoozed or dismissed. */
        fun stop(context: Context) {
            context.stopService(Intent(context, RoutineAlarmService::class.java))
        }

        /** Stops a routine's alarm if that's what's ringing, e.g. once it's answered in the app. */
        fun stopIfFor(context: Context, routineId: Long) {
            if (ringing?.routineId == routineId) stop(context)
        }

        private fun ensureChannel(context: Context) {
            // Silent itself: the service plays the sound, so the screen can stop it.
            val channel = NotificationChannel(RING_CHANNEL_ID, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A routine alarm or the nightly check-in while it rings"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun ringingNotification(context: Context, alarm: Alarm): Notification {
            val fullScreen = PendingIntent.getActivity(
                context, 1, RoutineAlarmActivity.intent(context, alarm, opened = false),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val open = PendingIntent.getActivity(
                context, 2, RoutineAlarmActivity.intent(context, alarm, opened = true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, RING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(alarm.title)
                .setContentText(alarm.text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(open)
                .setOngoing(true)
            when {
                alarm.test -> builder.addAction(0, "Stop", action(context, RoutineCheckInReceiver.ACTION_DISMISS, alarm, 10))
                alarm.kind == AlarmKind.CHECK_IN -> builder
                    .addAction(0, "💤 ${alarm.snoozeMinutes} min", action(context, RoutineCheckInReceiver.ACTION_SNOOZE_CHECK_IN, alarm, 11))
                    .addAction(0, "Later", action(context, RoutineCheckInReceiver.ACTION_LATER_CHECK_IN, alarm, 12))
                else -> builder
                    .addAction(0, "❤️ Done", answer(context, alarm))
                    .addAction(0, "💤 ${alarm.snoozeMinutes} min", action(context, RoutineCheckInReceiver.ACTION_SNOOZE, alarm, 13))
                    .addAction(0, "Dismiss", action(context, RoutineCheckInReceiver.ACTION_DISMISS, alarm, 14))
            }
            return builder.build()
        }

        private fun action(context: Context, action: String, alarm: Alarm, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, RoutineCheckInReceiver::class.java)
                    .setAction(action)
                    .putExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, alarm.routineId)
                    .putExtra(RoutineCheckInReceiver.EXTRA_DATE, alarm.date),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun answer(context: Context, alarm: Alarm): PendingIntent =
            PendingIntent.getBroadcast(
                context, 15,
                Intent(context, RoutineCheckInReceiver::class.java)
                    .setAction(RoutineCheckInReceiver.ACTION_ANSWER)
                    .putExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, alarm.routineId)
                    .putExtra(RoutineCheckInReceiver.EXTRA_DATE, alarm.date)
                    .putExtra(RoutineCheckInReceiver.EXTRA_STATUS, CheckInStatus.DONE.key)
                    .putExtra(RoutineCheckInReceiver.EXTRA_FROM_REMINDER, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
