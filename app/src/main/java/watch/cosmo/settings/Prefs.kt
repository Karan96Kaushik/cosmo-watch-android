package watch.cosmo.settings

import android.content.Context
import watch.cosmo.browser.UrlInput
import watch.cosmo.tv.CursorSpeed

class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var homePage: String
        get() = prefs.getString(KEY_HOME, UrlInput.DEFAULT_HOME) ?: UrlInput.DEFAULT_HOME
        set(value) {
            prefs.edit().putString(KEY_HOME, value).apply()
        }

    var searchTemplate: String
        get() = prefs.getString(KEY_SEARCH, UrlInput.DEFAULT_SEARCH) ?: UrlInput.DEFAULT_SEARCH
        set(value) {
            prefs.edit().putString(KEY_SEARCH, value).apply()
        }

    var desktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DESKTOP, value).apply()
        }

    var cursorSpeed: Int
        get() = prefs.getInt(KEY_SPEED, CursorSpeed.MEDIUM)
        set(value) {
            prefs.edit().putInt(KEY_SPEED, value).apply()
        }

    private companion object {
        const val NAME = "cosmo_watch"
        const val KEY_HOME = "home_page"
        const val KEY_SEARCH = "search_template"
        const val KEY_DESKTOP = "desktop_mode"
        const val KEY_SPEED = "cursor_speed"
    }
}
