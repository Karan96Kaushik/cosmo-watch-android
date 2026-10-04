package watch.cosmo.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoKeyMapTest {
    @Test
    fun mediaKeysMapToTransport() {
        assertEquals(VideoKeyMap.Command.PLAY, VideoKeyMap.transport(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(VideoKeyMap.Command.PAUSE, VideoKeyMap.transport(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(
            VideoKeyMap.Command.TOGGLE,
            VideoKeyMap.transport(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        )
        assertEquals(
            VideoKeyMap.Command.SEEK_FORWARD,
            VideoKeyMap.transport(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
        )
        assertEquals(
            VideoKeyMap.Command.SEEK_BACKWARD,
            VideoKeyMap.transport(KeyEvent.KEYCODE_MEDIA_REWIND),
        )
        assertEquals(VideoKeyMap.Command.NEXT, VideoKeyMap.transport(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(
            VideoKeyMap.Command.PREVIOUS,
            VideoKeyMap.transport(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
        )
        assertNull(VideoKeyMap.transport(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun dpadMapsOnlyForFullscreenVideo() {
        assertEquals(VideoKeyMap.Command.SEEK_BACKWARD, VideoKeyMap.dpad(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(VideoKeyMap.Command.SEEK_FORWARD, VideoKeyMap.dpad(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(VideoKeyMap.Command.VOLUME_UP, VideoKeyMap.dpad(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(VideoKeyMap.Command.VOLUME_DOWN, VideoKeyMap.dpad(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(VideoKeyMap.Command.TOGGLE, VideoKeyMap.dpad(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(VideoKeyMap.isSelect(KeyEvent.KEYCODE_ENTER))
        assertFalse(VideoKeyMap.isSelect(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    @Test
    fun seekClampsAndThrottlesRepeats() {
        assertEquals(0.0, VideoKeyMap.seekTarget(5.0, 100.0, -10.0), 0.0)
        assertEquals(100.0, VideoKeyMap.seekTarget(95.0, 100.0, 10.0), 0.0)
        assertEquals(40.0, VideoKeyMap.seekTarget(30.0, 0.0, 10.0), 0.0)
        assertTrue(VideoKeyMap.allowRepeatedSeek(1000L, 0L, 0))
        assertFalse(VideoKeyMap.allowRepeatedSeek(1000L, 900L, 3))
        assertTrue(VideoKeyMap.allowRepeatedSeek(1000L, 700L, 3))
    }

    @Test
    fun clockFormatsHours() {
        assertEquals("0:00", VideoKeyMap.clock(0.0))
        assertEquals("1:05", VideoKeyMap.clock(65.0))
        assertEquals("1:01:01", VideoKeyMap.clock(3661.0))
        assertEquals("1:05 / 24:00", VideoKeyMap.positionLabel(65.0, 1440.0))
    }
}
