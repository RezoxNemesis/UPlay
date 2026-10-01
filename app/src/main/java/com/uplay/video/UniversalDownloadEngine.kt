package com.uplay.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import dev.ffmpegkit_maintained.ytdlp.DownloadProgressCallback
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Multi-source downloader backed by yt-dlp's site extractors.
 * It handles supported page URLs, direct media files, and supported segmented
 * streams. It intentionally does not supply cookies, credentials, or DRM bypasses.
 */
class UniversalDownloadEngine(context: Context) {
    private val appContext = context.applicationContext
    private val workDir: File by lazy {
        File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir, "uplay-work")
            .apply { mkdirs() }
    }

    @Volatile private var initialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        synchronized(this@UniversalDownloadEngine) {
            if (initialized) return@synchronized
            YtDlp.init(appContext)
            initialized = true
        }
    }

    suspend fun download(
        rawUrl: String,
        onProgress: (Float, String) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        val parsed = Uri.parse(url)
        require((parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
            !parsed.host.isNullOrBlank()) { "Enter a valid HTTP(S) video link." }
        initialize()
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw IllegalStateException("UPlay couldn't create its temporary download folder.")
        }

        val startedAt = System.currentTimeMillis()
        val outputTemplate = File(workDir, "uplay_${startedAt}_%(title).100B_[%(id)s].%(ext)s").absolutePath
        val request = YtDlpRequest(url).setOutputTemplate(outputTemplate).apply {
            addOption("--no-playlist")
            addOption("--newline")
            // Select one complete media format; no external merger is assumed.
            addOption("-f", "best")
        }

        onProgress(0f, "Finding available video streams…")
        val response = YtDlp.execute(request, DownloadProgressCallback { progress, eta, line ->
            val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 100f) else 0f
            val status = when {
                safeProgress > 0f -> "Downloading ${safeProgress.roundToInt()}%" +
                    if (eta > 0L) " · about ${eta}s left" else ""
                line.isNotBlank() -> line.take(140)
                else -> "Resolving media source…"
            }
            onProgress(safeProgress, status)
        })
        if (!response.isSuccess) {
            throw IllegalStateException(response.errorOutput.ifBlank { "The media extractor returned exit code ${response.exitCode}." })
        }

        val prefix = "uplay_${startedAt}_"
        val completed = workDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith(".part") && it.length() > 0L }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("The extractor finished but no completed media file was found.")
        publishToDownloads(completed)
    }

    private fun publishToDownloads(file: File): Uri {
        if (Build.VERSION.SDK_INT < 29) {
            // App-specific external Downloads remains writable without broad storage access.
            return Uri.fromFile(file)
        }
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (extension in setOf("m3u8", "mpd", "ts")) "video/mp2t" else "video/mp4"
        val safeName = file.name.replace(Regex("^uplay_\\d+_"), "")
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/UPlay")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android couldn't create a file in Downloads.")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output, bufferSize = 1024 * 256) }
            } ?: throw IllegalStateException("Android couldn't open the destination file.")
            val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, ready, null, null)
            runCatching { file.delete() }
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}
