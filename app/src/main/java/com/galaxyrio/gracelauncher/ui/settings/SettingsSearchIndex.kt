package com.galaxyrio.gracelauncher.ui.settings

import androidx.annotation.StringRes
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.SearchNames
import com.galaxyrio.gracelauncher.data.SearchQuery

/**
 * Fork: what Grace settings search (#36) can find. Drafted from the settings screens, then reviewed;
 * SettingsSearchIndexTest fails when a screen gains a setting row that is not listed here.
 */
internal object SettingsSearchIndex {
    /** Each searchable page, its label (the row that opens it), and the page that opens it. */
    val pages: Map<SettingsPage, Pair<Int, SettingsPage>> = mapOf(
        SettingsPage.Productivity to (R.string.settings_productivity to SettingsPage.Root),
        SettingsPage.AppCategories to (R.string.app_categories_title to SettingsPage.Productivity),
        SettingsPage.DigitalWellbeing to (R.string.digital_wellbeing to SettingsPage.Productivity),
        SettingsPage.Homepage to (R.string.settings_home_screen to SettingsPage.Productivity),
        SettingsPage.AppList to (R.string.settings_folder_app_list to SettingsPage.Productivity),
        SettingsPage.Clock to (R.string.settings_clock to SettingsPage.Productivity),
        SettingsPage.Search to (R.string.settings_search to SettingsPage.Productivity),
        SettingsPage.ShortcutsFolders to (R.string.popups_title to SettingsPage.Productivity),
        SettingsPage.WidgetScreen to (R.string.widget_screen_title to SettingsPage.Productivity),
        SettingsPage.MediaPlayer to (R.string.settings_media_player to SettingsPage.Productivity),
        SettingsPage.Weather to (R.string.settings_weather to SettingsPage.Productivity),
        SettingsPage.WeatherApp to (R.string.weather_app to SettingsPage.Weather),
        SettingsPage.Themes to (R.string.settings_themes to SettingsPage.Root),
        SettingsPage.GraceButtonAppearance to (R.string.settings_grace_button to SettingsPage.Themes),
        SettingsPage.GraceButtonPosition to (R.string.grace_button_position_size to SettingsPage.GraceButtonAppearance),
        SettingsPage.AgendaAppearance to (R.string.settings_agenda to SettingsPage.Themes),
        SettingsPage.MediaPlayerAppearance to (R.string.settings_media_player to SettingsPage.Themes),
        SettingsPage.ClockStyle to (R.string.settings_clock_style to SettingsPage.Themes),
        SettingsPage.Fonts to (R.string.settings_font to SettingsPage.Themes),
        SettingsPage.FontColors to (R.string.list_font_color to SettingsPage.Fonts),
        SettingsPage.Wallpaper to (R.string.settings_wallpaper to SettingsPage.Themes),
        SettingsPage.ListAppearance to (R.string.settings_favorites_app_list to SettingsPage.Themes),
        SettingsPage.Alphabet to (R.string.settings_alphabet to SettingsPage.Themes),
        SettingsPage.IconPacks to (R.string.settings_icons to SettingsPage.Themes),
        SettingsPage.Calendar to (R.string.settings_calendar_agenda to SettingsPage.Productivity),
        SettingsPage.CalendarSources to (R.string.calendar_sources to SettingsPage.Calendar),
        SettingsPage.CalendarApp to (R.string.calendar_app to SettingsPage.Calendar),
        SettingsPage.TasksOrg to (R.string.tasks_org to SettingsPage.Calendar),
        SettingsPage.TaskLists to (R.string.tasks_choose_lists to SettingsPage.TasksOrg),
        SettingsPage.Advanced to (R.string.settings_advanced to SettingsPage.Root),
        SettingsPage.Language to (R.string.settings_language to SettingsPage.Root),
        SettingsPage.LabGraceButton to (R.string.settings_grace_button to SettingsPage.Advanced),
        SettingsPage.LabGesture to (R.string.settings_gestures to SettingsPage.Advanced),
        SettingsPage.LabPopup to (R.string.lab_popup to SettingsPage.Advanced),
        SettingsPage.LabAccessibility to (R.string.lab_accessibility to SettingsPage.Advanced),
        SettingsPage.LabDismiss to (R.string.lab_swipe_dismiss to SettingsPage.Advanced),
        SettingsPage.About to (R.string.settings_about to SettingsPage.Root),
        SettingsPage.Backup to (R.string.settings_backup to SettingsPage.Root),
        SettingsPage.HiddenApps to (R.string.settings_hide_apps to SettingsPage.Productivity),
        SettingsPage.PrivateSpace to (R.string.private_space_title to SettingsPage.Productivity),
        SettingsPage.WorkProfile to (R.string.work_profile_title to SettingsPage.Productivity),
        SettingsPage.Folders to (R.string.settings_folders to SettingsPage.Productivity),
        SettingsPage.AppStacks to (R.string.app_stacks_title to SettingsPage.Productivity),
        SettingsPage.Changelog to (R.string.settings_changelog to SettingsPage.About),
        SettingsPage.Licenses to (R.string.settings_licenses to SettingsPage.About),
        SettingsPage.GraceButton to (R.string.settings_grace_button to SettingsPage.Productivity),
        SettingsPage.Gestures to (R.string.settings_gestures to SettingsPage.Productivity),
        SettingsPage.IconDesigner to (R.string.icon_designer_title to SettingsPage.IconPacks),
    )

