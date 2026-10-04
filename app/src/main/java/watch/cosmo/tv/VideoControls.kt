package watch.cosmo.tv

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSession as PlatformMediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession as GeckoMediaSession
import watch.cosmo.R

/**
 * Bridges GeckoView's media session to the Fire TV remote. Pages that play
 * through an HTML media element activate a [GeckoMediaSession]; play, pause,
 * and seek on that object are what the remote calls.
 */
class VideoControls(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onOpenMenu: () -> Unit,
) : GeckoMediaSession.Delegate {
    var pageFullScreen: Boolean = false

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val platformCallback = object : PlatformMediaSession.Callback() {
        override fun onPlay() = perform(VideoKeyMap.Command.PLAY, fast = false)
        override fun onPause() = perform(VideoKeyMap.Command.PAUSE, fast = false)
        override fun onStop() = perform(VideoKeyMap.Command.STOP, fast = false)
        override fun onSkipToNext() = perform(VideoKeyMap.Command.NEXT, fast = false)
        override fun onSkipToPrevious() = perform(VideoKeyMap.Command.PREVIOUS, fast = false)
        override fun onFastForward() = perform(VideoKeyMap.Command.SEEK_FORWARD, fast = true)
        override fun onRewind() = perform(VideoKeyMap.Command.SEEK_BACKWARD, fast = true)
        override fun onSeekTo(pos: Long) {
            seekToSeconds(pos / 1000.0, fast = false)
        }
    }
    private val platform = PlatformMediaSession(appContext, "CosmoWatch").apply {
        @Suppress("DEPRECATION")
        setFlags(
            PlatformMediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                PlatformMediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
        )
        setCallback(platformCallback, handler)
    }

    private var gecko: GeckoMediaSession? = null
    private var playing = false
    private var position = 0.0
    private var duration = 0.0
    private var rate = 1.0
    private var hasPosition = false
    private var features = 0L
    private var title: String? = null
    private var artist: String? = null
    private var elementFullScreen = false
    private var centerLongFired = false
    private var lastSeekUptime = 0L

    private val longPress = Runnable {
        centerLongFired = true
        onOpenMenu()
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (gecko?.isActive != true) return false
        val fromDpad = pageFullScreen || elementFullScreen
        val command = VideoKeyMap.transport(event.keyCode)
            ?: if (fromDpad) VideoKeyMap.dpad(event.keyCode) else null
        if (command == null) return false
        if (fromDpad && command == VideoKeyMap.Command.TOGGLE && VideoKeyMap.isSelect(event.keyCode)) {
            return handleSelect(event)
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (!VideoKeyMap.acceptsRepeat(command) && event.repeatCount != 0) return true
        if (command == VideoKeyMap.Command.SEEK_FORWARD || command == VideoKeyMap.Command.SEEK_BACKWARD) {
            val now = SystemClock.uptimeMillis()
            if (!VideoKeyMap.allowRepeatedSeek(now, lastSeekUptime, event.repeatCount)) return true
            lastSeekUptime = now
        }
        perform(command, fast = event.repeatCount > 0)
        return true
    }

    fun cancelHold() {
        handler.removeCallbacks(longPress)
        centerLongFired = false
    }

    fun release() {
        cancelHold()
        platform.isActive = false
        platform.release()
        gecko = null
    }

    override fun onActivated(session: GeckoSession, mediaSession: GeckoMediaSession) {
        onMain {
            gecko = mediaSession
            publish()
        }
    }

    override fun onDeactivated(session: GeckoSession, mediaSession: GeckoMediaSession) {
        onMain {
            if (gecko !== mediaSession && gecko != null) return@onMain
            gecko = null
            playing = false
            elementFullScreen = false
            hasPosition = false
            publish()
        }
    }

    override fun onMetadata(
        session: GeckoSession,
        mediaSession: GeckoMediaSession,
        meta: GeckoMediaSession.Metadata,
    ) {
        onMain {
            if (!owns(mediaSession)) return@onMain
            title = meta.title
            artist = meta.artist
            publish()
        }
    }

    override fun onFeatures(session: GeckoSession, mediaSession: GeckoMediaSession, features: Long) {
        onMain {
            if (!owns(mediaSession)) return@onMain
            this.features = features
            publish()
        }
    }

    override fun onPlay(session: GeckoSession, mediaSession: GeckoMediaSession) {
        onMain {
            if (!owns(mediaSession)) return@onMain
            playing = true
            publish()
        }
    }

    override fun onPause(session: GeckoSession, mediaSession: GeckoMediaSession) {
        onMain {
            if (!owns(mediaSession)) return@onMain
            playing = false
            publish()
        }
    }

    override fun onStop(session: GeckoSession, mediaSession: GeckoMediaSession) {
        onMain {
            if (!owns(mediaSession)) return@onMain
            playing = false
            publish()
        }
    }

    override fun onPositionState(
        session: GeckoSession,
        mediaSession: GeckoMediaSession,
        state: GeckoMediaSession.PositionState,
    ) {
        onMain {
            if (!owns(mediaSession)) return@onMain
            position = state.position
            duration = state.duration
            rate = state.playbackRate
            hasPosition = true
            publish()
        }
    }

    override fun onFullscreen(
        session: GeckoSession,
        mediaSession: GeckoMediaSession,
        enabled: Boolean,
        meta: GeckoMediaSession.ElementMetadata?,
    ) {
        onMain {
            if (!owns(mediaSession)) return@onMain
            elementFullScreen = enabled
            if (meta != null && meta.duration > 0.0) duration = meta.duration
            publish()
        }
    }

    private fun handleSelect(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            centerLongFired = false
            handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(longPress)
            if (!centerLongFired) perform(VideoKeyMap.Command.TOGGLE, fast = false)
            centerLongFired = false
            return true
        }
        return true
    }

    private fun perform(command: VideoKeyMap.Command, fast: Boolean) {
        val media = gecko ?: return
        when (command) {
            VideoKeyMap.Command.PLAY -> {
                media.play()
                playing = true
                onStatus(appContext.getString(R.string.video_playing, label()))
            }
            VideoKeyMap.Command.PAUSE -> {
                media.pause()
                playing = false
                onStatus(appContext.getString(R.string.video_paused, label()))
            }
            VideoKeyMap.Command.TOGGLE -> {
                if (playing) perform(VideoKeyMap.Command.PAUSE, fast) else perform(VideoKeyMap.Command.PLAY, fast)
                return
            }
            VideoKeyMap.Command.STOP -> {
                media.stop()
                playing = false
                onStatus(appContext.getString(R.string.video_stopped))
            }
            VideoKeyMap.Command.SEEK_FORWARD -> seek(media, forward = true, fast = fast)
            VideoKeyMap.Command.SEEK_BACKWARD -> seek(media, forward = false, fast = fast)
            VideoKeyMap.Command.NEXT -> {
                media.nextTrack()
                onStatus(appContext.getString(R.string.video_next))
            }
            VideoKeyMap.Command.PREVIOUS -> {
                media.previousTrack()
                onStatus(appContext.getString(R.string.video_previous))
            }
            VideoKeyMap.Command.VOLUME_UP -> adjustVolume(raise = true)
            VideoKeyMap.Command.VOLUME_DOWN -> adjustVolume(raise = false)
        }
        publish()
    }

    private fun seek(media: GeckoMediaSession, forward: Boolean, fast: Boolean) {
        val delta = if (forward) VideoKeyMap.SEEK_STEP_SECONDS else -VideoKeyMap.SEEK_STEP_SECONDS
        val absolute = hasPosition && (
            features == 0L || features and GeckoMediaSession.Feature.SEEK_TO != 0L
            )
        if (absolute) {
            position = VideoKeyMap.seekTarget(position, duration, delta)
            media.seekTo(position, fast)
            onStatus(VideoKeyMap.positionLabel(position, duration))
            return
        }
        if (forward) {
            media.seekForward()
            onStatus(appContext.getString(R.string.video_seek_forward))
        } else {
            media.seekBackward()
            onStatus(appContext.getString(R.string.video_seek_backward))
        }
    }

    private fun seekToSeconds(seconds: Double, fast: Boolean) {
        val media = gecko ?: return
        position = VideoKeyMap.seekTarget(seconds, duration, 0.0)
        hasPosition = true
        media.seekTo(position, fast)
        onStatus(VideoKeyMap.positionLabel(position, duration))
        publish()
    }

    private fun adjustVolume(raise: Boolean) {
        audio?.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    private fun label(): String = VideoKeyMap.positionLabel(position, duration)

    private fun owns(mediaSession: GeckoMediaSession): Boolean = gecko === mediaSession

    private fun publish() {
        val active = gecko?.isActive == true
        val state = when {
            !active -> PlaybackState.STATE_NONE
            playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_FAST_FORWARD or
            PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS
        val positionMs = if (hasPosition) {
            (position * 1000.0).toLong()
        } else {
            PlaybackState.PLAYBACK_POSITION_UNKNOWN
        }
        platform.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, positionMs, if (playing) rate.toFloat() else 0f)
                .build(),
        )
        platform.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title.orEmpty())
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, artist.orEmpty())
                .putLong(
                    android.media.MediaMetadata.METADATA_KEY_DURATION,
                    if (duration > 0.0) (duration * 1000.0).toLong() else 0L,
                )
                .build(),
        )
        platform.isActive = active
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) {
            block()
        } else {
            handler.post(block)
        }
    }
}
