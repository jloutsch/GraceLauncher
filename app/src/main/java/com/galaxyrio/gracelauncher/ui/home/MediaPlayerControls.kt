@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.MediaPlaybackButtonStyle
import com.galaxyrio.gracelauncher.data.MediaPlayerStyle
import com.galaxyrio.gracelauncher.data.MediaProgressStyle
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import kotlinx.coroutines.delay

@Composable
internal fun MediaPlayerControls(media: NowPlaying, style: MediaPlayerStyle, enabled: Boolean, interactive: Boolean,
    onCommand: (String, MediaCommand) -> Unit, modifier: Modifier = Modifier, trackRipple: MediaTrackRipple? = null) {
    // Keep the native controls while opting out of their extra 48dp layout reservation here.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (style.hasSeparateProgress) 6.dp else 0.dp)) {
            if (style.showPlaybackControls) MediaTransportButtons(media, style, enabled, interactive, onCommand, trackRipple)
            if (style.hasSeparateProgress) MediaProgress(media, style.progressStyle, enabled, interactive, onCommand)
        }
    }
}

private enum class TransportButton(val tag: String, val command: MediaCommand) {
    Previous("media_previous", MediaCommand.Previous),
    PlayPause("media_toggle", MediaCommand.TogglePlayback),
    Next("media_next", MediaCommand.Next);

    fun available(media: NowPlaying): Boolean = when (this) {
        Previous -> media.canPrevious
        PlayPause -> media.canToggle
        Next -> media.canNext
    }

    fun label(playing: Boolean): Int = when (this) {
        Previous -> R.string.media_previous
        PlayPause -> if (playing) R.string.media_pause else R.string.media_play
        Next -> R.string.media_next
    }
}

