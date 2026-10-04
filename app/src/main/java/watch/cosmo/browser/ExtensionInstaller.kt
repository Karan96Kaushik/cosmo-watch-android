package watch.cosmo.browser

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Installs the unpacked Firefox build of uBlock Origin that ships in assets.
 *
 * [ensureBuiltIn] skips reinstall when the same version is already in the
 * Gecko profile. The GeckoView 157 javadoc says a different bundled version
 * is reinstalled; older reports said it was not. After [ensureBuiltIn] this
 * compares [WebExtension.MetaData.version] with the bundled manifest and,
 * when they differ, calls [WebExtensionController.installBuiltIn]. That path
 * updates the same extension id in the profile and is the one expected to
 * keep extension storage. If it still does not match, the fallback is
 * uninstall then [installBuiltIn], which can wipe uBO settings, custom
 * filters, and list toggles. That survival behavior is not verified on a
 * device in this tree.
 *
 * Private browsing is not used in v1. When it is added, allow the extension
 * with [WebExtensionController.setAllowedInPrivateBrowsing], which takes the
 * extension and a boolean and returns `GeckoResult<WebExtension>`.
 */
object ExtensionInstaller {
    const val EXTENSION_ID = "uBlock0@raymondhill.net"
    const val ASSET_DIRECTORY = "extensions/ublock/"
    const val RESOURCE_URI = "resource://android/assets/extensions/ublock/"
    const val DASHBOARD_PAGE = "dashboard.html"
    private const val MANIFEST = "extensions/ublock/manifest.json"
    private const val TAG = "uBO"

    data class BundledManifest(val id: String, val version: String)

    fun readBundledManifest(context: Context): BundledManifest {
        val text = context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
        val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
            ?: error("Bundled uBO manifest has no extension id")
        val version = Regex(""""version"\s*:\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
            ?: error("Bundled uBO manifest has no version")
        return BundledManifest(id, version)
    }

    fun install(context: Context, runtime: GeckoRuntime): GeckoResult<WebExtension> {
        val bundled = readBundledManifest(context)
        if (bundled.id != EXTENSION_ID) {
            return GeckoResult.fromException(
                IllegalStateException(
                    "Bundled manifest id ${bundled.id} does not match $EXTENSION_ID",
                ),
            )
        }
        val controller = runtime.webExtensionController
        Log.i(TAG, "ensureBuiltIn $RESOURCE_URI id=${bundled.id} bundled=${bundled.version}")
        return controller.ensureBuiltIn(RESOURCE_URI, bundled.id).then { installed ->
            if (installed == null) {
                GeckoResult.fromException(IllegalStateException("ensureBuiltIn returned null"))
            } else {
                val installedVersion = installed.metaData.version
                Log.i(TAG, "Installed ${installed.id} v$installedVersion")
                if (installedVersion == bundled.version) {
                    GeckoResult.fromValue(installed)
                } else {
                    upgrade(controller, installed, bundled.version)
                }
            }
        }
    }

    fun dashboardUrl(extension: WebExtension): String? {
        val base = extension.metaData.baseUrl
        if (base.isBlank()) return null
        return if (base.endsWith("/")) base + DASHBOARD_PAGE else "$base/$DASHBOARD_PAGE"
    }

    private fun upgrade(
        controller: WebExtensionController,
        installed: WebExtension,
        bundledVersion: String,
    ): GeckoResult<WebExtension> {
        Log.i(
            TAG,
            "Bundled uBO $bundledVersion differs from installed ${installed.metaData.version}; installBuiltIn",
        )
        return controller.installBuiltIn(RESOURCE_URI).then({ upgraded ->
            if (upgraded == null) {
                GeckoResult.fromException(IllegalStateException("installBuiltIn returned null"))
            } else if (upgraded.metaData.version == bundledVersion) {
                Log.i(TAG, "installBuiltIn updated uBO to ${upgraded.metaData.version}")
                GeckoResult.fromValue(upgraded)
            } else {
                Log.w(
                    TAG,
                    "installBuiltIn left v${upgraded.metaData.version}; uninstalling and reinstalling. " +
                        "uBO settings may not survive this fallback.",
                )
                reinstall(controller, upgraded)
            }
        }, { error ->
            Log.e(TAG, "installBuiltIn failed; uninstalling and reinstalling", error)
            reinstall(controller, installed)
        })
    }

    private fun reinstall(
        controller: WebExtensionController,
        current: WebExtension,
    ): GeckoResult<WebExtension> {
        return controller.uninstall(current).then {
            controller.installBuiltIn(RESOURCE_URI)
        }
    }
}
