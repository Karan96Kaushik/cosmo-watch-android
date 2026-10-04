package watch.cosmo.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInputTest {
    @Test
    fun keepsExplicitSchemes() {
        assertEquals("https://example.com/a", UrlInput.resolve("https://example.com/a"))
        assertEquals("http://example.com", UrlInput.resolve("  http://example.com  "))
        assertEquals("about:config", UrlInput.resolve("about:config"))
        assertEquals(
            "moz-extension://abc/dashboard.html",
            UrlInput.resolve("moz-extension://abc/dashboard.html"),
        )
    }

    @Test
    fun addsHttpsToHosts() {
        assertEquals("https://example.com/path", UrlInput.resolve("example.com/path"))
        assertEquals("https://localhost:8080", UrlInput.resolve("localhost:8080"))
        assertEquals("https://192.168.1.20", UrlInput.resolve("192.168.1.20"))
    }

    @Test
    fun searchesEverythingElse() {
        val resolved = UrlInput.resolve("fire tv browser", "https://duckduckgo.com/?q=%s")
        assertEquals("https://duckduckgo.com/?q=fire+tv+browser", resolved)
    }

    @Test
    fun appendsWhenTemplateHasNoPlaceholder() {
        assertEquals("https://search.example/?q=cats", UrlInput.resolve("cats", "https://search.example/?q="))
    }

    @Test
    fun blankInputStaysBlank() {
        assertEquals("", UrlInput.resolve("   "))
    }

    @Test
    fun defaultHomeIsTheCinejoyEpisode() {
        assertEquals("https://cinejoy.pk/watch/tv/220542/1/1", UrlInput.DEFAULT_HOME)
    }

    @Test
    fun homePageFilter() {
        assertTrue(UrlInput.canSaveAsHome("https://example.com"))
        assertFalse(UrlInput.canSaveAsHome("data:text/html,hi"))
        assertFalse(UrlInput.canSaveAsHome(null))
    }
}
