@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.ThemeMode
import com.galaxyrio.gracelauncher.data.icons.IconPackStatus
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.toDynamicScheme
import com.materialkolor.ktx.toneColor

@Composable
internal fun ThemeSettings(
    uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    onClockStyle: () -> Unit, onIconPacks: () -> Unit, onFonts: () -> Unit, onWallpaper: () -> Unit,
    onListAppearance: () -> Unit, onAlphabet: () -> Unit,
    onGraceButton: () -> Unit = {}, onAgenda: () -> Unit = {}, onSearch: () -> Unit = {}, onMediaPlayer: () -> Unit = {},
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val settings = uiState.settings
    when (dialog) {
        "palette" -> SettingsSelectionDialog(
            title = stringResource(R.string.settings_palette_style),
            items = PaletteStyle.entries, selectedItem = settings.paletteStyle,
            tag = "palette_style", itemKey = { it.name }, itemLabel = { it.label() },
            onSelect = { style -> actions.updateSettings { it.copy(paletteStyle = style) }; dialog = null },
            onDismiss = { dialog = null },
        )
        "theme" -> SettingsSelectionDialog(
            title = stringResource(R.string.settings_theme_mode),
            items = ThemeMode.entries, selectedItem = settings.darkMode,
            tag = "theme_mode", itemKey = { it.name }, itemLabel = { it.label() },
            onSelect = { mode -> actions.updateSettings { current -> current.copy(darkMode = mode) }; dialog = null },
            onDismiss = { dialog = null },
        )
    }
    SettingsScaffold(stringResource(R.string.settings_themes), "settings_themes", onBack) { padding ->
        SettingsList(padding) {
            item { SettingsHeading(stringResource(R.string.settings_theme_section)) }
            item {
                AccentColorItem(settings.useDynamicColors, settings.themeColor, settings.paletteStyle) { color ->
                    actions.updateSettings { current -> current.copy(useDynamicColors = false, themeColor = color.toArgb()) }
                }
            }
            item {
                SettingsToggleItem(
                    stringResource(R.string.settings_dynamic_colors), stringResource(R.string.settings_dynamic_colors_summary),
                    settings.useDynamicColors, 1, 5, "settings_dynamic_colors",
                ) { value -> actions.updateSettings { current -> current.copy(useDynamicColors = value) } }
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_palette_style), settings.paletteStyle.label(), 2, 5,
                    "settings_palette_style") { dialog = "palette" }
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_theme_mode), settings.darkMode.label(), 3, 5, "settings_theme_mode") { dialog = "theme" }
            }
            item {
                SettingsToggleItem(
                    stringResource(R.string.settings_amoled_mode), stringResource(R.string.settings_amoled_mode_summary),
                    settings.amoledMode, 4, 5, "settings_amoled_mode",
                ) { value -> actions.updateSettings { it.copy(amoledMode = value) } }
            }
            item { SettingsHeading(stringResource(R.string.settings_personalization)) }
            item {
                val packs = settings.enabledIconPackPackages
                val packNames = packs.map { pkg -> uiState.iconPacks.firstOrNull { it.packageName == pkg }?.label ?: pkg }
                val summary = when {
                    packs.isEmpty() -> stringResource(R.string.icon_pack_system)
                    uiState.iconPackStatus == IconPackStatus.Unavailable -> stringResource(R.string.icon_pack_unavailable)
                    else -> packNames.joinToString(" → ")
                }
                SettingsActionItem(stringResource(R.string.settings_icons), summary, 0, 3, "settings_icon_pack") {
                    onIconPacks()
                    actions.refreshIconPacks()
                }
            }
            item {
                val fonts = settings.fontLibrary.selected.map { appFontLabel(it) }
                val systemFont = appFontLabel("system")
                SettingsActionItem(stringResource(R.string.settings_font), fonts.joinToString(" → ").ifEmpty { systemFont },
                    1, 3, "settings_font", onClick = onFonts)
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_wallpaper), null, 2, 3,
                    "settings_wallpaper", onClick = onWallpaper)
            }
            item { SettingsHeading(stringResource(R.string.settings_components)) }
            item {
                SettingsActionItem(stringResource(R.string.settings_clock_style), uiState.settings.clockStyle.layout.label(),
                    0, 7, "settings_clock_style", onClick = onClockStyle)
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_agenda), null, 1, 7,
                    "settings_agenda_appearance", onClick = onAgenda)
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_media_player), null, 2, 7,
                    "settings_media_appearance", onClick = onMediaPlayer)
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_search), null, 3, 7,
                    "settings_search_appearance", onClick = onSearch)
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_grace_button), null, 4, 7,
                    "settings_grace_button_appearance", onClick = onGraceButton)
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_alphabet), null, 5, 7, "settings_alphabet", onClick = onAlphabet)
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_favorites_app_list), null, 6, 7, "settings_list_appearance", onClick = onListAppearance)
            }
            item { SettingsHeading(stringResource(R.string.settings_misc)) }
            item {
                SettingsToggleItem(
                    stringResource(R.string.settings_hide_status_bar), stringResource(R.string.settings_hide_status_bar_summary),
                    settings.hideStatusBar, 0, 1, "settings_hide_status_bar",
                ) { value -> actions.updateSettings { it.copy(hideStatusBar = value) } }
            }
        }
    }
}

