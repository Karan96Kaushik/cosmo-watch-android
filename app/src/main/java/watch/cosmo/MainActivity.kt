package watch.cosmo

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.StorageController
import watch.cosmo.browser.ExtensionInstaller
import watch.cosmo.browser.SessionDelegates
import watch.cosmo.browser.UrlInput
import watch.cosmo.settings.Prefs
import watch.cosmo.tv.ControlMenu
import watch.cosmo.tv.CursorOverlay
import watch.cosmo.tv.CursorSpeed
import watch.cosmo.tv.PromptUi
import watch.cosmo.tv.UrlBar
import watch.cosmo.tv.VideoControls

class MainActivity : AppCompatActivity(),
    SessionDelegates.Callbacks,
    CursorOverlay.Listener,
    PromptUi.FilePicker {

    private lateinit var app: CosmoApp
    private lateinit var prefs: Prefs
    private lateinit var geckoView: org.mozilla.geckoview.GeckoView
    private lateinit var cursor: CursorOverlay
    private lateinit var urlBar: UrlBar
    private lateinit var menu: ControlMenu
    private lateinit var progress: ProgressBar
    private lateinit var warning: TextView
    private lateinit var status: TextView
    private lateinit var promptUi: PromptUi
    private lateinit var videoControls: VideoControls

    private var fullScreen = false
    private var extensionFailed = false
    private val heldKeys = HashSet<Int>()
    private var holdFrames = 0
    private var centerLongFired = false
    private var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null
    private var pendingFileResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null

    private val cursorHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (heldKeys.isEmpty() || menu.isOpen || urlBar.isOpen || fullScreen) {
                holdFrames = 0
                return
            }
            val step = CursorSpeed.pixelsPerFrame(prefs.cursorSpeed, holdFrames)
            var dx = 0f
            var dy = 0f
            if (KeyEvent.KEYCODE_DPAD_LEFT in heldKeys) dx -= step
            if (KeyEvent.KEYCODE_DPAD_RIGHT in heldKeys) dx += step
            if (KeyEvent.KEYCODE_DPAD_UP in heldKeys) dy -= step
            if (KeyEvent.KEYCODE_DPAD_DOWN in heldKeys) dy += step
            if (dx != 0f || dy != 0f) {
                cursor.moveBy(dx, dy)
                holdFrames++
            }
            cursorHandler.postDelayed(this, FRAME_MS)
        }
    }
    private val longPress = Runnable {
        centerLongFired = true
        openMenu()
    }
    private val hideStatus = Runnable { status.visibility = View.GONE }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        deliverFile(uri?.let { arrayOf(it) })
    }
    private val openDocuments = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        deliverFile(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
    }
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        deliverFile(uri?.let { arrayOf(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as CosmoApp
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)
        geckoView = findViewById(R.id.gecko_view)
        cursor = findViewById(R.id.cursor)
        urlBar = findViewById(R.id.url_bar)
        menu = findViewById(R.id.menu)
        progress = findViewById(R.id.progress)
        warning = findViewById(R.id.warning)
        status = findViewById(R.id.status)
        promptUi = PromptUi(this, this)
        videoControls = VideoControls(this, onStatus = ::showStatus, onOpenMenu = ::openMenu)
        cursor.listener = this
        urlBar.onSubmit = { raw -> navigate(raw) }
        menu.onItem = { id -> onMenu(id) }
        geckoView.isFocusable = true
        geckoView.isFocusableInTouchMode = true
        attachSession()
        if (app.extensionFailure != null) showWarning()
        app.whenExtensionSettled { installed ->
            if (isDestroyed) return@whenExtensionSettled
            runOnUiThread {
                if (!installed) showWarning()
                startInitialLoad()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        attachSession()
        app.session?.setActive(true)
        geckoView.requestFocus()
    }

    override fun onPause() {
        heldKeys.clear()
        cursorHandler.removeCallbacks(ticker)
        cursorHandler.removeCallbacks(longPress)
        videoControls.cancelHold()
        app.session?.setActive(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (geckoView.session != null) {
            geckoView.releaseSession()
        }
        videoControls.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handleBack()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            if (menu.isOpen) closeMenu() else openMenu()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            openAddress()
            return true
        }
        if (urlBar.isOpen || menu.isOpen) {
            return super.dispatchKeyEvent(event)
        }
        if (videoControls.handleKey(event)) return true
        if (fullScreen) {
            return geckoView.dispatchKeyEvent(event)
        }
        if (handleCursor(event)) return true
        return geckoView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onEdgeScroll(scrollX: Float, scrollY: Float) {
        val session = app.session ?: return
        session.panZoomController.scrollBy(
            ScreenLength.fromPixels(scrollX.toDouble()),
            ScreenLength.fromPixels(scrollY.toDouble()),
            PanZoomController.SCROLL_BEHAVIOR_AUTO,
        )
    }

    override fun onUrl(url: String) {
        if (!url.startsWith("data:")) app.lastUrl = url
    }

    override fun onCanGoBack(canGoBack: Boolean) {
        app.canGoBack = canGoBack
    }

    override fun onCanGoForward(canGoForward: Boolean) {
        app.canGoForward = canGoForward
    }

    override fun onPageStart(url: String) {
        runOnUiThread {
            progress.visibility = View.VISIBLE
            progress.isIndeterminate = true
        }
    }

    override fun onPageStop(success: Boolean) {
        runOnUiThread { progress.visibility = View.GONE }
    }

    override fun onProgress(progress: Int) {
        runOnUiThread {
            this.progress.visibility = View.VISIBLE
            this.progress.isIndeterminate = progress <= 0
            this.progress.progress = progress
        }
    }

    override fun onTitle(title: String?) {
        if (!title.isNullOrBlank()) setTitle(title)
    }

    override fun onFullScreen(fullScreen: Boolean) {
        runOnUiThread {
            this.fullScreen = fullScreen
            if (fullScreen) {
                urlBar.close()
                menu.close()
            }
            setSystemBarsHidden(fullScreen)
            videoControls.pageFullScreen = fullScreen
            updateChrome()
        }
    }

    override fun onContentProcessGone() {
        runOnUiThread {
            Log.e(TAG, "Content process gone; reloading")
            showStatus(getString(R.string.content_crashed))
            val session = app.session ?: return@runOnUiThread
            try {
                if (!session.isOpen) session.open(app.runtime)
            } catch (error: Exception) {
                Log.e(TAG, "Reopen after content-process crash failed", error)
            }
            session.loadUri(app.lastUrl ?: prefs.homePage)
        }
    }

    override fun pickFile(
        prompt: GeckoSession.PromptDelegate.FilePrompt,
        result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
    ) {
        pendingFilePrompt = prompt
        pendingFileResult = result
        val mime = prompt.mimeTypes?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
        try {
            when (prompt.type) {
                GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE -> openDocuments.launch(mime)
                GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER -> openTree.launch(null)
                else -> openDocument.launch(mime)
            }
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No file picker", error)
            showStatus(getString(R.string.file_picker_missing))
            pendingFilePrompt = null
            pendingFileResult = null
            result.complete(prompt.dismiss())
        }
    }

    private fun deliverFile(uris: Array<Uri>?) {
        val prompt = pendingFilePrompt
        val result = pendingFileResult
        pendingFilePrompt = null
        pendingFileResult = null
        if (prompt == null || result == null) return
        if (uris.isNullOrEmpty()) {
            result.complete(prompt.dismiss())
            return
        }
        for (uri in uris) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
        }
        result.complete(prompt.confirm(this, uris))
    }

    private fun attachSession() {
        val session = app.session ?: createSession().also { app.session = it }
        bindDelegates(session)
        if (geckoView.session === session) return
        try {
            geckoView.setSession(session)
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Session still attached to a previous view", error)
            geckoView.postDelayed({
                if (isDestroyed || geckoView.session === session) return@postDelayed
                try {
                    geckoView.setSession(session)
                } catch (again: IllegalStateException) {
                    Log.e(TAG, "Could not attach GeckoSession", again)
                }
            }, 50)
        }
    }

    private fun createSession(): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .userAgentMode(userAgentMode())
            .viewportMode(viewportMode())
            .build()
        val session = GeckoSession(settings)
        session.open(app.runtime)
        return session
    }

    private fun bindDelegates(session: GeckoSession) {
        val delegates = SessionDelegates(this, promptUi)
        session.navigationDelegate = delegates
        session.contentDelegate = delegates
        session.progressDelegate = delegates
        session.promptDelegate = delegates
        session.mediaSessionDelegate = videoControls
    }

    private fun startInitialLoad() {
        if (app.initialLoadStarted) return
        app.initialLoadStarted = true
        val home = prefs.homePage.ifBlank { UrlInput.DEFAULT_HOME }
        Log.i(TAG, "Initial load $home")
        app.session?.loadUri(home)
    }

    private fun navigate(raw: String) {
        val uri = UrlInput.resolve(raw, prefs.searchTemplate)
        if (uri.isEmpty()) return
        urlBar.close()
        updateChrome()
        geckoView.requestFocus()
        app.session?.loadUri(uri)
    }

    private fun handleBack() {
        when {
            urlBar.isOpen -> {
                urlBar.close()
                updateChrome()
                geckoView.requestFocus()
            }
            menu.isOpen -> closeMenu()
            fullScreen -> app.session?.exitFullScreen()
            app.canGoBack -> app.session?.goBack()
            else -> finish()
        }
    }

    private fun handleCursor(event: KeyEvent): Boolean {
        val code = event.keyCode
        val arrows = code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN
        val center = code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!arrows && !center) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (arrows) {
                if (heldKeys.add(code) && heldKeys.size == 1) {
                    holdFrames = 0
                    cursorHandler.post(ticker)
                }
            } else if (event.repeatCount == 0) {
                centerLongFired = false
                cursorHandler.postDelayed(longPress, longPressTimeout())
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (arrows) {
                heldKeys.remove(code)
                if (heldKeys.isEmpty()) holdFrames = 0
            } else {
                cursorHandler.removeCallbacks(longPress)
                if (!centerLongFired) clickCursor()
                centerLongFired = false
            }
            return true
        }
        return false
    }

    private fun clickCursor() {
        geckoView.requestFocus()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            cursor.pointerX,
            cursor.pointerY,
            0,
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime + 40,
            MotionEvent.ACTION_UP,
            cursor.pointerX,
            cursor.pointerY,
            0,
        )
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        geckoView.dispatchTouchEvent(down)
        geckoView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun openMenu() {
        urlBar.close()
        val extension = app.webExtension
        val header = when {
            extension != null -> getString(R.string.menu_ubo_ok, extension.metaData.version)
            app.extensionFailure != null -> getString(R.string.menu_ubo_failed)
            else -> getString(R.string.menu_ubo_pending)
        }
        val siteMode = if (prefs.desktopMode) {
            getString(R.string.menu_use_mobile)
        } else {
            getString(R.string.menu_use_desktop)
        }
        menu.show(
            header,
            listOf(
                ControlMenu.Item(ID_ADDRESS, getString(R.string.menu_address)),
                ControlMenu.Item(ID_BACK, getString(R.string.menu_back)),
                ControlMenu.Item(ID_FORWARD, getString(R.string.menu_forward)),
                ControlMenu.Item(ID_RELOAD, getString(R.string.menu_reload)),
                ControlMenu.Item(ID_HOME, getString(R.string.menu_home)),
                ControlMenu.Item(ID_SET_HOME, getString(R.string.menu_set_home)),
                ControlMenu.Item(ID_UA, siteMode),
                ControlMenu.Item(
                    ID_SPEED,
                    getString(R.string.menu_cursor_speed, CursorSpeed.label(prefs.cursorSpeed)),
                ),
                ControlMenu.Item(ID_DASHBOARD, getString(R.string.menu_dashboard)),
                ControlMenu.Item(ID_CLEAR, getString(R.string.menu_clear_data)),
                ControlMenu.Item(ID_SEARCH, getString(R.string.menu_search)),
                ControlMenu.Item(ID_EXIT, getString(R.string.menu_exit)),
            ),
        )
        updateChrome()
    }

    private fun closeMenu() {
        menu.close()
        updateChrome()
        geckoView.requestFocus()
    }

    private fun openAddress() {
        menu.close()
        updateChrome()
        urlBar.open(app.lastUrl)
    }

    private fun onMenu(id: String) {
        when (id) {
            ID_ADDRESS -> openAddress()
            ID_BACK -> {
                closeMenu()
                if (app.canGoBack) app.session?.goBack() else showStatus(getString(R.string.cant_go_back))
            }
            ID_FORWARD -> {
                closeMenu()
                if (app.canGoForward) {
                    app.session?.goForward()
                } else {
                    showStatus(getString(R.string.cant_go_forward))
                }
            }
            ID_RELOAD -> {
                closeMenu()
                app.session?.reload()
            }
            ID_HOME -> {
                closeMenu()
                app.session?.loadUri(prefs.homePage.ifBlank { UrlInput.DEFAULT_HOME })
            }
            ID_SET_HOME -> {
                closeMenu()
                val url = app.lastUrl
                if (UrlInput.canSaveAsHome(url)) {
                    prefs.homePage = url!!
                    showStatus(getString(R.string.home_saved))
                } else {
                    showStatus(getString(R.string.home_not_saved))
                }
            }
            ID_UA -> {
                prefs.desktopMode = !prefs.desktopMode
                app.session?.let { session ->
                    session.settings.setUserAgentMode(userAgentMode())
                    session.settings.setViewportMode(viewportMode())
                    session.reload()
                }
                closeMenu()
            }
            ID_SPEED -> {
                prefs.cursorSpeed = CursorSpeed.next(prefs.cursorSpeed)
                showStatus(getString(R.string.menu_cursor_speed, CursorSpeed.label(prefs.cursorSpeed)))
                closeMenu()
            }
            ID_DASHBOARD -> {
                closeMenu()
                val url = app.webExtension?.let { ExtensionInstaller.dashboardUrl(it) }
                if (url == null) {
                    showStatus(getString(R.string.dashboard_unavailable))
                } else {
                    app.session?.loadUri(url)
                }
            }
            ID_CLEAR -> {
                closeMenu()
                confirmClearData()
            }
            ID_SEARCH -> {
                closeMenu()
                editSearchEngine()
            }
            ID_EXIT -> finish()
        }
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_data_title)
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.menu_clear_data) { _, _ -> clearSiteData() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearSiteData() {
        app.runtime.storageController
            .clearData(StorageController.ClearFlags.SITE_DATA)
            .accept(
                {
                    runOnUiThread {
                        showStatus(getString(R.string.clear_data_done))
                        app.session?.reload()
                    }
                },
                { error ->
                    Log.e(TAG, "clearData failed", error)
                    runOnUiThread { showStatus(getString(R.string.clear_data_failed)) }
                },
            )
    }

    private fun editSearchEngine() {
        val view = layoutInflater.inflate(R.layout.dialog_text, null)
        val message = view.findViewById<TextView>(R.id.dialog_message)
        val input = view.findViewById<android.widget.EditText>(R.id.dialog_input)
        message.setText(R.string.search_message)
        input.setText(prefs.searchTemplate)
        input.setSelection(input.text.length)
        AlertDialog.Builder(this)
            .setTitle(R.string.search_title)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val entered = input.text.toString().trim()
                prefs.searchTemplate = entered.ifEmpty { UrlInput.DEFAULT_SEARCH }
                showStatus(getString(R.string.search_saved))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
        status.removeCallbacks(hideStatus)
        status.postDelayed(hideStatus, 2500)
    }

    private fun showWarning() {
        extensionFailed = true
        if (!fullScreen) warning.visibility = View.VISIBLE
    }

    private fun updateChrome() {
        val hidePointer = fullScreen || menu.isOpen || urlBar.isOpen
        cursor.visibility = if (hidePointer) View.INVISIBLE else View.VISIBLE
        if (fullScreen) {
            warning.visibility = View.GONE
        } else if (extensionFailed) {
            warning.visibility = View.VISIBLE
        }
    }

    private fun userAgentMode(): Int {
        return if (prefs.desktopMode) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
    }

    private fun viewportMode(): Int {
        return if (prefs.desktopMode) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            val bars = WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            if (hidden) {
                controller.hide(bars)
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(bars)
            }
        } else if (hidden) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun longPressTimeout(): Long {
        return ViewConfiguration.getLongPressTimeout().toLong()
    }

    private companion object {
        const val TAG = "CosmoWatch"
        const val FRAME_MS = 16L
        const val ID_ADDRESS = "address"
        const val ID_BACK = "back"
        const val ID_FORWARD = "forward"
        const val ID_RELOAD = "reload"
        const val ID_HOME = "home"
        const val ID_SET_HOME = "set_home"
        const val ID_UA = "ua"
        const val ID_SPEED = "speed"
        const val ID_DASHBOARD = "dashboard"
        const val ID_CLEAR = "clear"
        const val ID_SEARCH = "search"
        const val ID_EXIT = "exit"
    }
}
