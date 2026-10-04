package watch.cosmo.browser

import java.net.URLEncoder
import org.mozilla.geckoview.WebRequestError

object ErrorPage {
    fun describe(error: WebRequestError): String {
        return when (error.code) {
            WebRequestError.ERROR_OFFLINE,
            WebRequestError.ERROR_NET_INTERRUPT,
            WebRequestError.ERROR_NET_TIMEOUT,
            WebRequestError.ERROR_CONNECTION_REFUSED,
            WebRequestError.ERROR_NET_RESET,
            WebRequestError.ERROR_UNKNOWN_HOST,
            -> "Network error. Check the Fire TV connection and try again."

            WebRequestError.ERROR_SECURITY_SSL,
            WebRequestError.ERROR_SECURITY_BAD_CERT,
            WebRequestError.ERROR_BAD_HSTS_CERT,
            -> "The site certificate could not be trusted."

            WebRequestError.ERROR_MALFORMED_URI,
            WebRequestError.ERROR_UNKNOWN_PROTOCOL,
            -> "That address is not valid."

            WebRequestError.ERROR_CONTENT_CRASHED -> "The page crashed while loading."
            WebRequestError.ERROR_HTTPS_ONLY -> "This site is only available over HTTPS."
            WebRequestError.ERROR_REDIRECT_LOOP -> "The site redirected too many times."
            WebRequestError.ERROR_FILE_NOT_FOUND -> "The page was not found."
            else -> "The page failed to load (error ${error.code})."
        }
    }

    fun dataUri(url: String?, message: String): String {
        val safeUrl = (url ?: "").replace("<", "")
        val safeMessage = message.replace("<", "")
        val html = """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Can&#39;t open page</title>
            <style>
              body { margin: 0; background: #111; color: #f4f7fb;
                     font-family: sans-serif; font-size: 32px; padding: 64px; }
              h1 { font-size: 48px; margin: 0 0 24px; }
              p { max-width: 40em; line-height: 1.35; }
              code { color: #7cdbff; word-break: break-all; }
            </style>
            </head><body>
            <h1>Can&#39;t open this page</h1>
            <p>$safeMessage</p>
            <p><code>$safeUrl</code></p>
            </body></html>
        """.trimIndent()
        return "data:text/html;charset=utf-8," + URLEncoder.encode(html, Charsets.UTF_8.name())
    }
}
