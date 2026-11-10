@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.icons.iconShapePath
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.data.MediaCoverLayout
import com.galaxyrio.gracelauncher.data.MediaInformation
import com.galaxyrio.gracelauncher.data.MediaPlayerStyle
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.components.LocalLauncherInputEnabled
import com.galaxyrio.gracelauncher.ui.components.SwipeDismissContainer
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance

/** The layout wraps the original cover, metadata and controls; all modes share their rendering. */
@Composable
internal fun HomeMediaPlayer(
    media: NowPlaying,
    onCommand: (String, MediaCommand) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (String, Long) -> Boolean = { _, _ -> false },
    keepWhilePlaying: Boolean = true,
    dismissResistance: Int = 75,
    dismissThresholdDp: Int = 72,
    style: MediaPlayerStyle = MediaPlayerStyle(),
    iconDesign: IconDesign = IconDesign(),
    albumContainerColor: Color? = null,
) {
    val appearance = LocalLauncherAppearance.current
    val enabled = LocalLauncherInputEnabled.current

    SwipeDismissContainer(
        itemKey = media.sessionId to media.revision,
        onDismiss = { onDismiss(media.sessionId, media.revision) },
        modifier = modifier.fillMaxWidth().testTag("home_media_player"),
        enabled = enabled,
        canDismiss = !keepWhilePlaying || !media.playing,
        resistance = dismissResistance,
        thresholdDp = dismissThresholdDp,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
        contentColor = appearance.text,
        // Preserve the artwork's faint shadow at rest; swipes use the rounded row clip.
        clipContent = false,
    ) {
        MediaPlayerContent(media, onCommand, enabled = enabled, interactive = true, style = style, iconDesign = iconDesign,
            albumContainerColor = albumContainerColor)
    }
}

/** Render the same artwork, metadata and controls without a media-session or gesture target. */
@Composable
internal fun HomeMediaPlayerPreview(media: NowPlaying, modifier: Modifier = Modifier,
    style: MediaPlayerStyle = MediaPlayerStyle(), iconDesign: IconDesign = IconDesign()) {
    MediaPlayerContent(media, onCommand = { _, _ -> }, enabled = true, interactive = false,
        modifier = modifier, style = style, iconDesign = iconDesign)
}

@Composable
private fun MediaPlayerContent(
    media: NowPlaying,
    onCommand: (String, MediaCommand) -> Unit,
    enabled: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    style: MediaPlayerStyle,
    iconDesign: IconDesign,
    albumContainerColor: Color? = null,
) {
    val appearance = LocalLauncherAppearance.current
    val openLabel = stringResource(R.string.media_open_player, media.playerName)
    val openPlayer = { onCommand(media.sessionId, MediaCommand.OpenPlayer) }
    val trackRipple = remember { MediaTrackRipple() }
    Box(modifier.fillMaxWidth().onGloballyPositioned { trackRipple.coordinates = it }) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = LauncherLayout.ContentInset, vertical = 3.dp)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            val buttons = if (style.showPlaybackControls) 3 else 0
            val hasControls = style.showPlaybackControls || style.showProgress
            val mini = style.layout == MediaCoverLayout.Mini
            val gap = if (style.layout == MediaCoverLayout.None) 0.dp else LauncherLayout.IconLabelGap
            val targetCoverSize = when (style.layout) {
                MediaCoverLayout.None -> 0.dp
                MediaCoverLayout.Mini -> 40.dp * appearance.iconSize / 100f
                else -> (maxWidth - maxOf(144.dp, 48.dp * buttons) - gap)
                    .coerceIn(44.dp, if (style.layout == MediaCoverLayout.Large) 128.dp else 88.dp)
                    .coerceAtMost((maxWidth - gap - 48.dp).coerceAtLeast(0.dp))
            }
            val coverSize by animateDpAsState(targetCoverSize, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "mediaCoverSize")
            val rightWidth = (maxWidth - coverSize - gap).coerceAtLeast(0.dp)
            val hasInformation = style.information.isNotEmpty()
            val informationGap = if (style.layout == MediaCoverLayout.Standard && !style.hasSeparateProgress) 0.dp else 6.dp
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (style.layout != MediaCoverLayout.None) {
                    MediaAlbumCover(media, coverSize, style, style.coverDesign(iconDesign),
                        enabled, interactive, openLabel, openPlayer,
                        if (!interactive) MaterialTheme.colorScheme.secondaryContainer
                        else albumContainerColor ?: MaterialTheme.colorScheme.primaryContainer)
                    Spacer(Modifier.width(gap))
                }
                if (mini) {
                    val controlsWidth = maxOf(48.dp * buttons, if (style.showProgress) 96.dp else 0.dp)
                        .coerceAtMost((rightWidth - if (hasInformation) 64.dp else 0.dp).coerceAtLeast(0.dp))
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        MediaSongInformation(media, style, enabled, interactive, openLabel, openPlayer, Modifier.weight(1f))
                        if (hasControls) {
                            if (hasInformation) Spacer(Modifier.width(8.dp))
                            MediaPlayerControls(media, style, enabled, interactive, onCommand, Modifier.width(controlsWidth), trackRipple)
                        }
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        MediaSongInformation(media, style, enabled, interactive, openLabel, openPlayer)
                        if (hasInformation && hasControls && informationGap > 0.dp) Spacer(Modifier.height(informationGap))
                        if (hasControls) MediaPlayerControls(media, style, enabled, interactive, onCommand, trackRipple = trackRipple)
                    }
                }
            }
        }
        // Clip just the native indication: the artwork's shadow keeps its existing bounds.
        if (interactive) Box(Modifier.matchParentSize().clip(LauncherLayout.RowShape)
            .indication(trackRipple.interactions, ripple(bounded = true, color = appearance.text)))
    }
}

