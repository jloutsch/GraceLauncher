package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.settings.SettingsSearchIndex
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fork: settings search (#36) must not silently miss a setting added by a later edit or upstream release. */
class SettingsSearchIndexTest {
    // Rows that are deliberately not searchable: editors that need an item chosen first (folder, category,
    // icon designer), rows shown only in some states (errors, missing permissions or apps, first-time setup),
    // placeholders, and the one-off home shortcut.
    private val notSearchable = setOf(
        "app_category_condition",
        "app_category_end",
        "app_category_start",
        "calendar_refreshing",
        "category_dynamic_rule",
        "category_icon",
        "category_inclusion",
        "category_placement",
        "connect_calendar",
        "grace_button_align_center",
        "icon_designer_add_tray",
        "icon_designer_apply_to_shortcuts",
        "icon_designer_brightness",
        "icon_designer_hue",
        "icon_designer_invert_background",
        "icon_designer_pebble_roundness",
        "icon_designer_rotation",
        "icon_designer_saturation",
        "icon_designer_size",
        "icon_designer_source",
        "icon_designer_square_aspect_ratio",
        "icon_designer_square_corner_radius",
        "icon_designer_switch_app",
        "icon_designer_theme_icon",
        "icon_designer_theme_unsupported",
        "icon_designer_x",
        "icon_designer_y",
        "icon_dot_size",
        "icon_edit_follow_theme",
        "icon_edit_image",
        "icon_pack_system",
        "private_space_setup",
        "private_space_unlock_edit",
        "retry",
        "set_default_launcher",
        "settings_feed_screen",
        "settings_folder_auto_hide",
        "settings_open_home",
        "settings_status_screen",
        "settings_tablets_foldables",
        "tasks_app_permissions",
        "tasks_connect",
        "tasks_get_app",
        "tasks_update_app",
        "usage_access",
        "weather_settings_allow",
        "weather_settings_install",
        "weather_settings_location",
        "weather_settings_open",
        "weather_settings_refresh",
        "widget_remove",
        "widget_stack_height",
        "work_profile_setup",
    )

    @Test fun everySettingRowIsSearchableOrDeliberatelyLeftOut() {
        // Every row component the settings screens use, with a fixed or an if/else title.
        val components = "SettingsToggleItem|SettingsActionItem|DesignerSlider|SettingsFeatureBanner|LabSliderItem|NotificationDisplayItem|WallpaperEffectItem|UsageOption|AgendaNavigationToggle|AccentColorItem"
        val direct = Regex("""(?:$components)\(\s*stringResource\(R\.string\.(\w+)\)""")
        val conditional = Regex("""(?:$components)\(\s*stringResource\(\s*if\s*\([^()]*(?:\([^()]*\)[^()]*)*\)\s*R\.string\.(\w+)\s*else\s*R\.string\.(\w+)\)""")
        val rows = File("src/main/java/com/galaxyrio/gracelauncher/ui/settings").listFiles { file -> file.extension == "kt" }!!
            .flatMap { file ->
                val text = file.readText()
                direct.findAll(text).map { it.groupValues[1] }.toList() +
                    conditional.findAll(text).flatMap { listOf(it.groupValues[1], it.groupValues[2]) }.toList()
            }.toSet()
        val strings = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val indexed = (SettingsSearchIndex.items.map { it.title } + SettingsSearchIndex.pages.values.map { it.first })
            .map(strings::getValue).toSet()
        assertEquals("Add these to SettingsSearchIndex, or to notSearchable here", emptySet<String>(), rows - indexed - notSearchable)
        assertEquals("No longer settings rows; remove from notSearchable", emptySet<String>(), notSearchable - rows)
    }

    private val settingsFiles get() = File("src/main/java/com/galaxyrio/gracelauncher/ui/settings").listFiles { file -> file.extension == "kt" }!!

    @Test fun everyRowComponentCanBeHighlightedBySearch() {
        // A result scrolls to its row and flashes it; a component without the hook opens the page but shows nothing.
        val components = listOf("SettingsToggleItem", "SettingsActionItem", "DesignerSlider", "SettingsFeatureBanner", "LabSliderItem", "NotificationDisplayItem", "WallpaperEffectItem", "UsageOption", "AgendaNavigationToggle", "AccentColorItem")
        val text = settingsFiles.joinToString("\n") { it.readText() }
        val missing = components.filterNot { name ->
            val body = text.substringAfter("fun $name(").substringBefore("\n}\n")
            "rememberSearchHighlight(" in body || "SettingsActionItem(" in body
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test fun everyLazyListHoldingSettingRowsScrollsToTheSearchTarget() {
        // A lazy list composes only the rows on screen, so a row further down needs SettingsSearchScroll to be found.
        val rowCall = Regex("""(?:SettingsToggleItem|SettingsActionItem|DesignerSlider|SettingsFeatureBanner|LabSliderItem|NotificationDisplayItem|WallpaperEffectItem|UsageOption|AgendaNavigationToggle)\(""")
        val missing = settingsFiles.filter { file ->
            val text = file.readText()
            "LazyColumn(" in text && rowCall.containsMatchIn(text) && "SettingsSearchScroll(" !in text
        }.map { it.name }
        assertEquals(emptyList<String>(), missing)
    }

    @Test fun rowComponentsWithATitleParameterAreAllScanned() {
        // A new row component would hide its rows from the scan above; list it there when this fails.
        val scanned = setOf("SettingsToggleItem", "SettingsActionItem", "DesignerSlider", "SettingsFeatureBanner", "LabSliderItem", "NotificationDisplayItem", "WallpaperEffectItem", "UsageOption", "AgendaNavigationToggle", "AccentColorItem")
        val declared = Regex("""fun (\w+Item|\w+Option|\w+Toggle|DesignerSlider|SettingsFeatureBanner)\(\s*title: String""")
        val found = File("src/main/java/com/galaxyrio/gracelauncher/ui/settings").listFiles { file -> file.extension == "kt" }!!
            .flatMap { file -> declared.findAll(file.readText()).map { it.groupValues[1] }.toList() }.toSet()
        assertEquals(emptySet<String>(), found - scanned)
    }
}