@Composable
private fun MediaTransportButtons(media: NowPlaying, style: MediaPlayerStyle, enabled: Boolean, interactive: Boolean,
    onCommand: (String, MediaCommand) -> Unit, trackRipple: MediaTrackRipple?) {
    val color = LocalLauncherAppearance.current.text
    val disabledColor = color.copy(alpha = color.alpha * .38f)
    val height = if (style.hasSeparateProgress) 24.dp else 40.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        // Retain horizontal space for all three buttons even in a narrow Mini layout.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), enabled = 144.dp > availableWidth)) {
            Row(Modifier.widthIn(min = availableWidth), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TransportButton.entries.forEach { button ->
                    val label = stringResource(button.label(media.playing))
                    MediaControlButton(
                        onClick = { onCommand(media.sessionId, button.command) },
                        enabled = enabled && button.available(media), interactive = interactive,
                        modifier = Modifier.width(48.dp).height(height).clip(CircleShape).testTag(button.tag)
                            .semantics { contentDescription = label },
                        color = color, disabledColor = disabledColor,
                        trackRipple = trackRipple.takeIf { button != TransportButton.PlayPause },
                    ) {
                        Box(Modifier.width(48.dp).height(height), contentAlignment = Alignment.Center) {
                            MediaTransportGlyph(button, style.playbackButtonStyle, media.playing)
                            if (button == TransportButton.PlayPause && style.showProgress && style.progressStyle == MediaProgressStyle.Circular) {
                                MediaCircularProgress(media, enabled, interactive, color)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTransportGlyph(button: TransportButton, style: MediaPlaybackButtonStyle, playing: Boolean) {
    val icon = when (button) {
        TransportButton.Previous -> if (style == MediaPlaybackButtonStyle.Circle) R.drawable.ms_skip_previous else R.drawable.ms_chevron_backward
        TransportButton.Next -> if (style == MediaPlaybackButtonStyle.Circle) R.drawable.ms_skip_next else R.drawable.ms_chevron_forward
        TransportButton.PlayPause -> when (style) {
            MediaPlaybackButtonStyle.Circle -> if (playing) R.drawable.ms_pause_filled else R.drawable.ms_play_arrow_filled
            MediaPlaybackButtonStyle.Chevron -> if (playing) R.drawable.ms_pause else R.drawable.ms_play_arrow
        }
    }
    Icon(painterResource(icon), null, Modifier.size(24.dp))
}

/** Passive samples reuse the desktop glyphs and tracks inside the settings radio rows. */
@Composable
internal fun MediaPlaybackStylePreview(style: MediaPlaybackButtonStyle, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        TransportButton.entries.forEach { button -> MediaTransportGlyph(button, style, playing = false) }
    }
}

@Composable
internal fun MediaProgressStylePreview(style: MediaProgressStyle, buttonStyle: MediaPlaybackButtonStyle,
    modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    if (style == MediaProgressStyle.Circular) {
        Box(modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            MediaTransportGlyph(TransportButton.PlayPause, buttonStyle, playing = false)
            MediaProgressRing({ .4f }, color)
        }
    } else MediaSeekBar(.4f, style, color, enabled = true, interactive = false, playing = true, modifier = modifier)
}

/** Position ticks stay in the progress leaf; playback never remeasures artwork or metadata. */
@Composable
private fun rememberMediaPosition(media: NowPlaying, enabled: Boolean, interactive: Boolean): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(media.positionAt(SystemClock.elapsedRealtime()), media, lifecycle, enabled, interactive) {
        value = media.positionAt(SystemClock.elapsedRealtime())
        if (interactive && enabled && media.playbackSpeed != 0f && media.durationMs > 0) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    value = media.positionAt(SystemClock.elapsedRealtime())
                    delay(250)
                }
            }
        }
    }
}

@Composable
private fun MediaCircularProgress(media: NowPlaying, enabled: Boolean, interactive: Boolean, color: Color) {
    val position = rememberMediaPosition(media, enabled, interactive)
    val hasDuration = media.durationMs > 0
    val shownColor = if (enabled && hasDuration) color else color.copy(alpha = color.alpha * .38f)
    val label = stringResource(R.string.media_progress)
    MediaProgressRing(
        progress = { if (hasDuration) (position.value.toFloat() / media.durationMs).coerceIn(0f, 1f) else 0f },
        color = shownColor,
        modifier = Modifier.testTag("media_progress").semantics { contentDescription = label },
    )
}

@Composable
private fun MediaProgressRing(progress: () -> Float, color: Color, modifier: Modifier = Modifier) {
    CircularProgressIndicator(progress = progress, modifier = modifier.size(40.dp), color = color,
        trackColor = color.copy(alpha = color.alpha * .24f), strokeWidth = 2.5.dp, gapSize = 0.dp)
}

@Composable
private fun MediaProgress(media: NowPlaying, style: MediaProgressStyle, enabled: Boolean, interactive: Boolean,
    onCommand: (String, MediaCommand) -> Unit) {
    val position by rememberMediaPosition(media, enabled, interactive)
    var dragging by remember(media.sessionId, media.revision) { mutableStateOf<Float?>(null) }
    val duration = media.durationMs.coerceAtLeast(1)
    val active = enabled && media.canSeek && media.durationMs > 0
    val label = stringResource(R.string.media_progress)
    MediaSeekBar(
        progress = dragging ?: if (media.durationMs > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
        style = style, color = LocalLauncherAppearance.current.text,
        enabled = active, interactive = interactive, playing = enabled && media.playing && media.durationMs > 0,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onCommand(media.sessionId, MediaCommand.Seek((it * duration).toLong(), media.revision)) }
            dragging = null
        },
        modifier = Modifier.testTag("media_progress").semantics { contentDescription = label },
    )
}

