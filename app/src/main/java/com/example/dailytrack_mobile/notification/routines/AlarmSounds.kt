package com.example.dailytrack_mobile.notification.routines

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings

/** The sounds an alarm can ring with: the phone's alarm tones and ringtones, or a file. */
object AlarmSounds {

    data class Sound(val title: String, val value: String)

    /** What to play for a stored choice: null for silent, the phone's alarm when unset or gone. */
    fun resolve(context: Context, value: String?): Uri? = when (value) {
        RoutineAlarmSettings.SILENT -> null
        null -> defaultAlarm(context)
        else -> Uri.parse(value)
    }

    fun defaultAlarm(context: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** "Default (Morning Glory)", "Silent", a tone's name or a file's name. */
    fun title(context: Context, value: String?): String = when (value) {
        RoutineAlarmSettings.SILENT -> "Silent"
        null -> "Default" + (defaultAlarm(context)?.let { uri -> nameOf(context, uri)?.let { " ($it)" } }.orEmpty())
        else -> nameOf(context, Uri.parse(value)) ?: "Custom sound"
    }

    /** The phone's alarm tones, or its ringtones. Reads the media store, so call off the main thread. */
    fun list(context: Context, type: Int): List<Sound> = runCatching {
        val manager = RingtoneManager(context).apply { setType(type) }
        val cursor = manager.cursor
        buildList {
            while (cursor.moveToNext()) {
                val uri = manager.getRingtoneUri(cursor.position) ?: continue
                add(Sound(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX), uri.toString()))
            }
        }.distinctBy { it.value }
    }.getOrDefault(emptyList())

    private fun nameOf(context: Context, uri: Uri): String? {
        if (uri.scheme == "content" && uri.authority != "media" && !uri.toString().startsWith("content://settings")) {
            // A picked file: its display name, without the extension.
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) return c.getString(0)?.substringBeforeLast('.')
                }
            }
        }
        return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
    }
}