    /**
     * Setting rows by the page that shows them. Rows that only open another page are found as that page. Rows that
     * appear only in some states (a missing permission, an app not installed yet) are left out.
     */
    val items: List<SettingsSearchItem> = listOf(
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.agenda_hourly_weather),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.agenda_daily_content),
        SettingsSearchItem(SettingsPage.DigitalWellbeing, R.string.usage_show_favorites),
        SettingsSearchItem(SettingsPage.DigitalWellbeing, R.string.usage_sort),
        SettingsSearchItem(SettingsPage.LabGesture, R.string.lab_swipe_sensitivity),
        SettingsSearchItem(SettingsPage.LabGesture, R.string.lab_double_tap_interval),
        SettingsSearchItem(SettingsPage.LabPopup, R.string.lab_popup_angle),
        SettingsSearchItem(SettingsPage.LabPopup, R.string.lab_popup_distance),
        SettingsSearchItem(SettingsPage.LabDismiss, R.string.lab_dismiss_threshold),
        SettingsSearchItem(SettingsPage.LabDismiss, R.string.lab_dismiss_resistance),
        SettingsSearchItem(SettingsPage.PrivateSpace, R.string.private_space_edit),
        SettingsSearchItem(SettingsPage.PrivateSpace, R.string.private_space_indicator),
        SettingsSearchItem(SettingsPage.WorkProfile, R.string.work_profile_edit),
        SettingsSearchItem(SettingsPage.WorkProfile, R.string.work_profile_indicator),
        SettingsSearchItem(SettingsPage.ShortcutsFolders, R.string.popup_normal_notifications),
        SettingsSearchItem(SettingsPage.ShortcutsFolders, R.string.popup_silent_notifications),
        SettingsSearchItem(SettingsPage.Wallpaper, R.string.settings_dim_wallpaper),
        SettingsSearchItem(SettingsPage.Wallpaper, R.string.settings_blur_wallpaper),
        SettingsSearchItem(SettingsPage.Calendar, R.string.calendar_refresh_now),
        SettingsSearchItem(SettingsPage.GraceButton, R.string.grace_button_enable),
        SettingsSearchItem(SettingsPage.Gestures, R.string.home_gestures_enable),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.weather_hour_show_chart, R.string.agenda_hourly_weather),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.weather_hour_show_text, R.string.agenda_hourly_weather),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.weather_hour_always_show, R.string.agenda_hourly_weather),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.weather_chart_wavy, R.string.agenda_hourly_weather),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.weather_chart_always_expanded, R.string.agenda_hourly_weather),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.agenda_tasks_first, R.string.agenda_daily_content),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_show_seconds),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_show_leading_zero),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_show_colon),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_separate_digits),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_fixed_width),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_equal_width_characters),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_center_characters),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_use_latin_letters),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_rotate_primary),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_rotate_all),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_rotate_above_primary),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_weather_icons),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_weather_contents),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_agenda_contents),
        SettingsSearchItem(SettingsPage.ClockStyle, R.string.clock_task_contents),
        SettingsSearchItem(SettingsPage.MediaPlayerAppearance, R.string.media_show_progress),
        SettingsSearchItem(SettingsPage.MediaPlayerAppearance, R.string.media_show_playback_controls),
        SettingsSearchItem(SettingsPage.MediaPlayerAppearance, R.string.media_cover_border),
        SettingsSearchItem(SettingsPage.MediaPlayerAppearance, R.string.media_cover_shadow),
        SettingsSearchItem(SettingsPage.MediaPlayerAppearance, R.string.media_border_color),
        SettingsSearchItem(SettingsPage.MediaPlayerAppearance, R.string.media_border_width),
        SettingsSearchItem(SettingsPage.MediaPlayerAppearance, R.string.media_border_opacity),
        SettingsSearchItem(SettingsPage.Productivity, R.string.settings_allow_haptics),
        SettingsSearchItem(SettingsPage.AppCategories, R.string.app_category_create),
        SettingsSearchItem(SettingsPage.DigitalWellbeing, R.string.usage_enable),
        SettingsSearchItem(SettingsPage.Homepage, R.string.favorites_lock_editing),
        SettingsSearchItem(SettingsPage.Homepage, R.string.homepage_adjust_height),
        SettingsSearchItem(SettingsPage.AppList, R.string.recently_installed),
        SettingsSearchItem(SettingsPage.AppList, R.string.app_list_grace_settings),
        SettingsSearchItem(SettingsPage.Clock, R.string.clock_enable),
        SettingsSearchItem(SettingsPage.Search, R.string.search_enable),
        SettingsSearchItem(SettingsPage.Search, R.string.search_suggestions),
        SettingsSearchItem(SettingsPage.Search, R.string.search_contacts),
        SettingsSearchItem(SettingsPage.Search, R.string.search_files),
        SettingsSearchItem(SettingsPage.Search, R.string.search_fuzzy),
        SettingsSearchItem(SettingsPage.Search, R.string.search_hidden_apps),
        SettingsSearchItem(SettingsPage.ShortcutsFolders, R.string.shortcuts_folders_enable),
        SettingsSearchItem(SettingsPage.ShortcutsFolders, R.string.notification_allow),
        SettingsSearchItem(SettingsPage.ShortcutsFolders, R.string.popup_swipe_left_first),
        SettingsSearchItem(SettingsPage.WidgetScreen, R.string.widget_screen_enable),
        SettingsSearchItem(SettingsPage.WidgetScreen, R.string.widget_screen_swipe),
        SettingsSearchItem(SettingsPage.WidgetScreen, R.string.favorites_lock_editing),
        SettingsSearchItem(SettingsPage.WidgetScreen, R.string.widget_screen_columns),
        SettingsSearchItem(SettingsPage.WidgetScreen, R.string.widget_screen_edit),
        SettingsSearchItem(SettingsPage.MediaPlayer, R.string.media_enable),
        SettingsSearchItem(SettingsPage.MediaPlayer, R.string.media_keep_while_playing),
        SettingsSearchItem(SettingsPage.MediaPlayer, R.string.media_always_visible),
        SettingsSearchItem(SettingsPage.MediaPlayer, R.string.media_allow_controls),
        SettingsSearchItem(SettingsPage.Weather, R.string.weather_settings_enable),
        SettingsSearchItem(SettingsPage.Weather, R.string.weather_settings_forecast_days),
        SettingsSearchItem(SettingsPage.Weather, R.string.weather_temperature_unit),
        SettingsSearchItem(SettingsPage.Themes, R.string.settings_dynamic_colors),
        SettingsSearchItem(SettingsPage.Themes, R.string.settings_palette_style),
        SettingsSearchItem(SettingsPage.Themes, R.string.settings_theme_mode),
        SettingsSearchItem(SettingsPage.Themes, R.string.settings_amoled_mode),
        SettingsSearchItem(SettingsPage.Themes, R.string.settings_hide_status_bar),
        SettingsSearchItem(SettingsPage.GraceButtonPosition, R.string.grace_button_side_distance),
        SettingsSearchItem(SettingsPage.GraceButtonPosition, R.string.grace_button_bottom_distance),
        SettingsSearchItem(SettingsPage.GraceButtonPosition, R.string.grace_button_size),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.settings_add_widget),
        SettingsSearchItem(SettingsPage.AgendaAppearance, R.string.agenda_undated_tasks),
        SettingsSearchItem(SettingsPage.Fonts, R.string.font_apply_settings),
        SettingsSearchItem(SettingsPage.Fonts, R.string.font_add),
        SettingsSearchItem(SettingsPage.FontColors, R.string.font_color_add),
        SettingsSearchItem(SettingsPage.Wallpaper, R.string.settings_wallpaper_designer),
        SettingsSearchItem(SettingsPage.Wallpaper, R.string.settings_choose_wallpaper),
        SettingsSearchItem(SettingsPage.Wallpaper, R.string.settings_wallpaper_dim_color),
        SettingsSearchItem(SettingsPage.ListAppearance, R.string.popup_single_line_previews),
        SettingsSearchItem(SettingsPage.ListAppearance, R.string.popup_multiple_app_previews),
        SettingsSearchItem(SettingsPage.ListAppearance, R.string.icon_designer_icon_size),
        SettingsSearchItem(SettingsPage.ListAppearance, R.string.list_names_in_folders),
        SettingsSearchItem(SettingsPage.Alphabet, R.string.alphabet_letter_color),
        SettingsSearchItem(SettingsPage.Alphabet, R.string.clock_size),
        SettingsSearchItem(SettingsPage.Alphabet, R.string.alphabet_top),
        SettingsSearchItem(SettingsPage.Alphabet, R.string.alphabet_bottom),
        SettingsSearchItem(SettingsPage.Alphabet, R.string.alphabet_free_movement),
        SettingsSearchItem(SettingsPage.Alphabet, R.string.alphabet_touch_width),
        SettingsSearchItem(SettingsPage.Alphabet, R.string.list_left_alphabet),
        SettingsSearchItem(SettingsPage.Alphabet, R.string.alphabet_numbers_first),
        SettingsSearchItem(SettingsPage.Calendar, R.string.calendar_enable),
        SettingsSearchItem(SettingsPage.Calendar, R.string.calendar_read_days),
        SettingsSearchItem(SettingsPage.Calendar, R.string.calendar_refresh_frequency),
        SettingsSearchItem(SettingsPage.Calendar, R.string.calendar_deprioritize_all_day),
        SettingsSearchItem(SettingsPage.TasksOrg, R.string.tasks_enable),
        SettingsSearchItem(SettingsPage.TasksOrg, R.string.tasks_open_app),
        SettingsSearchItem(SettingsPage.TasksOrg, R.string.calendar_refresh_now),
        SettingsSearchItem(SettingsPage.TasksOrg, R.string.tasks_sort_by),
        SettingsSearchItem(SettingsPage.TasksOrg, R.string.tasks_undated_first),
        SettingsSearchItem(SettingsPage.TaskLists, R.string.tasks_all_lists),
        SettingsSearchItem(SettingsPage.Advanced, R.string.settings_allow_screen_rotation),
        SettingsSearchItem(SettingsPage.Advanced, R.string.choose_default_launcher),
        SettingsSearchItem(SettingsPage.LabGraceButton, R.string.lab_animation),
        SettingsSearchItem(SettingsPage.LabAccessibility, R.string.lab_prefer_non_accessibility),
        SettingsSearchItem(SettingsPage.Backup, R.string.backup_export),
        SettingsSearchItem(SettingsPage.Backup, R.string.backup_import),
        SettingsSearchItem(SettingsPage.Backup, R.string.backup_auto),
        SettingsSearchItem(SettingsPage.Backup, R.string.backup_folder),
        SettingsSearchItem(SettingsPage.PrivateSpace, R.string.private_space_display),
        SettingsSearchItem(SettingsPage.PrivateSpace, R.string.private_space_password),
        SettingsSearchItem(SettingsPage.PrivateSpace, R.string.private_space_lock_immediately),
        SettingsSearchItem(SettingsPage.WorkProfile, R.string.private_space_display),
        SettingsSearchItem(SettingsPage.AppStacks, R.string.widget_stack_create),
    )
}

