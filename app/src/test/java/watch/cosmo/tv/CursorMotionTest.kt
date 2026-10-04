package watch.cosmo.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorMotionTest {
    @Test
    fun movesInsideThePage() {
        val moved = CursorMotion.move(100f, 100f, 20f, -10f, 1000f, 600f, 28f)
        assertEquals(120f, moved.x)
        assertEquals(90f, moved.y)
        assertEquals(0f, moved.scrollX)
        assertEquals(0f, moved.scrollY)
    }

    @Test
    fun bottomEdgeScrollsDown() {
        val moved = CursorMotion.move(100f, 580f, 0f, 40f, 1000f, 600f, 28f)
        assertEquals(572f, moved.y)
        assertEquals(48f, moved.scrollY)
        assertEquals(0f, moved.scrollX)
    }

    @Test
    fun topEdgeScrollsUp() {
        val moved = CursorMotion.move(100f, 30f, 0f, -20f, 1000f, 600f, 28f)
        assertEquals(28f, moved.y)
        assertEquals(-18f, moved.scrollY)
    }

    @Test
    fun speedAcceleratesThenCaps() {
        val start = CursorSpeed.pixelsPerFrame(CursorSpeed.MEDIUM, 0)
        val later = CursorSpeed.pixelsPerFrame(CursorSpeed.MEDIUM, 40)
        val beyond = CursorSpeed.pixelsPerFrame(CursorSpeed.MEDIUM, 400)
        assertEquals(14f, start)
        assertTrue(later > start)
        assertEquals(later, beyond)
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
