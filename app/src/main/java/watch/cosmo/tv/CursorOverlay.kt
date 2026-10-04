package watch.cosmo.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CursorOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    interface Listener {
        fun onEdgeScroll(scrollX: Float, scrollY: Float)
    }

    var listener: Listener? = null
    var pointerX = 0f
        private set
    var pointerY = 0f
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val radius = 18f * resources.displayMetrics.density
    private val edge = 28f * resources.displayMetrics.density
    private var placed = false

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    fun moveBy(dx: Float, dy: Float) {
        if (width == 0 || height == 0) return
        val moved = CursorMotion.move(
            pointerX,
            pointerY,
            dx,
            dy,
            width.toFloat(),
            height.toFloat(),
            edge,
        )
        pointerX = moved.x
        pointerY = moved.y
        invalidate()
        if (moved.scrollX != 0f || moved.scrollY != 0f) {
            listener?.onEdgeScroll(moved.scrollX, moved.scrollY)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!placed && w > 0 && h > 0) {
            pointerX = w / 2f
            pointerY = h / 2f
            placed = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        val ring = radius
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ring * 0.34f
        paint.color = Color.BLACK
        canvas.drawCircle(pointerX, pointerY, ring, paint)
        paint.strokeWidth = ring * 0.16f
        paint.color = Color.WHITE
        canvas.drawCircle(pointerX, pointerY, ring, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(pointerX, pointerY, ring * 0.16f, paint)
    }
}