/** A searchable row; [section] names the tab or sub-page inside [page] that holds it, when there is one. */
internal data class SettingsSearchItem(val page: SettingsPage, @StringRes val title: Int, @StringRes val section: Int? = null)

/**
 * [pages] is the route from the settings home to [page], so back returns through the [path] shown.
 * [row] is the setting row's title to scroll to and highlight; null when the result is a page.
 */
internal data class SettingsSearchResult(val page: SettingsPage, val title: String, val path: List<String>, val pages: List<SettingsPage>,
    val row: String? = null)

/** Pages and setting rows whose title contains [query], best match first. [label] resolves string resources. */
internal fun searchSettings(query: String, label: (Int) -> String): List<SettingsSearchResult> {
    val term = SearchQuery(query, fuzzy = false)
    if (term.literal.isEmpty()) return emptyList()
    val pages = SettingsSearchIndex.pages
    fun route(page: SettingsPage) = generateSequence(page) { pages[it]?.second }.filter { it in pages }.toList().asReversed()
    fun path(page: SettingsPage) = route(page).map { label(pages.getValue(it).first) }
    val entries = pages.map { (page, entry) -> SettingsSearchResult(page, label(entry.first), path(entry.second), route(page)) } +
        SettingsSearchIndex.items.filter { it.page in pages }.map { item ->
            label(item.title).let { title ->
                SettingsSearchResult(item.page, title, path(item.page) + listOfNotNull(item.section?.let(label)), route(item.page), row = title)
            }
        }
    return entries.mapNotNull { result -> SearchNames(result.title).score(term)?.let { it to result } }
        .sortedWith(compareBy({ it.first }, { it.second.path.size }, { it.second.title })).map { it.second }
}
