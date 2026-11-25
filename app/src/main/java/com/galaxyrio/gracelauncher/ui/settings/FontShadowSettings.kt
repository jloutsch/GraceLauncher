@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.FontShadow
import com.galaxyrio.gracelauncher.data.FontShadowOverride
import com.galaxyrio.gracelauncher.data.FontShadowPreset
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.theme.FontShadowText
import com.galaxyrio.gracelauncher.ui.theme.LocalFontShadow
import com.galaxyrio.gracelauncher.ui.theme.LocalFontShadowEnabled
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherTypography
import com.galaxyrio.gracelauncher.ui.theme.LocalWallpaperFontColor
import com.galaxyrio.gracelauncher.ui.theme.resolvedFontColor
import kotlinx.coroutines.launch
import java.util.Locale

private val FontShadowSaver = Saver<FontShadow, String>(save = { it.encode() }, restore = { FontShadow.decode(it) })

/** The same draft editor saves either the global preference or an enclosing component draft. */
@Composable
internal fun FontShadowSettings(uiState: LauncherUiState, initial: FontShadow,
    onBack: () -> Unit, onSave: suspend (FontShadow) -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = FontShadowSaver) { mutableStateOf(initial.normalized()) }
    var baseline by rememberSaveable(stateSaver = FontShadowSaver) { mutableStateOf(initial.normalized()) }
    var colorOpen by rememberSaveable { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val enabled = !saving && LocalSettingsStorageState.current.canEdit
    val listState = rememberLazyListState()
    SettingsSearchScroll(listState)
    fun change(transform: (FontShadow) -> FontShadow) {
        if (enabled) draft = transform(draft).copy(preset = FontShadowPreset.Custom).normalized()
    }
    fun save() {
        if (!enabled) return
        saving = true
        val value = draft.normalized()
        scope.launch {
            try {
                if (onSave(value)) {
                    baseline = value
                    confirmExit = false
                    onBack()
                } else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }
    val back: () -> Unit = {
        if (!saving) when {
            colorOpen -> colorOpen = false
            draft != baseline -> confirmExit = true
            else -> onBack()
        }
    }
    BackHandler(onBack = back)
    SettingsPreviewScaffold(uiState, stringResource(R.string.clock_font_shadow), "settings_font_shadow", back,
        actions = {
            SettingsApplyAction(stringResource(R.string.save), onClick = ::save,
                modifier = Modifier.testTag("font_shadow_apply"), enabled = enabled)
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            CompositionLocalProvider(LocalFontShadow provides draft, LocalFontShadowEnabled provides true) {
                val style = LocalLauncherTypography.current.bodyLarge
                Row(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 24.dp)
                    .testTag("font_shadow_preview"), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    listOf(84, 48, 24).forEach { size ->
                        FontShadowText("G", fontSize = size.sp, color = resolvedFontColor(onWallpaper = true),
                            style = style, maxLines = 1)
                    }
                }
            }
            val heroSpec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
            val bounds = remember(heroSpec) { BoundsTransform { _, _ -> heroSpec } }
            val shape = ListItemDefaults.segmentedShapes(0, 1).shape
            SharedTransitionLayout(Modifier.weight(1f).fillMaxWidth().settingsPreviewScrim().padding(horizontal = 16.dp)) {
                AnimatedContent(colorOpen, modifier = Modifier.fillMaxSize(), label = "fontShadowColorHero",
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }) { showColor ->
                    val colorModifier = Modifier.sharedBounds(rememberSharedContentState("font_shadow_color"), this,
                        boundsTransform = bounds, clipInOverlayDuringTransition = OverlayClip(shape))
                    if (showColor) IconDesignerColorPicker(stringResource(R.string.icon_designer_color), draft.color,
                        MaterialTheme.colorScheme.primary.toArgb(), FontShadow.Soft.color,
                        onChange = { value -> if (colorOpen) change { it.copy(color = value ?: FontShadow.Soft.color) } },
                        onClose = { colorOpen = false }, modifier = colorModifier.padding(bottom = 16.dp),
                        fontColors = uiState.settings.fontColors)
                    else LazyColumn(Modifier.fillMaxSize().testTag("font_shadow_controls"), state = listState,
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp + LocalSettingsBottomInset.current)) {
                        item("presets") {
                            DesignerSegment(0, 7, "font_shadow_presets") {
                                DesignerConnectedChoices(FontShadowPreset.entries, draft.preset, enabled && !colorOpen, "font_shadow_preset",
                                    label = { it.label() }, topPadding = 0.dp, onSelect = { draft = draft.select(it) })
                            }
                        }
                        item("opacity") {
                            DesignerSegment(1, 7, "font_shadow_opacity_segment") {
                                DesignerSlider(stringResource(R.string.font_shadow_opacity), draft.opacity.toFloat(), 32f, 0f..100f,
                                    enabled, "font_shadow_opacity") { value -> change { it.copy(opacity = value.toInt()) } }
                            }
                        }
                        item("blur") {
                            ShadowDimensionSlider(R.string.font_shadow_blur, draft.blur, 4f, 0f..48f,
                                2, "font_shadow_blur", enabled) { value -> change { it.copy(blur = value) } }
                        }
                        item("offset_x") {
                            ShadowDimensionSlider(R.string.font_shadow_offset_x, draft.offsetX, 0f, -24f..24f,
                                3, "font_shadow_offset_x", enabled) { value -> change { it.copy(offsetX = value) } }
                        }
                        item("offset_y") {
                            ShadowDimensionSlider(R.string.font_shadow_offset_y, draft.offsetY, 0f, -24f..24f,
                                4, "font_shadow_offset_y", enabled) { value -> change { it.copy(offsetY = value) } }
                        }
                        item("spread") {
                            ShadowDimensionSlider(R.string.font_shadow_spread, draft.spread, 0f, 0f..16f,
                                5, "font_shadow_spread", enabled) { value -> change { it.copy(spread = value) } }
                        }
                        item("color") {
                            val scheme = MaterialTheme.colorScheme
                            val color = Color(draft.color.resolve(scheme.primary.toArgb(), colorScheme = scheme,
                                fontColors = uiState.settings.fontColors, wallpaperColor = LocalWallpaperFontColor.current.toArgb()))
                            SettingsActionItem(stringResource(R.string.icon_designer_color), fontColorSummary(draft.color),
                                6, 7, "font_shadow_color", enabled, modifier = colorModifier,
                                trailing = { Surface(Modifier.size(28.dp), CircleShape, color) {} }) { colorOpen = true }
                        }
                    }
                }
            }
        }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { if (!saving) confirmExit = false },
        title = { Text(stringResource(R.string.icon_designer_unsaved_title)) },
        text = { Text(stringResource(R.string.font_shadow_unsaved_message)) },
        confirmButton = { TextButton(onClick = ::save, enabled = enabled) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmExit = false }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { confirmExit = false; onBack() }, enabled = !saving) {
                    Text(stringResource(R.string.icon_designer_discard))
                }
            }
        }, modifier = Modifier.testTag("font_shadow_unsaved"))
}

