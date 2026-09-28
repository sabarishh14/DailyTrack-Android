package com.example.dailytrack_mobile.presentation.screens.settings.components

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings
import com.example.dailytrack_mobile.notification.routines.AlarmSounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks an alarm's sound: the phone's default, silent, one of its alarm tones or
 * ringtones, or an audio file. Tapping one chooses it and plays a moment of it,
 * at the alarm volume, so it can be heard before it rings for real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmSoundSheet(
    title: String,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preview = remember { SoundPreview(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    var current by remember { mutableStateOf(selected) }

    val alarmTones by produceState(emptyList<AlarmSounds.Sound>()) {
        value = withContext(Dispatchers.IO) { AlarmSounds.list(context, RingtoneManager.TYPE_ALARM) }
    }
    val ringtones by produceState(emptyList<AlarmSounds.Sound>()) {
        value = withContext(Dispatchers.IO) { AlarmSounds.list(context, RingtoneManager.TYPE_RINGTONE) }
    }
    val defaultTitle by produceState("Default") {
        value = withContext(Dispatchers.IO) { AlarmSounds.title(context, null) }
    }
    val listed = remember(alarmTones, ringtones) { (alarmTones + ringtones).map { it.value }.toSet() }
    val custom = current?.takeIf { it != RoutineAlarmSettings.SILENT && it !in listed }
    val customTitle by produceState<String?>(null, custom) {
        value = custom?.let { withContext(Dispatchers.IO) { AlarmSounds.title(context, it) } }
    }

    fun choose(value: String?) {
        current = value
        onPick(value)
        preview.play(AlarmSounds.resolve(context, value))
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Kept readable after a restart, so the alarm can still play it.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            choose(uri.toString())
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            preview.stop()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap one to hear it. It plays at your alarm volume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "default") { SoundRow("🔔", defaultTitle, current == null) { choose(null) } }
            item(key = "silent") {
                SoundRow("🔕", "Silent · vibrate only", current == RoutineAlarmSettings.SILENT) { choose(RoutineAlarmSettings.SILENT) }
            }
            if (custom != null) {
                item(key = "custom") { SoundRow("🎵", customTitle ?: "Your sound", true) { choose(custom) } }
            }
            item(key = "file") { SoundRow("📁", "Pick a file…", selected = null) { filePicker.launch(arrayOf("audio/*")) } }
            if (alarmTones.isNotEmpty()) {
                item(key = "alarms-title") { GroupTitle("Alarm sounds") }
                items(alarmTones, key = { "alarm-${it.value}" }) { sound ->
                    SoundRow(null, sound.title, current == sound.value) { choose(sound.value) }
                }
            }
            if (ringtones.isNotEmpty()) {
                item(key = "ringtones-title") { GroupTitle("Ringtones") }
                items(ringtones, key = { "ringtone-${it.value}" }) { sound ->
                    SoundRow(null, sound.title, current == sound.value) { choose(sound.value) }
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** [selected] null: an action row, with no radio. */
@Composable
private fun SoundRow(emoji: String?, title: String, selected: Boolean?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (emoji != null) 10.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji != null) {
            Text(text = emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected != null) {
            RadioButton(selected = selected, onClick = onClick)
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

/** Plays a few seconds of a sound on the alarm stream. */
private class SoundPreview(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    fun play(uri: Uri?) {
        stop()
        uri ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.getOrNull()
        handler.postDelayed({ stop() }, PREVIEW_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private companion object {
        const val PREVIEW_MS = 7_000L
    }
}
