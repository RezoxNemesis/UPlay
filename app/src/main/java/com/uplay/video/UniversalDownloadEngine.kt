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
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Multi-source downloader backed by yt-dlp's site extractors.
 * It handles supported page URLs and direct media files, with a resumable HTTP
 * fallback for direct media URLs. It does not supply cookies or DRM bypasses.
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
        quality: String = "best",
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
        val prefix = "uplay_${startedAt}_"
        val host = parsed.host.orEmpty().lowercase()
        val isInstagramSource = host == "instagram.com" || host.endsWith(".instagram.com")
        val maxHeight = when (quality.lowercase().replace("p", "").replace(" ", "")) {
            "1080" -> 1080
            "720" -> 720
            "480" -> 480
            "360" -> 360
            else -> null
        }
        val videoFormat = maxHeight?.let { "bestvideo[height<=?$it]" } ?: "bestvideo"
        val singleFormat = maxHeight?.let { "best[height<=?$it]" } ?: "best"
        var extractionFailure: Exception? = null

        val mergedUri = try {
            onProgress(0f, "Finding separate video and audio streams…")
            val videoFile = downloadFormat(url, startedAt, "video", videoFormat, 0f, 0.48f, onProgress)
            val audioFile = downloadFormat(url, startedAt, "audio", "bestaudio", 0.48f, 0.48f, onProgress)
            val mp4CompatibleVideo = videoFile.extension.lowercase() in setOf("mp4", "m4v", "mov")
            val mp4CompatibleAudio = audioFile.extension.lowercase() in setOf("m4a", "mp4", "aac")
            val container = if (mp4CompatibleVideo && mp4CompatibleAudio) "mp4" else "mkv"
            val merged = File(workDir, "uplay_${startedAt}_merged.$container")
            val arguments = buildList {
                add("-y")
                add("-i"); add(videoFile.absolutePath)
                add("-i"); add(audioFile.absolutePath)
                add("-map"); add("0:v:0")
                add("-map"); add("1:a:0")
                add("-c"); add("copy")
                if (container == "mp4") { add("-movflags"); add("+faststart") }
                add(merged.absolutePath)
            }.toTypedArray()
            val session = FFmpegKit.executeWithArguments(arguments)
            if (!ReturnCode.isSuccess(session.returnCode) || !merged.exists() || merged.length() == 0L) {
                throw IllegalStateException("The selected video and audio streams couldn't be combined.")
            }
            runCatching { videoFile.delete() }
            runCatching { audioFile.delete() }
            onProgress(98f, "Finalizing downloaded video…")
            publishToDownloads(merged)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            extractionFailure = error
            null
        }
        if (mergedUri != null) return@withContext mergedUri

        // Public Instagram pages can expose different formats to mobile browsers.
        // Retry two common mobile clients without bypassing private/login-only access.
        if (isInstagramSource) {
            val mobileAgents = listOf(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
            )
            for ((index, mobileAgent) in mobileAgents.withIndex()) {
                workDir.listFiles()?.filter { it.isFile && it.name.startsWith(prefix) }?.forEach { runCatching { it.delete() } }
                try {
                    val mobileFile = downloadFormat(
                        url, startedAt, "instagram_mobile_$index", singleFormat,
                        0f, 0.95f, onProgress, userAgent = mobileAgent
                    )
                    return@withContext publishToDownloads(mobileFile)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    extractionFailure = error
                }
            }
        }

        // A single-file format is a compatibility fallback for sources without separable tracks.
        workDir.listFiles()?.filter { it.isFile && it.name.startsWith(prefix) }?.forEach { runCatching { it.delete() } }
        val singleUri = try {
            val singleFile = downloadFormat(url, startedAt, "single", singleFormat, 0f, 0.95f, onProgress)
            publishToDownloads(singleFile)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            extractionFailure = error
            null
        }
        if (singleUri != null) return@withContext singleUri

        onProgress(0f, "Trying direct-file recovery…")
        val directFile = try {
            downloadDirectMedia(url, startedAt, onProgress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (directError: Exception) {
            null
        }
        if (directFile != null) return@withContext publishToDownloads(directFile)
        val extractorDetail = extractionFailure?.message.orEmpty()
            .replace(Regex("\\u001B\\[[;\\d]*m"), "")
            .lineSequence().map(String::trim).filter(String::isNotBlank).lastOrNull().orEmpty()
        val detail = when {
            isInstagramSource -> "Instagram did not expose a downloadable public media stream. Try a public Reel URL in a browser; private, login-gated, expired, or restricted media may not be available to UPlay."
            extractorDetail.contains("Unsupported URL", true) -> "This link format is not supported by the current extractor. Try the direct media link or update UPlay's downloader."
            extractorDetail.contains("HTTP Error 403", true) || extractorDetail.contains("Forbidden", true) -> "The source refused the download request (HTTP 403). The media may require access UPlay does not have."
            extractorDetail.contains("HTTP Error 429", true) || extractorDetail.contains("Too Many Requests", true) -> "The source is rate-limiting downloads. Wait a while and retry."
            extractorDetail.contains("Sign in", true) || extractorDetail.contains("login", true) -> "This source requires a signed-in session that UPlay does not currently have."
            extractorDetail.isNotBlank() -> extractorDetail.take(220)
            else -> "No complete downloadable media stream was found. The page may not expose a public media file."
        }
        throw IllegalStateException(detail)
    }

    private fun downloadFormat(
        url: String,
        startedAt: Long,
        label: String,
        format: String,
        progressStart: Float,
        progressScale: Float,
        onProgress: (Float, String) -> Unit,
        userAgent: String? = null
    ): File {
        val prefix = "uplay_${startedAt}_${label}_"
        val template = File(workDir, "${prefix}%(title).100B_[%(id)s].%(ext)s").absolutePath
        val request = YtDlpRequest(url).setOutputTemplate(template).apply {
            addOption("--no-playlist")
            addOption("--newline")
            addOption("--restrict-filenames")
            addOption("--extractor-retries", "5")
            addOption("--retries", "5")
            addOption("--fragment-retries", "5")
            addOption("--file-access-retries", "3")
            addOption("--socket-timeout", "30")
            addOption("--force-ipv4")
            addOption("--retry-sleep", "http:1:3")
            if (url.contains("instagram.com", ignoreCase = true)) {
                addOption("--add-headers", "Referer:https://www.instagram.com/")
            }
            if (userAgent != null) addOption("--user-agent", userAgent)
            addOption("-f", format)
        }
        val response = YtDlp.execute(request, DownloadProgressCallback { progress, eta, _ ->
            val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 100f) else 0f
            val combinedProgress = (progressStart + safeProgress * progressScale).coerceIn(0f, 99f)
            val status = when {
                safeProgress > 0f -> "Downloading $label stream: ${safeProgress.roundToInt()}%" +
                    if (eta > 0L) " · about ${eta}s left" else ""
                else -> "Resolving $label stream…"
            }
            onProgress(combinedProgress, status)
        })
        if (!response.isSuccess) {
            throw IllegalStateException(response.errorOutput.ifBlank { "No compatible $format stream was found." })
        }
        return workDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith(".part") && it.length() > 0L }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("The $label stream did not produce a complete file.")
    }
    private fun downloadDirectMedia(
        rawUrl: String,
        startedAt: Long,
        onProgress: (Float, String) -> Unit
    ): File? {
        val uri = Uri.parse(rawUrl)
        val extension = uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
        val knownMediaExtensions = setOf(
            "mp4", "m4v", "mov", "webm", "mkv", "avi", "3gp", "mpeg", "mpg",
            "ts", "m4a", "mp3", "aac", "ogg", "opus", "wav", "flac"
        )
        if (extension in setOf("m3u8", "mpd", "m3u")) return null

        val probe = (URL(rawUrl).openConnection() as? HttpURLConnection) ?: return null
        val mime: String
        try {
            probe.instanceFollowRedirects = true
            probe.connectTimeout = 15_000
            probe.readTimeout = 30_000
            probe.requestMethod = "GET"
            probe.setRequestProperty("Accept", "*/*")
            probe.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36"
            )
            if (probe.responseCode !in 200..299) return null
            mime = probe.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (mime.contains("mpegurl") || mime.contains("dash+xml") ||
                mime == "text/html" || mime.contains("json") || mime.startsWith("image/")) return null
            val isMedia = mime.startsWith("video/") || mime.startsWith("audio/") ||
                (extension in knownMediaExtensions && (mime.isBlank() || mime == "application/octet-stream" ||
                    mime == "binary/octet-stream"))
            if (!isMedia) return null
        } finally {
            probe.disconnect()
        }

        val outputExtension = extension.takeIf { it in knownMediaExtensions }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime).orEmpty().ifBlank { "mp4" }
        val part = File(workDir, "uplay_${startedAt}_direct.$outputExtension.part")
        val finalFile = File(workDir, "uplay_${startedAt}_direct.$outputExtension")
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < 3) {
            attempt++
            val existing = part.takeIf { it.exists() }?.length() ?: 0L
            val connection = (URL(rawUrl).openConnection() as? HttpURLConnection) ?: return null
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36"
                )
                if (existing > 0L) connection.setRequestProperty("Range", "bytes=$existing-")
                val code = connection.responseCode
                if (code == 416 && existing > 0L) {
                    val total = connection.getHeaderField("Content-Range")
                        ?.substringAfterLast("/", "")?.toLongOrNull()
                    if (total != null && total == existing && part.length() == total) {
                        if (finalFile.exists()) finalFile.delete()
                        if (!part.renameTo(finalFile)) throw IllegalStateException("Could not finalize the resumed download.")
                        return finalFile
                    }
                    part.delete()
                    lastError = IllegalStateException("The server rejected the resume range; restarting from the beginning.")
                    if (attempt < 3) continue
                    throw lastError!!
                }
                if (code !in 200..299) return null
                val contentRangeStart = connection.getHeaderField("Content-Range")
                    ?.substringAfter("bytes ", "")?.substringBefore("-")?.toLongOrNull()
                val append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL &&
                    (contentRangeStart == null || contentRangeStart == existing)
                val offset = if (append) existing else 0L
                val responseLength = connection.contentLengthLong.takeIf { it >= 0L }
                val totalFromRange = connection.getHeaderField("Content-Range")
                    ?.substringAfterLast("/", "")?.toLongOrNull()
                val expectedLength = totalFromRange ?: responseLength?.let { it + offset }
                if (!append && part.exists()) part.delete()
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var total = offset
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            total += count
                            if (expectedLength != null && expectedLength > 0L) {
                                val percent = (total * 100f / expectedLength).coerceIn(0f, 100f)
                                onProgress(percent, "Downloading ${percent.roundToInt()}% · direct media")
                            } else {
                                onProgress(0f, "Downloading direct media…")
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (expectedLength != null && part.length() < expectedLength) {
                    lastError = IllegalStateException("Direct download ended before all bytes arrived.")
                    if (attempt < 3) continue
                    throw lastError!!
                }
                if (finalFile.exists()) finalFile.delete()
                if (!part.renameTo(finalFile)) throw IllegalStateException("Could not finalize the downloaded file.")
                return finalFile
            } catch (error: Exception) {
                lastError = error
                if (attempt >= 3) throw error
            } finally {
                connection.disconnect()
            }
        }
        throw lastError ?: IllegalStateException("Direct download failed.")
    }

    private fun publishToDownloads(file: File): Uri {
        if (Build.VERSION.SDK_INT < 29) {
            // App-specific external Downloads remains writable without broad storage access.
            return Uri.fromFile(file)
        }
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (extension in setOf("m3u8", "mpd", "ts")) "video/mp2t" else "video/mp4"
        val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
        val isAudio = extension in audioExtensions || mime.startsWith("audio/")
        val safeName = file.name.replace(Regex("^uplay_\\d+_"), "")
        val collection = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS}/UPlay"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = appContext.contentResolver
        val uri = resolver.insert(collection, values)
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
