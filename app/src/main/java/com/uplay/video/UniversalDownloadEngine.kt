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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Multi-source downloader backed by yt-dlp's site extractors.
 * It handles supported page URLs and direct media files, with a resumable HTTP
 * fallback for direct media URLs. It does not supply cookies or DRM bypasses.
 */
data class DownloadPreview(val title: String, val thumbnailUrl: String?)

class UniversalDownloadEngine(context: Context) {
    /**
     * Best-effort public-page preview. Pages requiring a signed-in session simply return
     * a fallback title; this does not attempt to authenticate or bypass access checks.
     */
    suspend fun preview(rawUrl: String): DownloadPreview? = withContext(Dispatchers.IO) {
        val parsed = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return@withContext null
        if (!(parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) ||
            parsed.host.isNullOrBlank()) return@withContext null
        val fallbackTitle = parsed.lastPathSegment.orEmpty()
            .substringBefore('?').replace(Regex("[-_]+"), " ")
            .takeIf { it.isNotBlank() } ?: parsed.host.orEmpty()
        val connection = (URL(rawUrl).openConnection() as? HttpURLConnection)
            ?: return@withContext DownloadPreview(fallbackTitle, null)
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36"
            )
            if (connection.responseCode !in 200..299) return@withContext DownloadPreview(fallbackTitle, null)
            val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (contentType.isNotBlank() && contentType !in setOf("text/html", "application/xhtml+xml")) {
                return@withContext DownloadPreview(fallbackTitle, null)
            }
            val html = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (total < 1_000_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 1_000_000 - total))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
                output.toString(Charsets.UTF_8.name())
            }
            fun meta(vararg names: String): String? {
                for (tag in Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }) {
                    val name = Regex("""(?:property|name)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(tag)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
                    if (name !in names) continue
                    val value = Regex("""content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(tag)?.groupValues?.getOrNull(1) ?: continue
                    return value.replace("&amp;", "&", true).replace("&#39;", "'", true)
                        .replace("&quot;", "\"", true).replace("&#x26;", "&", true)
                }
                return null
            }
            val title = meta("og:title", "twitter:title")
                ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
                ?: fallbackTitle
            val thumbnail = meta("og:image", "og:image:url", "twitter:image")
                ?.takeIf { candidate ->
                    val imageUri = runCatching { Uri.parse(candidate) }.getOrNull()
                    imageUri != null &&
                        (imageUri.scheme.equals("https", true) || imageUri.scheme.equals("http", true)) &&
                        !imageUri.host.isNullOrBlank()
                }
            DownloadPreview(title.take(180), thumbnail)
        } catch (_: Exception) {
            DownloadPreview(fallbackTitle, null)
        } finally {
            connection.disconnect()
        }
    }

    private val appContext = context.applicationContext
    private val workDir: File by lazy {
        File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir, "uplay-work")
            .apply { mkdirs() }
    }

    private val instagramCookiesFile: File by lazy {
        File(appContext.filesDir, "instagram-cookies.txt")
    }

    fun hasInstagramSession(): Boolean =
        instagramCookiesFile.isFile && runCatching {
            instagramCookiesFile.readText().contains("\tsessionid\t")
        }.getOrDefault(false)

    /**
     * Stores only cookies the user explicitly approves from the embedded Instagram login.
     * The cookie jar stays in this app's private storage and is only passed to Instagram
     * extraction requests; it is never uploaded by UPlay.
     */
    fun saveInstagramCookies(cookieHeader: String): Boolean {
        val pairs = cookieHeader.split(';').mapNotNull { item ->
            val separator = item.indexOf('=')
            if (separator <= 0) null else {
                val name = item.substring(0, separator).trim()
                val value = item.substring(separator + 1).trim()
                if (name.isBlank() || value.contains('\n') || value.contains('\r') ||
                    name.contains('\t') || value.contains('\t')) null else name to value
            }
        }.distinctBy { it.first }
        if (pairs.none { it.first == "sessionid" && it.second.isNotBlank() }) return false
        val contents = buildString {
            append("# Netscape HTTP Cookie File\n")
            pairs.forEach { (name, value) ->
                append(".instagram.com\tTRUE\t/\tTRUE\t0\t")
                append(name).append('\t').append(value).append('\n')
            }
        }
        return runCatching {
            instagramCookiesFile.writeText(contents)
            true
        }.getOrDefault(false)
    }

    fun clearInstagramSession() {
        runCatching { instagramCookiesFile.delete() }
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
        // Prefer a single audio+video file first to avoid downloading two large tracks
        // serially on mobile data. If only separate tracks exist, keep the FFmpeg mux fallback.
        val singleFormat = maxHeight?.let {
            "best[height<=?$it]/bestvideo[height<=?$it]+bestaudio/best[height<=?$it]"
        } ?: "best/bestvideo+bestaudio"
        var extractionFailure: Exception? = null

        val combinedUri = try {
            onProgress(0f, "Selecting a combined video/audio stream…")
            val combinedFile = downloadFormat(url, startedAt, "combined", singleFormat, 0f, 0.96f, onProgress)
            onProgress(98f, "Verifying and saving ${combinedFile.name}…")
            publishToDownloads(combinedFile)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            extractionFailure = error
            null
        }
        if (combinedUri != null) return@withContext combinedUri

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
            // Some shared Reel links carry tracking/query parameters that confuse older
            // extractor builds. Retry the canonical public post path once, without query data.
            val canonicalUrl = canonicalInstagramPostUrl(url)
            if (canonicalUrl != null && canonicalUrl != url) {
                workDir.listFiles()?.filter { it.isFile && it.name.startsWith(prefix) }?.forEach { runCatching { it.delete() } }
                try {
                    val canonicalFile = downloadFormat(
                        canonicalUrl, startedAt, "instagram_canonical", singleFormat,
                        0f, 0.95f, onProgress, userAgent = mobileAgents.first()
                    )
                    return@withContext publishToDownloads(canonicalFile)
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

        // Some public Instagram pages expose a playable CDN URL in Open Graph metadata
        // even when the extractor cannot resolve the post. Never attempt to log in or
        // bypass private media; this only follows media explicitly exposed by the page.
        if (isInstagramSource) {
            onProgress(0f, "Checking publicly exposed Reel media…")
            val publicMediaUrl = runCatching { resolveInstagramPublicMediaUrl(url) }.getOrNull()
            if (publicMediaUrl != null) {
                try {
                    val publicMediaFile = downloadDirectMedia(publicMediaUrl, startedAt, onProgress)
                    if (publicMediaFile != null) return@withContext publishToDownloads(publicMediaFile)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    extractionFailure = error
                }
            }
        }

        onProgress(0f, "Trying direct-file recovery…")
        var directFailure: Exception? = null
        val directFile = try {
            downloadDirectMedia(url, startedAt, onProgress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (directError: Exception) {
            directFailure = directError
            null
        }
        if (directFile != null) return@withContext publishToDownloads(directFile)
        val extractorDetail = extractionFailure?.message.orEmpty()
            .replace(Regex("\\u001B\\[[;\\d]*m"), "")
            .lineSequence().map(String::trim).filter(String::isNotBlank).lastOrNull().orEmpty()
        val detail = when {
            extractorDetail.contains("HTTP Error 403", true) || extractorDetail.contains("Forbidden", true) -> "The source refused the download request (HTTP 403). The media may require access UPlay does not have."
            extractorDetail.contains("HTTP Error 429", true) || extractorDetail.contains("Too Many Requests", true) -> "The source is rate-limiting downloads. Wait a while and retry."
            extractorDetail.contains("Sign in", true) || extractorDetail.contains("login", true) || extractorDetail.contains("checkpoint", true) -> "This source requires a signed-in session or verification that UPlay does not currently have."
            extractorDetail.contains("Unsupported URL", true) -> "This link format is not supported by the current extractor. Try the canonical post/Reel share link or update UPlay's downloader."
            isInstagramSource && extractorDetail.isNotBlank() -> "Instagram extraction failed: ${extractorDetail.take(180)}. Publicly visible posts may still be unavailable to an independent downloader."
            isInstagramSource -> "Instagram did not expose a downloadable public media stream. Try the canonical Reel/post share link; private, login-gated, expired, or restricted media may not be available to UPlay."
            extractorDetail.isNotBlank() -> extractorDetail.take(220)
            directFailure?.message?.isNotBlank() == true -> "Direct-media recovery failed: ${directFailure?.message?.take(180)}"
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
            addOption("--concurrent-fragments", "8")
            addOption("--buffer-size", "16K")
            addOption("--http-chunk-size", "10M")
            addOption("--file-access-retries", "3")
            addOption("--socket-timeout", "30")
            addOption("--force-ipv4")
            addOption("--format-sort", "res,ext:mp4:m4a")
            addOption("--retry-sleep", "http:1:3")
            if (url.contains("instagram.com", ignoreCase = true)) {
                addOption("--add-headers", "Referer:https://www.instagram.com/")
                if (hasInstagramSession()) addOption("--cookies", instagramCookiesFile.absolutePath)
            }
            if (userAgent != null) addOption("--user-agent", userAgent)
            addOption("-f", format)
        }
        val progressSample = longArrayOf(0L, System.currentTimeMillis())
        val response = YtDlp.execute(request, DownloadProgressCallback { progress, eta, _ ->
            val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 100f) else 0f
            val combinedProgress = (progressStart + safeProgress * progressScale).coerceIn(0f, 99f)
            val currentBytes = workDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith(prefix) }
                ?.maxOfOrNull { it.length() } ?: 0L
            val now = System.currentTimeMillis()
            var speedBytesPerSecond = 0L
            if (now - progressSample[1] >= 500L) {
                speedBytesPerSecond = ((currentBytes - progressSample[0]).coerceAtLeast(0L) * 1000L) /
                    (now - progressSample[1]).coerceAtLeast(1L)
                progressSample[0] = currentBytes
                progressSample[1] = now
            }
            val estimatedTotal = if (safeProgress > 0f && currentBytes > 0L) {
                (currentBytes * 100f / safeProgress).toLong()
            } else 0L
            val sizeStatus = if (currentBytes > 0L) {
                " · ${formatBytes(currentBytes)}" +
                    if (estimatedTotal > currentBytes) " / ~${formatBytes(estimatedTotal)}" else ""
            } else ""
            val speedStatus = if (speedBytesPerSecond > 0L) " · ${formatSpeed(speedBytesPerSecond)}" else ""
            val status = when {
                safeProgress > 0f -> "Downloading $label stream: ${safeProgress.roundToInt()}%$sizeStatus$speedStatus" +
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
    private fun canonicalInstagramPostUrl(pageUrl: String): String? {
        val parsed = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return null
        val host = parsed.host.orEmpty().lowercase()
        if (host != "instagram.com" && !host.endsWith(".instagram.com")) return null
        val parts = parsed.pathSegments
        if (parts.size < 2) return null
        val kind = parts[0].lowercase()
        if (kind !in setOf("reel", "reels", "p", "tv")) return null
        val shortcode = parts[1].takeIf { it.matches(Regex("[A-Za-z0-9_-]{5,}")) } ?: return null
        val canonicalKind = if (kind == "reels") "reel" else kind
        return "https://www.instagram.com/$canonicalKind/$shortcode/"
    }

    private fun resolveInstagramPublicMediaUrl(pageUrl: String): String? {
        val connection = (URL(pageUrl).openConnection() as? HttpURLConnection) ?: return null
        val html = try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8")
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
            )
            if (connection.responseCode !in 200..299) return null
            val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (type.isNotBlank() && type !in setOf("text/html", "application/xhtml+xml")) return null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (total < 1_500_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 1_500_000 - total))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }

        val videoMetaTags = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
        for (tagMatch in videoMetaTags.findAll(html)) {
            val tag = tagMatch.value
            val property = Regex("""(?:property|name)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
            if (property !in setOf("og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream")) continue
            val content = Regex("""content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1) ?: continue
            val candidate = content
                .replace("&amp;", "&", ignoreCase = true)
                .replace("&#x26;", "&", ignoreCase = true)
                .replace("\\/", "/")
                .replace("\\u0026", "&", ignoreCase = true)
            val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: continue
            if ((parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
                !parsed.host.isNullOrBlank()) return candidate
        }
        // Public pages sometimes embed a direct CDN URL in structured metadata rather
        // than an Open Graph tag. Only accept explicitly named media fields and HTTP(S) URLs.
        val embeddedMedia = Regex(
            """["'](?:video_url|contentUrl|playable_url)["']\s*:\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        for (match in embeddedMedia.findAll(html)) {
            val candidate = match.groupValues[1]
                .replace("\\/", "/")
                .replace("\\u0026", "&", ignoreCase = true)
                .replace("\\u003d", "=", ignoreCase = true)
                .replace("&amp;", "&", ignoreCase = true)
            val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: continue
            val host = parsed.host.orEmpty().lowercase()
            if ((parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
                (host.endsWith("cdninstagram.com") || host.endsWith("fbcdn.net"))) return candidate
        }
        return null
    }

    private suspend fun downloadDirectMedia(
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
            val probeHost = uri.host.orEmpty().lowercase()
            if (probeHost.endsWith("cdninstagram.com") || probeHost.endsWith("fbcdn.net")) {
                probe.setRequestProperty("Referer", "https://www.instagram.com/")
            }
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
        val resumeKey = MessageDigest.getInstance("SHA-256")
            .digest(rawUrl.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        // Stable partial filename lets a later retry resume this exact direct URL.
        val part = File(workDir, "uplay_direct_${resumeKey}.$outputExtension.part")
        val finalFile = File(workDir, "uplay_${startedAt}_direct.$outputExtension")
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < 3) {
            currentCoroutineContext().ensureActive()
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
                val mediaHost = uri.host.orEmpty().lowercase()
                if (mediaHost.endsWith("cdninstagram.com") || mediaHost.endsWith("fbcdn.net")) {
                    connection.setRequestProperty("Referer", "https://www.instagram.com/")
                }
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
                if (code == HttpURLConnection.HTTP_PARTIAL &&
                    contentRangeStart != (if (existing > 0L) existing else 0L)) {
                    part.delete()
                    lastError = IllegalStateException("The server returned an incompatible resume range; restarting.")
                    if (attempt < 3) continue
                    throw lastError!!
                }
                val append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL &&
                    contentRangeStart == existing
                val offset = if (append) existing else 0L
                val responseLength = connection.contentLengthLong.takeIf { it >= 0L }
                val totalFromRange = connection.getHeaderField("Content-Range")
                    ?.substringAfterLast("/", "")?.toLongOrNull()
                val expectedLength = totalFromRange ?: responseLength?.let { it + offset }
                var lastSpeedBytes = offset
                var lastSpeedAt = System.currentTimeMillis()
                if (!append && part.exists()) part.delete()
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var total = offset
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            total += count
                            val now = System.currentTimeMillis()
                            if (expectedLength != null && expectedLength > 0L) {
                                val percent = (total * 100f / expectedLength).coerceIn(0f, 100f)
                                val elapsed = now - lastSpeedAt
                                val speed = if (elapsed >= 500L) {
                                    ((total - lastSpeedBytes).coerceAtLeast(0L) * 1000L) / elapsed.coerceAtLeast(1L)
                                } else 0L
                                if (elapsed >= 500L) {
                                    lastSpeedBytes = total
                                    lastSpeedAt = now
                                }
                                val speedText = if (speed > 0L) " · ${formatSpeed(speed)}" else ""
                                onProgress(
                                    percent,
                                    "Downloading ${percent.roundToInt()}% · ${formatBytes(total)} / ${formatBytes(expectedLength)}$speedText"
                                )
                            } else {
                                val elapsed = now - lastSpeedAt
                                val speed = if (elapsed >= 500L) {
                                    ((total - lastSpeedBytes).coerceAtLeast(0L) * 1000L) / elapsed.coerceAtLeast(1L)
                                } else 0L
                                if (elapsed >= 500L) {
                                    lastSpeedBytes = total
                                    lastSpeedAt = now
                                }
                                val speedText = if (speed > 0L) " · ${formatSpeed(speed)}" else ""
                                onProgress(0f, "Downloading ${formatBytes(total)}$speedText · direct media")
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (part.length() == 0L) {
                    part.delete()
                    throw IllegalStateException("The source returned an empty media file.")
                }
                if (expectedLength != null && part.length() != expectedLength) {
                    lastError = IllegalStateException(
                        "Direct download size mismatch: expected $expectedLength bytes, received ${part.length()}."
                    )
                    if (attempt < 3) continue
                    throw lastError!!
                }
                // Some servers label an HTML error/login page as generic binary data.
                val signature = ByteArray(512)
                val signatureLength = part.inputStream().use { it.read(signature) }.coerceAtLeast(0)
                val prefixText = String(signature, 0, signatureLength, Charsets.UTF_8)
                    .trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
                if (prefixText.startsWith("<!doctype html") || prefixText.startsWith("<html") ||
                    prefixText.startsWith("{\"error\"") || prefixText.startsWith("{\"message\"")) {
                    part.delete()
                    throw IllegalStateException("The direct URL returned an HTML/JSON page, not a media file.")
                }
                if (finalFile.exists()) finalFile.delete()
                if (!part.renameTo(finalFile)) throw IllegalStateException("Could not finalize the downloaded file.")
                return finalFile
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
                if (attempt >= 3) throw error
            } finally {
                connection.disconnect()
            }
        }
        throw lastError ?: IllegalStateException("Direct download failed.")
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun formatSpeed(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1024L * 1024L -> "%.2f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024L -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
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
        // Android restricts the Video collection's RELATIVE_PATH to media folders
        // (for example Movies/), so Download/UPlay must use the Downloads collection.
        // The returned content URI remains directly playable by Media3.
        val collection = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Downloads.EXTERNAL_CONTENT_URI
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
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, ready, null, null)
            runCatching { file.delete() }
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}
