package watch.cosmo.browser

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

/**
 * One session for v1. New windows load in this session instead of opening
 * another [GeckoSession].
 */
class SessionDelegates(
    private val callbacks: Callbacks,
    private val prompts: GeckoSession.PromptDelegate,
) : GeckoSession.NavigationDelegate,
    GeckoSession.ContentDelegate,
    GeckoSession.ProgressDelegate,
    GeckoSession.PromptDelegate by prompts {

    interface Callbacks {
        fun onUrl(url: String)
        fun onCanGoBack(canGoBack: Boolean)
        fun onCanGoForward(canGoForward: Boolean)
        fun onPageStart(url: String)
        fun onPageStop(success: Boolean)
        fun onProgress(progress: Int)
        fun onTitle(title: String?)
        fun onFullScreen(fullScreen: Boolean)
        fun onContentProcessGone()
    }

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny> {
        if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
            session.loadUri(request.uri)
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
    }

    override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
        session.loadUri(uri)
        return null
    }

    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean,
    ) {
        if (!url.isNullOrBlank()) callbacks.onUrl(url)
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        callbacks.onCanGoBack(canGoBack)
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        callbacks.onCanGoForward(canGoForward)
    }

    override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError,
    ): GeckoResult<String> {
        return GeckoResult.fromValue(ErrorPage.dataUri(uri, ErrorPage.describe(error)))
    }

    override fun onPageStart(session: GeckoSession, url: String) {
        callbacks.onPageStart(url)
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        callbacks.onPageStop(success)
    }

    override fun onProgressChange(session: GeckoSession, progress: Int) {
        callbacks.onProgress(progress)
    }

    override fun onTitleChange(session: GeckoSession, title: String?) {
        callbacks.onTitle(title)
    }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        callbacks.onFullScreen(fullScreen)
    }

    override fun onCrash(session: GeckoSession) {
        callbacks.onContentProcessGone()
    }

    override fun onKill(session: GeckoSession) {
        callbacks.onContentProcessGone()
    }
}