@Composable
private fun MediaSeekBar(progress: Float, style: MediaProgressStyle, color: Color, enabled: Boolean, interactive: Boolean,
    playing: Boolean, modifier: Modifier = Modifier, onValueChange: (Float) -> Unit = {}, onValueChangeFinished: () -> Unit = {}) {
    val interactions = remember { MutableInteractionSource() }
    val shownColor = if (enabled) color else color.copy(alpha = color.alpha * .38f)
    val trackColor = color.copy(alpha = color.alpha * (if (enabled) .24f else .12f))
    val colors = SliderDefaults.colors(thumbColor = shownColor, activeTrackColor = shownColor, inactiveTrackColor = trackColor,
        disabledThumbColor = shownColor, disabledActiveTrackColor = shownColor, disabledInactiveTrackColor = trackColor)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Slider(value = progress, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
            enabled = interactive && enabled, modifier = modifier.fillMaxWidth().height(20.dp), colors = colors, interactionSource = interactions,
            thumb = {
                SliderDefaults.Thumb(interactionSource = interactions, isVertical = false, colors = colors,
                    enabled = enabled, thumbSize = DpSize(4.dp, 20.dp))
            },
            track = { state ->
                if (style == MediaProgressStyle.Wavy) {
                    MediaWavyTrack(progress = { state.value }, color = shownColor, trackColor = trackColor,
                        playing = playing, interactive = interactive)
                } else SliderDefaults.Track(sliderState = state, modifier = Modifier.height(4.dp), enabled = enabled,
                    colors = colors, drawStopIndicator = null, thumbTrackGapSize = 3.dp, trackInsideCornerSize = 2.dp)
            },
        )
    }
}

@Composable
private fun MediaWavyTrack(progress: () -> Float, color: Color, trackColor: Color, playing: Boolean, interactive: Boolean) {
    val waveState = remember { MutableTransitionState(false) }
    waveState.targetState = playing
    val transition = rememberTransition(waveState, label = "mediaWave")
    val drawingHeight by transition.animateDp(
        transitionSpec = { tween(durationMillis = if (targetState) 500 else 350, easing = FastOutSlowInEasing) },
        label = "mediaWaveHeight",
    ) { waving -> if (waving) 10.dp else 4.dp }

    // Keep the slider's layout and stroke width fixed. Only the drawing height changes,
    // smoothly reducing the wave's amplitude to zero when it equals the 4dp stroke.
    // The native renderer resets its phase when waveSpeed becomes zero, so stop it only
    // after the wave is flat, including when play/pause interrupts an earlier transition.
    val moving = interactive && (playing || drawingHeight > 4.dp)
    Box(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.Center) {
        LinearWavyProgressIndicator(progress = progress,
            modifier = Modifier.fillMaxWidth().height(drawingHeight).clearAndSetSemantics {},
            color = color, trackColor = trackColor, amplitude = { 1f },
            gapSize = 5.dp, stopSize = 0.dp, wavelength = 20.dp, waveSpeed = if (moving) 20.dp else 0.dp)
    }
}

@Composable
private fun MediaControlButton(onClick: () -> Unit, enabled: Boolean, interactive: Boolean, modifier: Modifier,
    color: Color, disabledColor: Color, trackRipple: MediaTrackRipple?, content: @Composable () -> Unit) {
    if (interactive) {
        val interactions = remember { MutableInteractionSource() }
        val buttonInteractions = remember(interactions, trackRipple) {
            if (trackRipple == null) interactions else MediaButtonIndicationSource(interactions)
        }
        val scale = rememberMediaButtonScale(interactions)
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        ForwardMediaTrackRipple(interactions, trackRipple) { coordinates }
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier.onGloballyPositioned { coordinates = it },
            interactionSource = buttonInteractions,
            colors = IconButtonDefaults.iconButtonColors(contentColor = color, disabledContentColor = disabledColor)) {
            // Animate only the visuals, keeping the button's bounds and touch target still.
            Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) { content() }
        }
    } else CompositionLocalProvider(LocalContentColor provides if (enabled) color else disabledColor) {
        Box(modifier, contentAlignment = Alignment.Center) { content() }
    }
}
