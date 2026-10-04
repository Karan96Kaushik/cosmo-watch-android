package watch.cosmo.tv

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import watch.cosmo.R

class ControlMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {
    data class Item(val id: String, val label: String)

    var onItem: ((String) -> Unit)? = null

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val header = TextView(context).apply {
        setTextColor(0xFF7CDBFF.toInt())
        textSize = 22f
        setPadding(dp(8), dp(4), dp(8), dp(16))
    }

    val isOpen: Boolean
        get() = visibility == VISIBLE

    init {
        isFillViewport = true
        setBackgroundColor(0xF2101218.toInt())
        val pad = dp(36)
        setPadding(pad, pad, dp(48), pad)
        column.addView(header)
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun show(title: String, items: List<Item>) {
        header.text = title
        while (column.childCount > 1) {
            column.removeViewAt(column.childCount - 1)
        }
        items.forEach { item ->
            column.addView(makeButton(item))
        }
        visibility = VISIBLE
        post {
            val first = if (column.childCount > 1) column.getChildAt(1) else header
            first.requestFocus()
        }
    }

    fun close() {
        visibility = GONE
    }

    private fun makeButton(item: Item): Button {
        return Button(context).apply {
            text = item.label
            isAllCaps = false
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = dp(64)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundResource(R.drawable.menu_button_bg)
            stateListAnimator = null
            setOnClickListener { onItem?.invoke(item.id) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
