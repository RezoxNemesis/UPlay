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
    @Test
    fun acceptsCaseInsensitiveSchemesAndExplicitPorts() {
        assertEquals("HTTPS://example.com:8443/watch", DownloadUrlPolicy.normalizeHttpUrl("HTTPS://example.com:8443/watch"))
        assertEquals("http://example.com:8080/video", DownloadUrlPolicy.normalizeHttpUrl("http://example.com:8080/video"))
    }

    @Test
    fun rejectsInvalidPortsAndWhitespaceInsideAuthority() {
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("https://example.com:invalid/video"))
        assertNull(DownloadUrlPolicy.normalizeHttpUrl("https://exa mple.com/video"))
    }

    @Test
    fun preservesQueryAndFragmentForValidLinks() {
        assertEquals(
            "https://example.com/watch?id=42#player",
            DownloadUrlPolicy.normalizeHttpUrl("https://example.com/watch?id=42#player")
        )
    }

    @Test
    fun resolvesSessionHostFromTheCurrentSourceUrl() {
        assertEquals("spankbang.com", DownloadUrlPolicy.sessionHostForUrl("https://spankbang.com/8kb89/video/example"))
        assertEquals("www.instagram.com", DownloadUrlPolicy.sessionHostForUrl("https://www.instagram.com/reel/abc123/"))
        assertEquals("example.com", DownloadUrlPolicy.sessionHostForUrl("HTTPS://EXAMPLE.COM:8443/watch"))
        assertNull(DownloadUrlPolicy.sessionHostForUrl("not a URL"))
    }

}
