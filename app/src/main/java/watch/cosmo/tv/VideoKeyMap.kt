package watch.cosmo.tv

import android.view.KeyEvent

/**
 * Maps a Fire TV remote to playback commands. Dedicated media keys always
 * apply. The D-pad applies only while video is fullscreen, because the rest
 * of the time those keys move the cursor.
 */
object VideoKeyMap {
    const val SEEK_STEP_SECONDS = 10.0
    const val SEEK_REPEAT_INTERVAL_MS = 280L

    enum class Command {
        PLAY,
        PAUSE,
        TOGGLE,
        STOP,
        SEEK_FORWARD,
        SEEK_BACKWARD,
        NEXT,
        PREVIOUS,
        VOLUME_UP,
        VOLUME_DOWN,
    }

    fun transport(keyCode: Int): Command? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY -> Command.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> Command.PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        -> Command.TOGGLE
        KeyEvent.KEYCODE_MEDIA_STOP -> Command.STOP
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> Command.SEEK_FORWARD
        KeyEvent.KEYCODE_MEDIA_REWIND -> Command.SEEK_BACKWARD
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        -> Command.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        -> Command.PREVIOUS
        else -> null
    }

    fun dpad(keyCode: Int): Command? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> Command.SEEK_BACKWARD
        KeyEvent.KEYCODE_DPAD_RIGHT -> Command.SEEK_FORWARD
        KeyEvent.KEYCODE_DPAD_UP -> Command.VOLUME_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Command.VOLUME_DOWN
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> Command.TOGGLE
        else -> null
    }

    fun isSelect(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> true
        else -> false
    }

    fun acceptsRepeat(command: Command): Boolean = when (command) {
        Command.SEEK_FORWARD,
        Command.SEEK_BACKWARD,
        Command.VOLUME_UP,
        Command.VOLUME_DOWN,
        -> true
        else -> false
    }

    fun allowRepeatedSeek(nowMs: Long, lastSeekMs: Long, repeatCount: Int): Boolean {
        if (repeatCount == 0) return true
        return nowMs - lastSeekMs >= SEEK_REPEAT_INTERVAL_MS
    }

    fun seekTarget(position: Double, duration: Double, delta: Double): Double {
        val next = (position + delta).coerceAtLeast(0.0)
        return if (duration.isFinite() && duration > 0.0) next.coerceAtMost(duration) else next
    }

    fun clock(seconds: Double): String {
        if (!seconds.isFinite() || seconds <= 0.0) return "0:00"
        val total = seconds.toInt()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    fun positionLabel(position: Double, duration: Double): String {
        return if (duration.isFinite() && duration > 0.0) {
            "${clock(position)} / ${clock(duration)}"
        } else {
            clock(position)
        }
    }
}
