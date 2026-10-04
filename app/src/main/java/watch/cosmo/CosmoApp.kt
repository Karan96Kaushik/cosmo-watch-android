package watch.cosmo

import android.app.Application
import android.util.Log
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import watch.cosmo.browser.ExtensionInstaller

/**
 * One [GeckoRuntime] for the process. A second [GeckoRuntime.create] in this
 * process throws, so the session and the extension install live here too and
 * survive activity recreation.
 */
class CosmoApp : Application() {
    lateinit var runtime: GeckoRuntime
        private set

    var session: GeckoSession? = null
    var webExtension: WebExtension? = null
        private set
    var extensionFailure: Throwable? = null
        private set
    var initialLoadStarted: Boolean = false
    var lastUrl: String? = null
    var canGoBack: Boolean = false
    var canGoForward: Boolean = false

    private val extensionSettled = GeckoResult<Boolean>()

    override fun onCreate() {
        super.onCreate()
        val settings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(BuildConfig.DEBUG)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .consoleOutput(BuildConfig.DEBUG)
            .build()
        runtime = GeckoRuntime.create(this, settings)
        Log.i(TAG, "GeckoRuntime ${BuildConfig.GECKOVIEW_VERSION}")
        ExtensionInstaller.install(this, runtime).accept(
            { extension ->
                webExtension = extension
                Log.i(UBO_TAG, "Installed ${extension?.id} v${extension?.metaData?.version}")
                extensionSettled.complete(true)
            },
            { error ->
                extensionFailure = error
                Log.e(UBO_TAG, "Install failed", error)
                extensionSettled.complete(false)
            },
        )
    }

    fun whenExtensionSettled(callback: (Boolean) -> Unit) {
        extensionSettled.accept(
            { ok -> callback(ok == true) },
            { callback(false) },
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i(TAG, "onTrimMemory level=$level")
    }

    companion object {
        private const val TAG = "CosmoWatch"
        const val UBO_TAG = "uBO"
    }
}
