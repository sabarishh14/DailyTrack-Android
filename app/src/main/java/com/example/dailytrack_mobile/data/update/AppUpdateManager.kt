package com.example.dailytrack_mobile.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.dailytrack_mobile.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSizeInBytes: Long,
    val publishedAt: String,
    val isUpdateAvailable: Boolean
) {
    val formattedSize: String
        get() {
            if (fileSizeInBytes <= 0) return ""
            val mb = fileSizeInBytes.toDouble() / (1024 * 1024)
            return String.format(Locale.US, "%.1f MB", mb)
        }
}

sealed interface UpdateDownloadProgress {
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Float // 0.0 to 1.0
    ) : UpdateDownloadProgress
    data class Completed(val apkFile: File) : UpdateDownloadProgress
}

@Singleton
class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Public GitHub Releases URL for DailyTrack-Android
    private val repoOwner = "sabarishh14"
    private val repoName = "DailyTrack-Android"
    private val releasesLatestUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"

    /**
     * Checks the latest GitHub release for DailyTrack-Mobile.
     * Compares remote version with current BuildConfig.VERSION_NAME.
     */
    suspend fun checkForUpdates(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(releasesLatestUrl)
                .header("User-Agent", "DailyTrack-Mobile-App")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.code == 404) {
                // No releases published yet on the repository
                return@withContext Result.success(null)
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to check for updates (HTTP ${response.code})")
                )
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(
                Exception("Empty response received from update server")
            )

            val json = JSONObject(responseBody)
            val rawTagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", rawTagName)
            val releaseNotes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            // Find an APK asset in the release
            val assetsArray = json.optJSONArray("assets")
            var apkDownloadUrl: String? = null
            var apkFileName: String? = null
            var apkFileSize: Long = 0L

            if (assetsArray != null && assetsArray.length() > 0) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", "")
                        apkFileName = assetName
                        apkFileSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkDownloadUrl.isNullOrBlank()) {
                return@withContext Result.failure(
                    Exception("Release found ($rawTagName), but no .apk package was attached to it.")
                )
            }

            val cleanRemoteVersion = rawTagName.removePrefix("v").removePrefix("V").trim()
            val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()

            val isNewer = isNewerVersion(current = currentVersion, remote = cleanRemoteVersion)

            val updateInfo = AppUpdateInfo(
                versionName = cleanRemoteVersion,
                releaseTitle = releaseTitle.ifBlank { "DailyTrack $rawTagName" },
                releaseNotes = releaseNotes.trim(),
                downloadUrl = apkDownloadUrl,
                fileName = apkFileName ?: "DailyTrack-$cleanRemoteVersion.apk",
                fileSizeInBytes = apkFileSize,
                publishedAt = publishedAt,
                isUpdateAvailable = isNewer
            )

            Result.success(updateInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file in streaming chunks, emitting download progress.
     */
    fun downloadApk(downloadUrl: String, fileName: String): Flow<UpdateDownloadProgress> = flow {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "DailyTrack-Mobile-App")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to download update (HTTP ${response.code})")
        }

        val body = response.body ?: throw Exception("Empty download response body")
        val totalBytes = body.contentLength()

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }

        // Clean any old apk files
        updatesDir.listFiles()?.forEach { it.delete() }

        val apkFile = File(updatesDir, fileName)

        body.byteStream().use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead: Long = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val progressPercent = if (totalBytes > 0) {
                        (totalBytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    emit(
                        UpdateDownloadProgress.Progress(
                            bytesDownloaded = totalBytesRead,
                            totalBytes = totalBytes,
                            progressPercent = progressPercent
                        )
                    )
                }
                output.flush()
            }
        }

        emit(UpdateDownloadProgress.Completed(apkFile))
    }.flowOn(Dispatchers.IO)

    /**
     * Checks if the app has permission to request package installations.
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Launches system settings to let the user allow installing unknown apps.
     */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Launches the Android Package Installer for the downloaded APK.
     */
    fun installApk(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    /**
     * Compares two semantic version strings (e.g. "1.0.1" vs "1.0").
     * Returns true if remote is newer than current.
     */
    fun isNewerVersion(current: String, remote: String): Boolean {
        if (current == remote) return false

        val currentParts = current.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        val remoteParts = remote.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val currPart = currentParts.getOrElse(i) { 0 }
            val remPart = remoteParts.getOrElse(i) { 0 }
            if (remPart > currPart) return true
            if (remPart < currPart) return false
        }

        return false
    }
}
