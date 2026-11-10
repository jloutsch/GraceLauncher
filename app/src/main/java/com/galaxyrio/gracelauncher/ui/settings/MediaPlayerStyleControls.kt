@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.MediaCoverLayout
import com.galaxyrio.gracelauncher.data.MediaInformation
import com.galaxyrio.gracelauncher.data.MediaPlaybackButtonStyle
import com.galaxyrio.gracelauncher.data.MediaPlayerStyle
import com.galaxyrio.gracelauncher.data.MediaProgressStyle
import com.galaxyrio.gracelauncher.ui.home.MediaPlaybackStylePreview
import com.galaxyrio.gracelauncher.ui.home.MediaProgressStylePreview
import com.galaxyrio.gracelauncher.ui.home.mediaCoverBorderColor
import kotlin.math.roundToInt

internal fun LazyListScope.mediaPlayerStyleControls(style: MediaPlayerStyle, enabled: Boolean,
    iconDesign: IconDesign, hapticsEnabled: Boolean, onChange: ((MediaPlayerStyle) -> MediaPlayerStyle) -> Unit) {
    mediaSetting("layout_heading") { SettingsHeading(stringResource(R.string.media_layout)) }
    mediaSetting("layout") {
        DesignerSegment(0, 1, "media_cover_layout") {
            DesignerConnectedChoices(MediaCoverLayout.entries, style.layout, enabled, "media_layout", { it.label() }) {
                layout -> onChange { it.copy(layout = layout) }
            }
        }
    }
    mediaSetting("cover_heading") { SettingsHeading(stringResource(R.string.media_album_cover)) }
    if (style.layout != MediaCoverLayout.None) {
        val standard = style.layout.hasStandardCover
        val count = if (!standard) 2 else if (style.coverBorder) 5 else 3
        val design = style.coverDesign(iconDesign)
        val defaults = if (standard) MediaPlayerStyle.DefaultCoverShape else iconDesign
        mediaSetting("cover_shape") {
            DesignerSegment(0, count, "media_cover_shape") {
                Text(stringResource(R.string.icon_designer_shape))
                DesignerShapeChoices(design, enabled, "media_cover", { shape -> onChange { it.withCoverShape(shape, iconDesign) } })
                DesignerShapeAdjustment(design, defaults, enabled, "media_cover") {
                    shape -> onChange { it.withCoverShape(shape, iconDesign) }
                }
            }
        }
        if (standard) mediaSetting("cover_border") {
            SettingsToggleItem(stringResource(R.string.media_cover_border), null, style.coverBorder,
                1, count, "media_cover_border", enabled) { checked -> onChange { it.copy(coverBorder = checked) } }
        }
        if (standard && style.coverBorder) {
            mediaSetting("cover_border_width") {
                DesignerSegment(2, count, "media_cover_border_width") {
                    DesignerSlider(stringResource(R.string.media_border_width), style.coverBorderWidth.toFloat(), 2f, 1f..12f,
                        enabled, "media_border_width", suffix = "dp") { value -> onChange { it.copy(coverBorderWidth = value.roundToInt()) } }
                }
            }
            mediaSetting("cover_border_opacity") {
                DesignerSegment(3, count, "media_cover_border_opacity") {
                    DesignerSlider(stringResource(R.string.media_border_opacity), style.coverBorderOpacity.toFloat(), 100f, 0f..100f,
                        enabled, "media_border_opacity") { value -> onChange { it.copy(coverBorderOpacity = value.roundToInt()) } }
                }
            }
        }
        mediaSetting("cover_shadow") { MediaCoverShadowSetting(style, count - 1, count, enabled, onChange) }
    }
    mediaSetting("information_heading") { SettingsHeading(stringResource(R.string.media_song_information)) }
    mediaSetting("information") {
        DesignerSegment(0, 1, "media_information") {
            ReorderableIconButtonGroup(style.informationOrder, style.information.toSet(), enabled,
                hapticsEnabled, "media_information", { it.label() }, { it.icon() },
                onSelect = { field -> onChange { it.toggleInformation(field) } },
                onReorder = { order -> onChange { it.withInformationOrder(order) } })
        }
    }
    mediaSetting("controls_heading") { SettingsHeading(stringResource(R.string.media_controls)) }
    val buttonOptions = if (style.showPlaybackControls) MediaPlaybackButtonStyle.entries.size else 0
    val progressOptions = if (style.showProgress) MediaProgressStyle.entries.size else 0
    val count = 2 + buttonOptions + progressOptions
    mediaSetting("playback_controls") {
        SettingsToggleItem(stringResource(R.string.media_show_playback_controls), null, style.showPlaybackControls,
            0, count, "media_show_playback_controls", enabled) { show -> onChange { it.withPlaybackControls(show) } }
    }
    if (style.showPlaybackControls) mediaSetting("playback_styles") {
        Column(Modifier.selectableGroup().testTag("media_playback_styles"),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            MediaPlaybackButtonStyle.entries.forEachIndexed { index, option ->
                MediaControlStyleOption(style.playbackButtonStyle == option, option.label(), index + 1, count,
                    "media_playback_style:${option.name}", enabled,
                    onClick = { onChange { it.copy(playbackButtonStyle = option) } }) { MediaPlaybackStylePreview(option) }
            }
        }
    }
    mediaSetting("show_progress") {
        SettingsToggleItem(stringResource(R.string.media_show_progress), null, style.showProgress,
            buttonOptions + 1, count, "media_show_progress", enabled) { show -> onChange { it.withProgress(show) } }
    }
    if (style.showProgress) mediaSetting("progress_styles") {
        Column(Modifier.selectableGroup().testTag("media_progress_styles"),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            MediaProgressStyle.entries.forEachIndexed { index, option ->
                MediaControlStyleOption(style.progressStyle == option, option.label(), buttonOptions + 2 + index, count,
                    "media_progress_style:${option.name}", enabled,
                    onClick = { onChange { it.copy(progressStyle = option).normalized() } }) {
                    MediaProgressStylePreview(option, style.playbackButtonStyle)
                }
            }
        }
    }
}

