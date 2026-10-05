package com.example.dailytrack_mobile

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Keeps the last crash's stack trace on the phone, so the next launch can offer
 * to copy it — the whole story of a crash without needing adb. Release builds
 * are obfuscated; the build's mapping.txt turns the trace back into code.
 */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val dir = context.applicationContext.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(dir, FILE_NAME).writeText(
                    "DailyTrack ${BuildConfig.VERSION_NAME} · ${Date()} · thread ${thread.name}\n\n" +
                        Log.getStackTraceString(error)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
