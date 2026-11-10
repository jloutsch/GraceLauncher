@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.data.MediaPlayerStyle
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.endPadding
import com.galaxyrio.gracelauncher.ui.components.startPadding
import com.galaxyrio.gracelauncher.ui.home.HomeMediaPlayerPreview
import com.galaxyrio.gracelauncher.ui.theme.LauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherTypography
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

private enum class MediaAppearancePage { Presets, Style, Color }
private val MediaStyleSaver = Saver<MediaPlayerStyle, String>(save = { it.encode() }, restore = { MediaPlayerStyle.decode(it) })

@Composable
internal fun MediaPlayerAppearanceSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = MediaStyleSaver) { mutableStateOf(uiState.settings.mediaStyle.normalized()) }
    var baseline by rememberSaveable(stateSaver = MediaStyleSaver) { mutableStateOf(draft) }
    var saving by remember { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var editingCoverColor by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(MediaAppearancePage.Style) }
    val enabled = !saving && LocalSettingsStorageState.current.canEdit
    val dirty = draft != baseline
    fun saveAndExit() {
        if (!enabled) return
        val applied = draft.normalized()
        saving = true
        scope.launch {
            try {
                if (actions.applyMediaStyle(applied)) { baseline = applied; confirmExit = false; onBack() }
                else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }
    val back: () -> Unit = {
        if (!saving) when {
            editingCoverColor -> editingCoverColor = false
            dirty -> confirmExit = true
            else -> onBack()
        }
    }
    BackHandler(onBack = back)
    if (confirmExit) AlertDialog(
        onDismissRequest = { if (!saving) confirmExit = false },
        title = { Text(stringResource(R.string.icon_designer_unsaved_title)) },
        text = { Text(stringResource(R.string.media_unsaved_message)) },
        confirmButton = { TextButton(onClick = ::saveAndExit, enabled = enabled) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmExit = false }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { confirmExit = false; onBack() }, enabled = !saving) { Text(stringResource(R.string.icon_designer_discard)) }
            }
        }, modifier = Modifier.testTag("media_unsaved"),
    )
    val sample = rememberMediaPreviewContent()
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons,
        uiState.settings.iconDesign?.design?.iconSize ?: 100)
    val presetsState = rememberLazyListState()
    val styleState = rememberLazyListState()
    val colorsState = rememberLazyListState()
    val transition = updateTransition(page, label = "mediaEditorPage")
    val reportSystemBarBackground = LocalSettingsSystemBarBackground.current
    val systemBarBackground = if (transition.currentState == MediaAppearancePage.Presets &&
        transition.targetState == MediaAppearancePage.Presets) SettingsSystemBarBackground.Surface
        else SettingsSystemBarBackground.WallpaperWithBottomScrim

    CompositionLocalProvider(LocalSettingsSystemBarBackground provides { reportSystemBarBackground(systemBarBackground) }) {
        SettingsPreviewScaffold(uiState, stringResource(R.string.settings_media_player), "media_appearance", back,
            actions = { SettingsApplyAction(stringResource(R.string.save), enabled = enabled,
                modifier = Modifier.testTag("media_style_apply"), onClick = ::saveAndExit) }) { padding ->
            val contentModifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            Box(Modifier.fillMaxSize()) {
                transition.AnimatedContent(
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    contentKey = { it == MediaAppearancePage.Presets },
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it * direction } togetherWith
                            slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it * direction }).using(null)
                    },
                ) { displayed ->
                    if (displayed == MediaAppearancePage.Presets) {
                        LazyColumn(
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainer).then(contentModifier)
                                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                                .testTag("media_presets").selectableGroup(),
                            state = presetsState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 96.dp + LocalSettingsBottomInset.current),
                            userScrollEnabled = displayed == page,
                        ) {
                            item("default") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MediaPresetOption(sample, appearance, draft == MediaPlayerStyle().normalized(), enabled && displayed == page, Modifier.weight(1f)) {
                                        draft = MediaPlayerStyle().normalized()
                                        page = MediaAppearancePage.Style
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else BoxWithConstraints(contentModifier) {
                        val previewMaxHeight = (maxHeight - LocalSettingsBottomInset.current).coerceAtLeast(0.dp) * .45f
                        Column(Modifier.fillMaxSize()) {
                            MediaAppearancePreview(uiState, uiState.media.nowPlaying ?: sample, appearance, draft,
                                Modifier.padding(top = 8.dp).fillMaxWidth()
                                    .heightIn(min = minOf(168.dp, previewMaxHeight), max = previewMaxHeight)
                                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()))
                            val controlsModifier = Modifier.weight(1f).fillMaxWidth().settingsPreviewScrim()
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                                    .clip(settingsPreviewControlsShape())
                            if (editingCoverColor) {
                                IconDesignerColorPicker(stringResource(R.string.media_border_color), draft.coverBorderColor,
                                    MaterialTheme.colorScheme.primary.toArgb(), MediaPlayerStyle().coverBorderColor,
                                    onChange = { if (enabled) draft = draft.copy(coverBorderColor = it ?: MediaPlayerStyle().coverBorderColor) },
                                    onClose = { editingCoverColor = false }, modifier = controlsModifier,
                                    fontColors = uiState.settings.fontColors, allowFromApp = true,
                                    appColor = MaterialTheme.colorScheme.secondaryContainer.toArgb(),
                                    fromAppLabel = stringResource(R.string.media_color_source_album),
                                    fromAppSummary = stringResource(R.string.media_album_color_summary))
                            } else LazyColumn(
                                controlsModifier.testTag("media_style_controls"),
                                state = if (displayed == MediaAppearancePage.Style) styleState else colorsState,
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                                contentPadding = PaddingValues(bottom = 96.dp + LocalSettingsBottomInset.current),
                                userScrollEnabled = displayed == page,
                            ) {
                                if (displayed == MediaAppearancePage.Style) {
                                    mediaPlayerStyleControls(draft, enabled && displayed == page,
                                        uiState.settings.iconDesign?.design ?: IconDesign(), uiState.settings.allowHapticFeedback,
                                        onChange = { transform -> if (enabled && displayed == page) draft = transform(draft).normalized() })
                                } else if (draft.layout.hasStandardCover && draft.coverBorder) {
                                    item("cover_heading") { SettingsHeading(stringResource(R.string.media_album_cover)) }
                                    item("cover_color") { MediaCoverColorSetting(draft, 0, 1, enabled && displayed == page) { editingCoverColor = true } }
                                } else item("${displayed.name}_coming_soon") {
                                    Text(stringResource(R.string.settings_coming_soon),
                                        Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
                                            .testTag("media_${displayed.name.lowercase()}_coming_soon"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                if (!editingCoverColor) Box(contentModifier) {
                    SettingsModeToolbar(
                        listOf(stringResource(R.string.clock_tab_presets), stringResource(R.string.clock_tab_style),
                            stringResource(R.string.icon_designer_color)),
                        page.ordinal, enabled, "media_style_toolbar",
                        MediaAppearancePage.entries.map { "media_tab:${it.name}" },
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = FloatingToolbarDefaults.ScreenOffset + LocalSettingsBottomInset.current),
                    ) { page = MediaAppearancePage.entries[it] }
                }
            }
        }
    }
}

@Composable
private fun rememberMediaPreviewContent(): NowPlaying {
    val playerName = stringResource(R.string.settings_media_player)
    val title = stringResource(R.string.media_preview_title)
    val artist = stringResource(R.string.media_preview_artist)
    val album = stringResource(R.string.media_album_name)
    return remember(playerName, title, artist, album) {
        NowPlaying("launcher:media-preview", playerName, title, artist,
            playing = true, canToggle = true, canPrevious = true, canNext = true, album = album,
            durationMs = 240_000, positionMs = 90_000, canSeek = true, canShuffle = true)
    }
}

@Composable
private fun MediaAppearancePreview(uiState: LauncherUiState, media: NowPlaying, appearance: LauncherAppearance, style: MediaPlayerStyle,
    modifier: Modifier) {
    val description = listOfNotNull(media.title, media.artist ?: media.playerName).joinToString(", ")
    Box(modifier.testTag("media_style_preview").clipToBounds()
        .clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.CenterStart) {
        CompositionLocalProvider(LocalLauncherAppearance provides appearance) {
            MaterialTheme(typography = LocalLauncherTypography.current) {
                Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    HomeMediaPlayerPreview(media, Modifier.padding(
                        start = uiState.settings.listAppearance.startPadding,
                        end = uiState.settings.listAppearance.endPadding, top = 12.dp, bottom = 12.dp),
                        style = style, iconDesign = uiState.settings.iconDesign?.design ?: IconDesign())
                }
            }
        }
    }
}

@Composable
private fun MediaPresetOption(media: NowPlaying, appearance: LauncherAppearance, selected: Boolean, enabled: Boolean,
    modifier: Modifier, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val interactions = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    val pressSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val releaseSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val label = stringResource(R.string.icon_edit_default)
    LaunchedEffect(interactions, pressSpec, releaseSpec) {
        interactions.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(.9f, pressSpec)
                is PressInteraction.Release -> {
                    if (scale.value > .94f) scale.animateTo(.9f, pressSpec)
                    scale.animateTo(1f, releaseSpec)
                }
                is PressInteraction.Cancel -> scale.animateTo(1f, releaseSpec)
            }
        }
    }
    Box(modifier.fillMaxWidth().aspectRatio(1.25f).testTag("media_preset:Default")
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clip(shape).background(colors.primaryContainer).then(if (selected) Modifier.border(3.dp, colors.primary, shape) else Modifier)
        .selectable(selected = selected, enabled = enabled, interactionSource = interactions, indication = ripple(),
            role = Role.RadioButton, onClick = onSelect)
        .semantics { contentDescription = label }) {
        MediaPresetPreview(media, appearance, Modifier.fillMaxSize().padding(16.dp))
    }
}