@Composable
private fun MediaControlStyleOption(checked: Boolean, label: String, index: Int, count: Int, tag: String,
    enabled: Boolean, onClick: () -> Unit, preview: @Composable () -> Unit) {
    SegmentedListItem(onClick = onClick, enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.testTag(tag).semantics {
            role = Role.RadioButton
            selected = checked
            contentDescription = label
        },
        leadingContent = { RadioButton(checked, onClick = null, enabled = enabled) },
        content = { Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) { preview() } },
    )
}

private fun LazyListScope.mediaSetting(key: String, content: @Composable () -> Unit) {
    item(key = "media_$key") {
        Box(Modifier.animateItem(fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec())) { content() }
    }
}

@Composable
internal fun MediaCoverColorSetting(style: MediaPlayerStyle, index: Int, count: Int, enabled: Boolean, onClick: () -> Unit) {
    SettingsActionItem(stringResource(R.string.media_border_color),
        if (style.coverBorderColor.fromApp) stringResource(R.string.media_album_color_summary) else fontColorSummary(style.coverBorderColor),
        index, count, "media_border_color", enabled,
        trailing = { Surface(Modifier.size(28.dp), shape = CircleShape,
            color = mediaCoverBorderColor(style.coverBorderColor, MaterialTheme.colorScheme.secondaryContainer)) {} },
        onClick = onClick)
}

@Composable
private fun MediaCoverShadowSetting(style: MediaPlayerStyle, index: Int, count: Int, enabled: Boolean,
    onChange: ((MediaPlayerStyle) -> MediaPlayerStyle) -> Unit) {
    DesignerSegment(index, count, "media_cover_shadow") {
        DesignerSlider(stringResource(R.string.media_cover_shadow), style.coverShadow.toFloat(), 20f, 0f..100f,
            enabled, "media_cover_shadow") { value -> onChange { it.copy(coverShadow = value.roundToInt()) } }
    }
}

@Composable
private fun MediaCoverLayout.label(): String = stringResource(when (this) {
    MediaCoverLayout.Mini -> R.string.media_layout_mini
    MediaCoverLayout.Standard -> R.string.media_layout_standard
    MediaCoverLayout.Large -> R.string.media_layout_large
    MediaCoverLayout.None -> R.string.media_layout_none
})

@Composable
private fun MediaInformation.label(): String = stringResource(when (this) {
    MediaInformation.Album -> R.string.media_album_name
    MediaInformation.Title -> R.string.media_preview_title
    MediaInformation.Artist -> R.string.media_preview_artist
})

private fun MediaInformation.icon(): Int = when (this) {
    MediaInformation.Album -> R.drawable.ms_album
    MediaInformation.Title -> R.drawable.ms_music_note
    MediaInformation.Artist -> R.drawable.ms_artist
}

@Composable
private fun MediaPlaybackButtonStyle.label(): String = stringResource(when (this) {
    MediaPlaybackButtonStyle.Circle -> R.string.media_playback_style_circle
    MediaPlaybackButtonStyle.Chevron -> R.string.media_playback_style_chevron
})

@Composable
private fun MediaProgressStyle.label(): String = stringResource(when (this) {
    MediaProgressStyle.Linear -> R.string.media_progress_style_linear
    MediaProgressStyle.Wavy -> R.string.media_progress_style_wavy
    MediaProgressStyle.Circular -> R.string.media_progress_style_circular
})
