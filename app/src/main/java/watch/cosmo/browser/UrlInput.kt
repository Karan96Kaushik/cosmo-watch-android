package watch.cosmo.browser

import java.net.URLEncoder

/**
 * Turns an address-bar string into a URI. Host-like input gets https.
 * Anything else is sent to the configured search template.
 */
object UrlInput {
    const val DEFAULT_HOME = "https://cinejoy.pk/watch/tv/220542/1/1"
    const val DEFAULT_SEARCH = "https://duckduckgo.com/?q=%s"

    fun resolve(raw: String, searchTemplate: String = DEFAULT_SEARCH): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val lower = text.lowercase()
        if (lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("moz-extension://") ||
            lower.startsWith("about:") ||
            lower.startsWith("data:")
        ) {
            return text
        }
        if (looksLikeHost(text)) {
            return "https://$text"
        }
        val template = searchTemplate.trim().ifEmpty { DEFAULT_SEARCH }
        val encoded = URLEncoder.encode(text, Charsets.UTF_8.name())
        return if (template.contains("%s")) {
            template.replace("%s", encoded)
        } else {
            template + encoded
        }
    }

    fun looksLikeHost(text: String): Boolean {
        if (text.any { it.isWhitespace() }) return false
        val lower = text.lowercase()
        if (lower.startsWith("localhost") || lower.startsWith("[")) return true
        val host = text.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.contains('.') && host.any { it.isLetterOrDigit() }
    }

    fun canSaveAsHome(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("moz-extension://")
    }
}
