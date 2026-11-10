package com.galaxyrio.gracelauncher.data.media

import android.media.session.PlaybackState
import androidx.compose.ui.graphics.ImageBitmap

sealed interface MediaCommand {
    data object TogglePlayback : MediaCommand
    data object Previous : MediaCommand
    data object Next : MediaCommand
    data object OpenPlayer : MediaCommand
    data object ToggleShuffle : MediaCommand
    data class Seek(val positionMs: Long, val revision: Long) : MediaCommand
}

const val IdleMediaSessionId = "launcher:idle"

data class NowPlaying(
    val sessionId: String,
    val playerName: String,
    val title: String?,
    val artist: String?,
    val artwork: ImageBitmap? = null,
    val playing: Boolean,
    val canToggle: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val revision: Long = 0,
    val album: String? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val positionUpdatedAtMs: Long = 0,
    val playbackSpeed: Float = 0f,
    val canSeek: Boolean = false,
    val canShuffle: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val artworkSeedColor: Int? = null,
) {
    /** Advance the visible progress locally, without polling the player every frame. */
    fun positionAt(elapsedRealtimeMs: Long): Long {
        val elapsed = if (positionUpdatedAtMs > 0) (elapsedRealtimeMs - positionUpdatedAtMs).coerceAtLeast(0) else 0
        val speed = playbackSpeed.takeIf { it.isFinite() } ?: 0f
        return (positionMs + (elapsed * speed).toLong()).coerceIn(0, durationMs.coerceAtLeast(0))
    }
}

data class MediaSnapshot(val hasAccess: Boolean = false, val nowPlaying: NowPlaying? = null, val failed: Boolean = false)

/** Only ranks notified players. Notification presence, not playback state, controls visibility. */
internal fun playbackPriority(state: Int): Int = when (state) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
    PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> 2
    PlaybackState.STATE_PAUSED -> 1
    else -> 0
}
