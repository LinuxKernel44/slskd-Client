package com.linuxkernel44.slskdclient.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Track(val url: String, val title: String, val subtitle: String = "")

data class PlayerUi(
    val hasMedia: Boolean = false, val playing: Boolean = false, val buffering: Boolean = false,
    val title: String = "", val subtitle: String = "", val positionMs: Long = 0, val durationMs: Long = 0,
    val index: Int = 0, val count: Int = 0, val titles: List<String> = emptyList(), val error: String? = null,
)

/** UI-side handle on the playback service (MediaController), exposing a single state flow. */
class PlayerController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _ui.value = _ui.value.copy(error = error.message ?: "Playback error")
        }
    }

    init {
        scope.launch {
            while (true) {
                if (controller?.isPlaying == true) refresh()
                delay(500)
            }
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { block(it); return }
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            c.addListener(listener); controller = c; block(c); refresh()
        }, context.mainExecutor)
    }

    private fun refresh() {
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaMetadata.title?.toString().orEmpty() }
        _ui.value = PlayerUi(
            hasMedia = c.mediaItemCount > 0, playing = c.isPlaying, buffering = c.playbackState == Player.STATE_BUFFERING,
            title = c.mediaMetadata.title?.toString().orEmpty(), subtitle = c.mediaMetadata.artist?.toString().orEmpty(),
            positionMs = c.currentPosition.coerceAtLeast(0), durationMs = c.duration.coerceAtLeast(0),
            index = c.currentMediaItemIndex, count = c.mediaItemCount, titles = items, error = _ui.value.error.takeIf { c.playerError != null },
        )
    }

    fun play(tracks: List<Track>, startIndex: Int = 0) = withController { c ->
        c.setMediaItems(tracks.map {
            MediaItem.Builder().setUri(it.url).setMediaId(it.url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.subtitle).build()).build()
        }, startIndex, 0)
        c.prepare(); c.play()
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipTo(index: Int) = withController { it.seekTo(index, 0); it.play() }
    fun stop() = withController { it.stop(); it.clearMediaItems(); _ui.value = PlayerUi() }
}
