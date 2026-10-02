package com.uplay.video

import java.net.URI

/**
 * Shared validation for user-provided media links.
 *
 * Only absolute HTTP(S) URLs with a host are accepted. Rejecting credentials and
 * control characters early keeps malformed links out of the downloader pipeline.
 */
internal object DownloadUrlPolicy {
    fun normalizeHttpUrl(rawUrl: String): String? {
        val value = rawUrl.trim()
        if (value.isEmpty() || value.any { it.isISOControl() }) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        return runCatching { uri.toASCIIString() }.getOrNull()
    }
}
