package com.uplay.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadUrlPolicyTest {
    @Test
    fun acceptsAbsoluteHttpAndHttpsUrls() {
        assertEquals("https://example.com/watch?v=1", DownloadUrlPolicy.normalizeHttpUrl(" https://example.com/watch?v=1 "))
        assertEquals("http://media.example/video.mp4", DownloadUrlPolicy.normalizeHttpUrl("http://media.example/video.mp4"))
    }

    @Test
    fun rejectsBlankMalformedAndRelativeUrls() {
        assertNull(DownloadUrlPolicy.normalizeHttpUrl(""))
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("   "))
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("not a url"))
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("/watch/123"))
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("https:///missing-host"))
    }

    @Test
    fun rejectsNonHttpSchemesAndEmbeddedCredentials() {
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("file:///sdcard/video.mp4"))
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("javascript:alert(1)"))
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("https://user:pass@example.com/video"))
    }

    @Test
    fun rejectsControlCharacters() {
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("https://example.com/\nvideo"))
    }
}
