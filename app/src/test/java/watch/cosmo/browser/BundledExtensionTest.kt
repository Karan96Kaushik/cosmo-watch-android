package watch.cosmo.browser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledExtensionTest {
    @Test
    fun manifestMatchesPinnedFirefoxBuild() {
        val manifest = repoFile("app/src/main/assets/extensions/ublock/manifest.json").readText()
        val pin = repoFile("third_party/ublock-origin.pin").readLines()
            .filter { it.contains("=") }
            .associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim()
            }
        assertEquals("2", firstJsonNumber(manifest, "manifest_version").toString())
        assertEquals(pin.getValue("id"), firstJsonString(manifest, "id"))
        assertEquals(pin.getValue("version"), firstJsonString(manifest, "version"))
        assertEquals(ExtensionInstaller.EXTENSION_ID, pin.getValue("id"))
        assertEquals("resource://android/assets/extensions/ublock/", ExtensionInstaller.RESOURCE_URI)
        assertTrue(pin.getValue("sha256").matches(Regex("[0-9a-f]{64}")))
        assertTrue(manifest.contains("\"webRequestBlocking\""))
        assertTrue(File(repoFile("app/src/main/assets/extensions/ublock"), "dashboard.html").isFile)
    }

    private fun firstJsonString(json: String, key: String): String {
        val match = Regex(""""$key"\s*:\s*"([^"]+)"""").find(json)
        return match?.groupValues?.get(1) ?: error("missing $key")
    }

    private fun firstJsonNumber(json: String, key: String): Int {
        val match = Regex(""""$key"\s*:\s*(\d+)""").find(json)
        return match?.groupValues?.get(1)?.toInt() ?: error("missing $key")
    }

    private fun repoFile(relativeFromRoot: String): File {
        val fromRoot = File(relativeFromRoot)
        if (fromRoot.exists()) return fromRoot
        val fromModule = File("..", relativeFromRoot)
        if (fromModule.exists()) return fromModule
        error("Cannot find $relativeFromRoot from ${File(".").absolutePath}")
    }
}
