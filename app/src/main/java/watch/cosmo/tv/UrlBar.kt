package watch.cosmo.tv

import android.content.Context
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import watch.cosmo.R

class UrlBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    var onSubmit: ((String) -> Unit)? = null

    private val field: EditText
    val isOpen: Boolean
        get() = visibility == VISIBLE

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xF2101218.toInt())
        val pad = dp(16)
        setPadding(pad, pad, pad, pad)
        field = EditText(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(0xFFF4F7FB.toInt())
            setHintTextColor(0xFFB7BDC8.toInt())
            textSize = 24f
            hint = context.getString(R.string.url_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_GO
            isSingleLine = true
            setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    enter
                ) {
                    submit()
                    true
                } else {
                    false
                }
            }
        }
        val go = Button(context).apply {
            text = context.getString(R.string.go)
            isAllCaps = false
            textSize = 24f
            setTextColor(0xFFF4F7FB.toInt())
            setBackgroundResource(R.drawable.menu_button_bg)
            setOnClickListener { submit() }
        }
        addView(field)
        addView(go)
    }

    fun open(currentUrl: String?) {
        visibility = VISIBLE
        val shown = currentUrl?.takeIf {
            it.startsWith("http://") || it.startsWith("https://") || it.startsWith("moz-extension://")
        }.orEmpty()
        field.setText(shown)
        field.setSelection(field.text?.length ?: 0)
        field.requestFocus()
        field.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                field.windowInsetsController?.show(WindowInsets.Type.ime())
            } else {
                val imm = context.getSystemService(InputMethodManager::class.java)
                @Suppress("DEPRECATION")
                imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun close() {
        if (!isOpen) return
        visibility = GONE
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun submit() {
        onSubmit?.invoke(field.text?.toString().orEmpty())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