@Composable
internal fun SettingsPlaceholder(title: String, tag: String, onBack: () -> Unit) {
    SettingsScaffold(title, tag, onBack) { padding ->
        SettingsList(padding) {
            item {
                Text(stringResource(R.string.settings_coming_soon), Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AccentColorItem(isDynamic: Boolean, selectedColor: Int, style: PaletteStyle, onSelect: (Color) -> Unit) {
    val pages = remember { AccentColors.chunked(5) }
    val initialPage = (AccentColors.indexOfFirst { it.toArgb() == selectedColor }.coerceAtLeast(0) / 5)
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(0, 5),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.testTag("settings_accent_color"),
        content = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_accent_color))
                Text(
                    stringResource(R.string.settings_dynamic_or_custom),
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = 18.dp)) { page ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        pages[page].forEachIndexed { index, color ->
                            PaletteOption(color, style, !isDynamic && selectedColor == color.toArgb(), page * 5 + index) { onSelect(color) }
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(pages.size) { page ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(
                            if (pager.settledPage == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ))
                    }
                }
            }
        },
    )
}

@Composable
private fun PaletteOption(seed: Color, style: PaletteStyle, selected: Boolean, index: Int, onClick: () -> Unit) {
    val palette = remember(seed, style) {
        seed.toDynamicScheme(isDark = false, style = style, specVersion = ColorSpec.SpecVersion.SPEC_2025)
    }
    val description = stringResource(R.string.settings_color_option, "#%06X".format(seed.toArgb() and 0xFFFFFF))
    Box(
        Modifier.size(48.dp).testTag("settings_palette_$index")
            .semantics { contentDescription = description; this.selected = selected }
            .clip(CircleShape).clickable(enabled = LocalSettingsStorageState.current.canEdit, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(palette.primaryPalette.toneColor(60)))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(palette.secondaryPalette.toneColor(80)))
                Box(Modifier.weight(1f).fillMaxHeight().background(palette.tertiaryPalette.toneColor(40)))
            }
        }
        if (selected) Surface(
            modifier = Modifier.size(24.dp), shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) { LauncherIcon(LauncherSymbol.Check, Modifier.padding(4.dp)) }
    }
}

@Composable
internal fun <T> SettingsSelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T?,
    tag: String,
    itemKey: (T) -> String,
    itemLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {},
    itemLeading: (@Composable (T) -> Unit)? = null,
    leadingButton: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Column(Modifier.selectableGroup()) {
                    items.forEach { item ->
                        val enabled = LocalSettingsStorageState.current.canEdit
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .testTag("$tag:${itemKey(item)}")
                                .selectable(
                                    selected = item == selectedItem, enabled = enabled,
                                    role = Role.RadioButton, onClick = { onSelect(item) },
                                )
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RadioButton(selected = item == selectedItem, onClick = null, enabled = enabled)
                            itemLeading?.invoke(item)
                            Text(itemLabel(item), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                footer()
            }
        },
        confirmButton = {
            if (leadingButton != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                leadingButton()
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
            }
        },
        dismissButton = if (leadingButton == null) ({
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }) else null,
    )
}

@Composable
private fun PaletteStyle.label(): String = stringResource(when (this) {
    PaletteStyle.TonalSpot -> R.string.palette_tonal_spot
    PaletteStyle.Neutral -> R.string.palette_neutral
    PaletteStyle.Vibrant -> R.string.palette_vibrant
    PaletteStyle.Expressive -> R.string.palette_expressive
    PaletteStyle.Rainbow -> R.string.palette_rainbow
    PaletteStyle.FruitSalad -> R.string.palette_fruit_salad
    PaletteStyle.Monochrome -> R.string.palette_monochrome
    PaletteStyle.Fidelity -> R.string.palette_fidelity
    PaletteStyle.Content -> R.string.palette_content
})

@Composable
private fun ThemeMode.label(): String = stringResource(when (this) {
    ThemeMode.System -> R.string.settings_follow_system
    ThemeMode.Light -> R.string.settings_light_mode
    ThemeMode.Dark -> R.string.settings_dark_mode
})

internal val AccentColors = listOf(
    Color(0xFF6750A4), Color(0xFFB3261E), Color(0xFFE27C33), Color(0xFF7D5260), Color(0xFF3F51B5),
    Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFF9A825), Color(0xFF0288D1), Color(0xFFC2185B),
)
