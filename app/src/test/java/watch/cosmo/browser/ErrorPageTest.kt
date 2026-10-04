package watch.cosmo.browser

import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorPageTest {
    @Test
    fun errorPageIsADataUri() {
        val uri = ErrorPage.dataUri("https://example.com", "Network error")
        assertTrue(uri.startsWith("data:text/html;charset=utf-8,"))
        assertTrue(uri.length < 8000)
        assertTrue(uri.contains("example.com"))
    }
}
