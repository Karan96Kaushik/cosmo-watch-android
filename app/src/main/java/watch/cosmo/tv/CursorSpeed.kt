package watch.cosmo.tv

object CursorSpeed {
    const val SLOW = 0
    const val MEDIUM = 1
    const val FAST = 2

    fun label(level: Int): String = when (level) {
        SLOW -> "Slow"
        FAST -> "Fast"
        else -> "Medium"
    }

    fun next(level: Int): Int = when (level) {
        SLOW -> MEDIUM
        MEDIUM -> FAST
        else -> SLOW
    }

    /**
     * Pixels to move on one 16ms tick. Holding the D-pad speeds the cursor up
     * and then levels off so a long press can cross a 1080p screen.
     */
    fun pixelsPerFrame(level: Int, holdFrames: Int): Float {
        val base = when (level) {
            SLOW -> 8f
            FAST -> 22f
            else -> 14f
        }
        val accel = 1f + holdFrames.coerceIn(0, 40) * 0.06f
        return base * accel
    }
}