/** Use the same desktop-width measurement as clock presets, including at large font scales. */
@Composable
private fun MediaPresetPreview(media: NowPlaying, appearance: LauncherAppearance, modifier: Modifier) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val previewAppearance = remember(appearance, contentColor) {
        appearance.copy(darkText = contentColor.luminance() < .5f, textColor = contentColor)
    }
    CompositionLocalProvider(LocalLauncherAppearance provides previewAppearance) {
        MaterialTheme(typography = LocalLauncherTypography.current) {
            Layout(modifier = modifier.clearAndSetSemantics {}, content = { HomeMediaPlayerPreview(media) }) { measurables, constraints ->
                val desktopWidth = 360.dp.roundToPx().coerceAtLeast(1)
                val child = measurables.single().measure(Constraints.fixedWidth(desktopWidth))
                val width = constraints.constrainWidth(if (constraints.hasBoundedWidth) constraints.maxWidth else desktopWidth)
                val height = constraints.constrainHeight(if (constraints.hasBoundedHeight) constraints.maxHeight else child.height)
                val scale = minOf(1f, width.toFloat() / child.width.coerceAtLeast(1), height.toFloat() / child.height.coerceAtLeast(1))
                layout(width, height) {
                    child.placeWithLayer(0, 0) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = (width - child.width * scale) / 2f
                        translationY = (height - child.height * scale) / 2f
                    }
                }
            }
        }
    }
}
