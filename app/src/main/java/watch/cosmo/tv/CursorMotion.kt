package watch.cosmo.tv

/**
 * Clamps a virtual pointer inside the view and reports the overflow so the
 * page can scroll when the pointer is held against an edge.
 */
object CursorMotion {
    data class Result(
        val x: Float,
        val y: Float,
        val scrollX: Float,
        val scrollY: Float,
    )

    fun move(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        width: Float,
        height: Float,
        edge: Float,
    ): Result {
        val minX = edge.coerceAtMost(width / 2f)
        val maxX = (width - edge).coerceAtLeast(minX)
        val minY = edge.coerceAtMost(height / 2f)
        val maxY = (height - edge).coerceAtLeast(minY)
        var nx = x + dx
        var ny = y + dy
        var scrollX = 0f
        var scrollY = 0f
        if (nx < minX) {
            scrollX = nx - minX
            nx = minX
        } else if (nx > maxX) {
            scrollX = nx - maxX
            nx = maxX
        }
        if (ny < minY) {
            scrollY = ny - minY
            ny = minY
        } else if (ny > maxY) {
            scrollY = ny - maxY
            ny = maxY
        }
        return Result(nx, ny, scrollX, scrollY)
    }
}
