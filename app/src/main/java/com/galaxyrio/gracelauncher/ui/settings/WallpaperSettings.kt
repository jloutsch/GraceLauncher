@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.rememberWallpaperBlurAvailable
import com.galaxyrio.gracelauncher.ui.theme.wallpaperTint
import kotlin.math.roundToInt

@Composable
internal fun WallpaperSettings(
    uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    onDesigner: () -> Unit, onChooseWallpaper: () -> Unit,
) {
    val settings = uiState.settings
    val defaults = remember { LauncherSettings() }
    var colorOpen by rememberSaveable { mutableStateOf(false) }
    var dimColor by remember(settings.listAppearance.wallpaperDimColor) { mutableStateOf(settings.listAppearance.wallpaperDimColor) }
    val controlsState = rememberLazyListState()
    val enabled = LocalSettingsStorageState.current.canEdit
    val blurAvailable = rememberWallpaperBlurAvailable()
    val wallpaper = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons)
    val primary = MaterialTheme.colorScheme.primary
    val automaticDimTint = remember(primary, wallpaper.darkText) { wallpaper.wallpaperTint(primary) }
    val heroSpec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    val heroBounds = remember(heroSpec) { BoundsTransform { _, _ -> heroSpec } }
    val heroShape = ListItemDefaults.segmentedShapes(0, 1).shape
    fun closeColor() {
        if (enabled) {
            val color = dimColor
            actions.updateSettings { it.copy(listAppearance = it.listAppearance.copy(wallpaperDimColor = color)) }
        }
        colorOpen = false
    }
    BackHandler(colorOpen) { closeColor() }
    SettingsScaffold(stringResource(R.string.settings_wallpaper), "wallpaper_settings",
        { if (colorOpen) closeColor() else onBack() }) { padding ->
        SharedTransitionLayout(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .padding(horizontal = 16.dp).clipToBounds()) {
            AnimatedContent(colorOpen, modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "wallpaperColorHero") { editingColor ->
                val colorModifier = Modifier.sharedBounds(rememberSharedContentState("wallpaper_dim_color"), this,
                    boundsTransform = heroBounds, clipInOverlayDuringTransition = OverlayClip(heroShape))
                if (editingColor) {
                    IconDesignerColorPicker(stringResource(R.string.settings_wallpaper_dim_color), dimColor, primary.toArgb(), null,
                        onChange = { if (enabled && colorOpen) dimColor = it },
                        onClose = { if (colorOpen) closeColor() },
                        modifier = Modifier.padding(bottom = 16.dp).then(colorModifier), fallback = automaticDimTint.toArgb())
                } else LazyColumn(Modifier.fillMaxSize().testTag("wallpaper_controls"), state = controlsState,
                    contentPadding = PaddingValues(bottom = 24.dp + LocalSettingsBottomInset.current),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    item("designer") {
                        SettingsActionItem(stringResource(R.string.settings_wallpaper_designer), null, 0, 4,
                            "settings_wallpaper_designer", onClick = onDesigner)
                    }
                    item("choose") {
                        SettingsActionItem(stringResource(R.string.settings_choose_wallpaper), null, 1, 4,
                            "settings_choose_wallpaper", onClick = onChooseWallpaper)
                    }
                    item("dim") {
                        WallpaperEffectItem(stringResource(R.string.settings_dim_wallpaper), stringResource(R.string.settings_dim_wallpaper_summary),
                            settings.dimWallpaper, settings.wallpaperDimAmount, 0..100,
                            stringResource(R.string.settings_wallpaper_opacity), 2, "settings_dim_wallpaper",
                            defaultAmount = defaults.wallpaperDimAmount, count = 4,
                            onToggle = { value -> actions.updateSettings { it.copy(dimWallpaper = value) } },
                            onAmount = { value -> actions.updateSettings { it.copy(wallpaperDimAmount = value) } }) {
                            SettingsActionItem(stringResource(R.string.settings_wallpaper_dim_color),
                                if (dimColor == null) stringResource(R.string.settings_wallpaper_dim_color_auto) else null,
                                0, 1, "settings_dim_wallpaper_color", modifier = colorModifier, trailing = {
                                    Surface(Modifier.size(24.dp), shape = MaterialTheme.shapes.small,
                                        color = dimColor?.resolve(primary.toArgb(), colorScheme = MaterialTheme.colorScheme)
                                            ?.let(::Color) ?: automaticDimTint) {}
                                }, onClick = { colorOpen = true })
                        }
                    }
                    item("blur") {
                        WallpaperEffectItem(stringResource(R.string.settings_blur_wallpaper),
                            stringResource(if (blurAvailable) R.string.settings_blur_wallpaper_summary else R.string.settings_blur_unavailable),
                            settings.blurWallpaper, settings.wallpaperBlurRadius, 0..48,
                            stringResource(R.string.settings_wallpaper_blur_amount), 3, "settings_blur_wallpaper", amountEnabled = blurAvailable,
                            defaultAmount = defaults.wallpaperBlurRadius, count = 4, suffix = "dp",
                            onToggle = { value -> actions.updateSettings { it.copy(blurWallpaper = value) } },
                            onAmount = { value -> actions.updateSettings { it.copy(wallpaperBlurRadius = value) } })
                    }
                }
            }
        }
    }
}

/** Keep the toggle and its expanding controls in one unchanged segmented outline. */
@Composable
internal fun WallpaperEffectItem(
    title: String, summary: String, checked: Boolean, amount: Int, range: IntRange,
    sliderLabel: String, index: Int, tag: String, defaultAmount: Int,
    count: Int = 3, amountEnabled: Boolean = true, suffix: String = "%",
    onToggle: (Boolean) -> Unit, onAmount: (Int) -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    val enabled = LocalSettingsStorageState.current.canEdit
    var draft by remember(amount) { mutableFloatStateOf(amount.coerceIn(range).toFloat()) }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        content = {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().testTag(tag)
                        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title)
                        Text(summary, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = checked, onCheckedChange = null, enabled = enabled,
                        modifier = Modifier.testTag("${tag}_switch"))
                }
                AnimatedVisibility(visible = checked) {
                    Column {
                        DesignerSlider(
                            label = sliderLabel, value = draft, default = defaultAmount.coerceIn(range).toFloat(),
                            // Do not enqueue a Room transaction for every drag frame.
                            onFinished = { onAmount(it.roundToInt().coerceIn(range)) },
                            range = range.first.toFloat()..range.last.toFloat(),
                            enabled = enabled && amountEnabled, tag = "${tag}_amount", suffix = suffix,
                            onChange = { draft = it },
                        )
                        extraContent()
                    }
                }
            }
        },
    )
}