@Composable
private fun MediaAlbumCover(media: NowPlaying, size: Dp, style: MediaPlayerStyle, design: IconDesign,
    enabled: Boolean, interactive: Boolean, openLabel: String, openPlayer: () -> Unit, albumContainer: Color) {
    val mini = style.layout == MediaCoverLayout.Mini
    val shape = remember(design) { mediaCoverShape(design) }
    val borderWidth = if (!mini && style.coverBorder) style.coverBorderWidth.dp else 0.dp
    val borderColor by animateColorAsState(mediaCoverBorderColor(style.coverBorderColor, albumContainer),
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "mediaCoverBorderColor")
    val placeholder = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .85f)
    Box(Modifier.size(size).testTag("home_media_artwork")
        .shadow((style.coverShadow / 10f).dp, shape, clip = false,
            ambientColor = Color.Black.copy(alpha = style.coverShadow * .006f),
            spotColor = Color.Black.copy(alpha = style.coverShadow * .008f))
        .clip(shape)
        .then(if (interactive) Modifier.clickable(enabled = enabled, onClickLabel = openLabel, onClick = openPlayer) else Modifier)
        .semantics { contentDescription = openLabel }, contentAlignment = Alignment.Center) {
        // Fit the image to the inner bounds, then use the single outer mask. Reapplying
        // a scaled mask after padding can leave gaps beneath borders on non-circular shapes.
        Box(Modifier.fillMaxSize().padding(borderWidth).background(placeholder), contentAlignment = Alignment.Center) {
            Crossfade(targetState = media.artwork, modifier = Modifier.fillMaxSize(),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "mediaArtwork") { artwork ->
                if (artwork != null) Image(artwork, null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alignment = Alignment.Center)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ms_music_note), null,
                        Modifier.size(minOf(28.dp, size * .55f)), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        if (borderWidth > 0.dp) Box(Modifier.fillMaxSize().border(borderWidth,
            borderColor.copy(alpha = borderColor.alpha * style.coverBorderOpacity / 100f), shape))
    }
}

private fun mediaCoverShape(design: IconDesign): Shape = when (design.shape) {
    IconShape.None -> RectangleShape
    IconShape.Circle -> CircleShape
    else -> GenericShape { size, _ ->
        addPath(iconShapePath(design.shape, design.cookieSides, size.minDimension,
            design.pebbleRoundness, design.squareCornerRadius, design.squareAspectRatio).asComposePath())
    }
}

@Composable
private fun MediaSongInformation(media: NowPlaying, style: MediaPlayerStyle, enabled: Boolean, interactive: Boolean,
    openLabel: String, openPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val appearance = LocalLauncherAppearance.current
    val unknownTitle = stringResource(R.string.media_unknown_title)
    val lines = style.informationLines()
    val slots = if (lines.isEmpty()) 0 else if (style.layout == MediaCoverLayout.Standard) style.informationLineCount else lines.size
    // Playback state and position callbacks are not new song content and must not restart the fade.
    val information = lines.map { fields -> fields to fields.mapNotNull { field -> when (field) {
        MediaInformation.Album -> media.album
        MediaInformation.Title -> media.title ?: unknownTitle
        MediaInformation.Artist -> media.artist ?: media.playerName
    } }.joinToString(" · ") }
    Column(modifier.fillMaxWidth().height(24.dp * slots).testTag("home_media_metadata")
        .then(if (interactive) Modifier.clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() },
            indication = null, onClickLabel = openLabel, onClick = openPlayer) else Modifier)) {
        Crossfade(targetState = information, modifier = Modifier.fillMaxSize(),
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "mediaInformation") { displayed ->
            Column(Modifier.fillMaxWidth().then(if (displayed != information) Modifier.clearAndSetSemantics {} else Modifier)) {
                displayed.forEachIndexed { index, (fields, text) ->
                    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
                        if (text.isNotEmpty()) Text(text,
                            modifier = Modifier.testTag(when (fields.singleOrNull()) {
                                MediaInformation.Title -> "home_media_title"
                                MediaInformation.Artist -> "home_media_artist"
                                else -> "home_media_information:$index"
                            }),
                            style = if (index == 0) MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, shadow = appearance.textShadow)
                            else MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, shadow = appearance.textShadow),
                            color = appearance.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