@Composable
private fun ShadowDimensionSlider(label: Int, value: Float, default: Float, range: ClosedFloatingPointRange<Float>,
    index: Int, tag: String, enabled: Boolean, onChange: (Float) -> Unit) {
    DesignerSegment(index, 7, "${tag}_segment") {
        DesignerSlider(stringResource(label), value * 10f, default * 10f, range.start * 10f..range.endInclusive * 10f,
            enabled, tag, centered = range.start < 0f, valueText = String.format(Locale.getDefault(), "%.1f dp", value)) {
            onChange(it / 10f)
        }
    }
}

@Composable
internal fun FontShadowOverrideItem(value: FontShadowOverride, index: Int, count: Int, tag: String, enabled: Boolean,
    onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val title = stringResource(R.string.font_shadow_override)
    SettingsActionItem(title, null, index, count, tag, enabled, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerticalDivider(Modifier.height(32.dp))
            Switch(value.enabled, onToggle, Modifier.padding(start = 16.dp).testTag("${tag}_switch")
                .semantics { contentDescription = title }, enabled = enabled)
        }
    }, onClick = onOpen)
}

@Composable
private fun FontShadowPreset.label(): String = stringResource(when (this) {
    FontShadowPreset.None -> R.string.settings_none
    FontShadowPreset.Soft -> R.string.font_shadow_soft
    FontShadowPreset.Sharp -> R.string.font_shadow_sharp
    FontShadowPreset.Custom -> R.string.font_shadow_custom
})
