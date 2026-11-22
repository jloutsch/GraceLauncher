package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.settings.SettingsSearchIndex
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fork: settings search (#36) must not silently miss a setting added by a later edit or upstream release. */
class SettingsSearchIndexTest {
    // Rows that are deliberately not searchable: editors that need an item chosen first (folder, category,
    // icon designer), error and permission states, placeholders, and the one-off home shortcut.
    private val notSearchable = setOf(
        "app_category_condition",
        "category_dynamic_rule",
        "category_icon",
        "category_inclusion",
        "category_placement",
        "grace_button_align_center",
        "icon_designer_add_tray",
        "icon_designer_apply_to_shortcuts",
        "icon_designer_brightness",
        "icon_designer_hue",
        "icon_designer_invert_background",
        "icon_designer_rotation",
        "icon_designer_saturation",
        "icon_designer_size",
        "icon_designer_source",
        "icon_designer_square_aspect_ratio",
        "icon_designer_switch_app",
        "icon_designer_theme_icon",
        "icon_designer_theme_unsupported",
        "icon_designer_x",
        "icon_designer_y",
        "icon_dot_size",
        "icon_edit_follow_theme",
        "icon_edit_image",
        "icon_pack_system",
        "private_space_unlock_edit",
        "retry",
        "set_default_launcher",
        "settings_feed_screen",
        "settings_folder_auto_hide",
        "settings_open_home",
        "settings_status_screen",
        "settings_tablets_foldables",
        "weather_settings_allow",
        "weather_settings_install",
        "widget_remove",
        "widget_stack_height",
    )

    @Test fun everySettingRowIsSearchableOrDeliberatelyLeftOut() {
        val row = Regex("""(?:SettingsToggleItem|SettingsActionItem|DesignerSlider|SettingsFeatureBanner)\(\s*stringResource\(R\.string\.(\w+)\)""")
        val rows = File("src/main/java/com/galaxyrio/gracelauncher/ui/settings").listFiles { file -> file.extension == "kt" }!!
            .flatMap { file -> row.findAll(file.readText()).map { it.groupValues[1] }.toList() }.toSet()
        val strings = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val indexed = (SettingsSearchIndex.items.map { it.second } + SettingsSearchIndex.pages.values.map { it.first })
            .map(strings::getValue).toSet()
        assertEquals("Add these to SettingsSearchIndex, or to notSearchable here", emptySet<String>(), rows - indexed - notSearchable)
        assertEquals("No longer settings rows; remove from notSearchable", emptySet<String>(), notSearchable - rows)
    }
}
