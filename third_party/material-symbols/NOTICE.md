# Google Material Symbols

The `ms_*.xml` Android drawables in `app/src/main/res/drawable/` are derived from
**Material Symbols Outlined** by Google.

- Upstream: https://github.com/google/material-design-icons
- Browse: https://fonts.google.com/icons
- License: Apache License 2.0 (unmodified upstream license in [LICENSE](LICENSE))
- Retrieved: 2026-09-23
- Style: Outlined, optical size 24, weight 400, grade 0, fill 0.

## Conversion

For SVG-derived resources, path data is copied verbatim from the official 24px SVG files. Android
VectorDrawable has no negative-origin viewBox, so each resource preserves the
original 960 by 960 viewport and adds `translateY="960"` to represent the original
SVG `viewBox="0 -960 960 960"`. There is no path redrawing, non-uniform scaling,
or stroke-width adjustment. Intrinsic display size is 24dp; Compose applies the
current content tint.

## Sources

### Music widget icons added 2026-11-05

- `ms_play_arrow_filled.xml` (fill 1): https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/play_arrow/materialsymbolsoutlined/play_arrow_fill1_24px.xml
- `ms_pause_filled.xml` (fill 1): https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/pause/materialsymbolsoutlined/pause_fill1_24px.xml
- `ms_play_circle.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/play_circle/materialsymbolsoutlined/play_circle_24px.xml
- `ms_pause_circle.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/pause_circle/materialsymbolsoutlined/pause_circle_24px.xml
- `ms_chevron_backward.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/chevron_backward/materialsymbolsoutlined/chevron_backward_24px.xml
- `ms_chevron_forward.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/chevron_forward/materialsymbolsoutlined/chevron_forward_24px.xml
- `ms_album.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/album/materialsymbolsoutlined/album_24px.xml
- `ms_artist.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/artist/materialsymbolsoutlined/artist_24px.xml
- `ms_shuffle.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/shuffle/materialsymbolsoutlined/shuffle_24px.xml
- `ms_linear_scale.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/linear_scale/materialsymbolsoutlined/linear_scale_24px.xml

Official Android vector paths and viewports retained. Tint is supplied by Compose.
Same Apache-2.0 license.

### App stack icon added 2026-10-15

- `ms_steppers.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/steppers/materialsymbolsoutlined/steppers_24px.xml

Official vector path retained. Same Apache-2.0 license.

### Grace button suggestions added 2026-10-09

- `ms_format_list_bulleted.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/format_list_bulleted/materialsymbolsoutlined/format_list_bulleted_24px.svg
- `ms_power_settings_new.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/power_settings_new/materialsymbolsoutlined/power_settings_new_24px.svg
- `ms_bolt.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/bolt/materialsymbolsoutlined/bolt_24px.svg

Official SVG paths retained using the conversion above. Same Apache-2.0 license.

### Productivity icons added 2026-10-08

- `ms_widgets.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/widgets/materialsymbolsoutlined/widgets_24px.xml
- `ms_notifications.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/notifications/materialsymbolsoutlined/notifications_24px.xml
- `ms_visibility_off.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/visibility_off/materialsymbolsoutlined/visibility_off_24px.xml
- `ms_gesture.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/gesture/materialsymbolsoutlined/gesture_24px.xml
- `ms_vibration.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/vibration/materialsymbolsoutlined/vibration_24px.xml

Official Android vectors; paths and 960px viewports retained, tint supplied by Compose. Same Apache-2.0 license.

### Work profile icon added 2026-10-08

- `ms_work.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/work/materialsymbolsoutlined/work_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose. Same Apache-2.0 license.)

### Popup icon added 2026-10-07

- `ms_outbound.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/outbound/materialsymbolsoutlined/outbound_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose. Same Apache-2.0 license.)

### Recently installed folder icon added 2026-10-06

- `ms_history_2.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/history_2/materialsymbolsoutlined/history_2_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose. Same Apache-2.0 license.)

### Weather icons added 2026-09-27

These resources use the official Android vectors at revision
[`bd8cb85bd4bad964fe6918f79665bb40c3a8efef`](https://github.com/google/material-design-icons/tree/bd8cb85bd4bad964fe6918f79665bb40c3a8efef).
They are Material Symbols Outlined, optical size 24, weight 400, grade 0,
fill 0, under the same Apache-2.0 license included above. Google's [Material
Symbols guide](https://developers.google.com/fonts/docs/material_symbols#licensing)
also documents the license.

The official Android `pathData`, 24dp dimensions and 960px viewport are
unchanged. The root `android:tint="?attr/colorControlNormal"` is removed and
the path fill is changed from white to black; Compose supplies the final
color. The home row uses its wallpaper-aware text color, while the agenda
uses `MaterialTheme.colorScheme.onSurface`. No icons are downloaded at runtime.

| Forecast | Resource / official Android source |
| --- | --- |
| Clear, day | [`ms_sunny.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/sunny/materialsymbolsoutlined/sunny_24px.xml) |
| Clear, night | [`ms_clear_night.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/clear_night/materialsymbolsoutlined/clear_night_24px.xml) |
| Partly cloudy, day | [`ms_partly_cloudy_day.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/partly_cloudy_day/materialsymbolsoutlined/partly_cloudy_day_24px.xml) |
| Partly cloudy, night | [`ms_partly_cloudy_night.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/partly_cloudy_night/materialsymbolsoutlined/partly_cloudy_night_24px.xml) |
| Cloudy | [`ms_cloud.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/cloud/materialsymbolsoutlined/cloud_24px.xml) |
| Rain | [`ms_rainy.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/rainy/materialsymbolsoutlined/rainy_24px.xml) |
| Snow | [`ms_weather_snowy.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/weather_snowy/materialsymbolsoutlined/weather_snowy_24px.xml) |
| Thunderstorm | [`ms_thunderstorm.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/thunderstorm/materialsymbolsoutlined/thunderstorm_24px.xml) |
| Fog | [`ms_foggy.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/foggy/materialsymbolsoutlined/foggy_24px.xml) |
| Wind | [`ms_air.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/air/materialsymbolsoutlined/air_24px.xml) |
| Hail | [`ms_weather_hail.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/weather_hail/materialsymbolsoutlined/weather_hail_24px.xml) |
| Sleet / mixed precipitation | [`ms_weather_mix.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/weather_mix/materialsymbolsoutlined/weather_mix_24px.xml) |

Day/night is independent of the UI theme. Unknown forecasts retain a neutral
em dash with the supplied accessible description.

### Other icons

App package row icon added 2026-10-06 (official Android vector retained, tint supplied by Compose):

- `ms_android.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/android/materialsymbolsoutlined/android_24px.xml

Search icons added 2026-10-05 (official SVG paths retained, same 24dp conversion):

- `ms_travel_explore.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/travel_explore/materialsymbolsoutlined/travel_explore_24px.svg
- `ms_more_vert.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/more_vert/materialsymbolsoutlined/more_vert_24px.svg

Icon designer entry added 2026-10-03:

- `ms_design_services.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/design_services/materialsymbolsoutlined/design_services_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose.)

Clock-style reset button added 2026-10-02:

- `ms_restart_alt.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/restart_alt/materialsymbolsoutlined/restart_alt_24px.svg
  (Official SVG path retained; same 24dp conversion and Apache-2.0 license above.)

Favorite reorder handle added 2026-09-27:

- `ms_drag_indicator.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/drag_indicator/materialsymbolsoutlined/drag_indicator_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose.)

Media controls added 2026-09-27 (same official Outlined 24px style):

- `ms_play_arrow.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/play_arrow/materialsymbolsoutlined/play_arrow_24px.svg
- `ms_pause.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/pause/materialsymbolsoutlined/pause_24px.svg
- `ms_skip_previous.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/skip_previous/materialsymbolsoutlined/skip_previous_24px.svg
- `ms_skip_next.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/skip_next/materialsymbolsoutlined/skip_next_24px.svg
- `ms_music_note.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/music_note/materialsymbolsoutlined/music_note_24px.svg

- `ms_account_balance.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/account_balance/materialsymbolsoutlined/account_balance_24px.svg
- `ms_person.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/person/materialsymbolsoutlined/person_24px.svg
- `ms_code.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/code/materialsymbolsoutlined/code_24px.svg
- `ms_link.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/link/materialsymbolsoutlined/link_24px.svg
- `ms_close.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/close/materialsymbolsoutlined/close_24px.svg

- `ms_star.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/star/materialsymbolsoutlined/star_24px.svg
- `ms_info.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/info/materialsymbolsoutlined/info_24px.svg
- `ms_hourglass_empty.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/hourglass_empty/materialsymbolsoutlined/hourglass_empty_24px.svg
- `ms_category.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/category/materialsymbolsoutlined/category_24px.svg
- `ms_delete.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/delete/materialsymbolsoutlined/delete_24px.svg
- `ms_expand_more.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/expand_more/materialsymbolsoutlined/expand_more_24px.svg
- `ms_settings.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/settings/materialsymbolsoutlined/settings_24px.svg
- `ms_open_in_new.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/open_in_new/materialsymbolsoutlined/open_in_new_24px.svg
- `ms_add.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/add/materialsymbolsoutlined/add_24px.svg
- `ms_edit.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/edit/materialsymbolsoutlined/edit_24px.svg
- `ms_apps.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/apps/materialsymbolsoutlined/apps_24px.svg
- `ms_check.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/check/materialsymbolsoutlined/check_24px.svg
- `ms_palette.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/palette/materialsymbolsoutlined/palette_24px.svg
- `ms_home.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/home/materialsymbolsoutlined/home_24px.svg
- `ms_search.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/search/materialsymbolsoutlined/search_24px.svg

Settings icon added 2026-10-28 (same official Outlined 24px style; original path and 960px viewport retained, tint supplied by Compose):

- `ms_splitscreen_landscape.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/splitscreen_landscape/materialsymbolsoutlined/splitscreen_landscape_24px.xml

### Category icons added 2026-10-31

These are official Material Symbols Outlined Android vectors, optical size 24,
weight 400, grade 0 and fill 0, under the Apache-2.0 license above. The original
24dp dimensions, 960px viewport and `pathData` are retained. Only the root
`android:tint` is removed and the white path fill is changed to black; Compose
supplies the displayed color.

- `ms_keep.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/keep/materialsymbolsoutlined/keep_24px.xml
- `ms_notifications_unread.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/notifications_unread/materialsymbolsoutlined/notifications_unread_24px.xml
- `ms_circle.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/circle/materialsymbolsoutlined/circle_24px.xml
- `ms_clear_day.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/clear_day/materialsymbolsoutlined/clear_day_24px.xml
- `ms_bedtime.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/bedtime/materialsymbolsoutlined/bedtime_24px.xml

The category chooser also reuses the existing `ms_music_note.xml` and
`ms_info.xml` resources attributed above, and `ms_schedule.xml` from the official
Material Symbols [Schedule SVG](https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/schedule/materialsymbolsoutlined/schedule_24px.svg).
The None option draws no icon.

### Touch-area preview icon added 2026-11-02

- `ms_touch_app.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/touch_app/materialsymbolsoutlined/touch_app_24px.xml
  (Official Material Symbols Outlined Android vector, optical size 24, weight 400,
  grade 0 and fill 0. The original path and 960px viewport are retained; the root
  tint is removed and the path fill is black, with Compose supplying its color.)

### Clock region icons added 2026-11-03

- `ms_view_week.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/view_week/materialsymbolsoutlined/view_week_24px.svg
- `ms_battery_android_5.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_5/materialsymbolsoutlined/battery_android_5_24px.svg

Official Material Symbols Outlined SVG paths retained using the 24dp conversion
above (optical size 24, weight 400, grade 0, fill 0). Same Apache-2.0 license.

### Clock content icons added 2026-11-04

- `ms_label.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/label/materialsymbolsoutlined/label_24px.svg
- `ms_label_off.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/label_off/materialsymbolsoutlined/label_off_24px.svg
- `ms_humidity_percentage.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/humidity_percentage/materialsymbolsoutlined/humidity_percentage_24px.svg
- `ms_location_on.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/location_on/materialsymbolsoutlined/location_on_24px.svg
- `ms_mode_heat_cool.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/mode_heat_cool/materialsymbolsoutlined/mode_heat_cool_24px.svg
- `ms_update.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/update/materialsymbolsoutlined/update_24px.svg

Official Material Symbols Outlined SVG paths retained using the 24dp conversion
above (optical size 24, weight 400, grade 0, fill 0). Same Apache-2.0 license.

### Battery indicator icons added 2026-11-04

These are official Material Symbols Outlined SVG paths, retained verbatim using
the 24dp conversion above (optical size 24, weight 400, grade 0, fill 0), under
the same Apache-2.0 license. Android and Android Frame each provide one charging
symbol; Battery Bar uses the official charging-level symbols. Android Frame has
no empty/zero symbol, so its lowest level uses `battery_android_frame_1`.

- `ms_battery_android_0.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_0/materialsymbolsoutlined/battery_android_0_24px.svg
- `ms_battery_android_1.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_1/materialsymbolsoutlined/battery_android_1_24px.svg
- `ms_battery_android_2.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_2/materialsymbolsoutlined/battery_android_2_24px.svg
- `ms_battery_android_3.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_3/materialsymbolsoutlined/battery_android_3_24px.svg
- `ms_battery_android_4.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_4/materialsymbolsoutlined/battery_android_4_24px.svg
- `ms_battery_android_6.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_6/materialsymbolsoutlined/battery_android_6_24px.svg
- `ms_battery_android_full.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_full/materialsymbolsoutlined/battery_android_full_24px.svg
- `ms_battery_android_bolt.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_bolt/materialsymbolsoutlined/battery_android_bolt_24px.svg
- `ms_battery_android_frame_1.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_frame_1/materialsymbolsoutlined/battery_android_frame_1_24px.svg
- `ms_battery_android_frame_2.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_frame_2/materialsymbolsoutlined/battery_android_frame_2_24px.svg
- `ms_battery_android_frame_3.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_frame_3/materialsymbolsoutlined/battery_android_frame_3_24px.svg
- `ms_battery_android_frame_4.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_frame_4/materialsymbolsoutlined/battery_android_frame_4_24px.svg
- `ms_battery_android_frame_5.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_frame_5/materialsymbolsoutlined/battery_android_frame_5_24px.svg
- `ms_battery_android_frame_6.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_frame_6/materialsymbolsoutlined/battery_android_frame_6_24px.svg
- `ms_battery_android_frame_full.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_frame_full/materialsymbolsoutlined/battery_android_frame_full_24px.svg
- `ms_battery_android_frame_bolt.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_android_frame_bolt/materialsymbolsoutlined/battery_android_frame_bolt_24px.svg
- `ms_battery_0_bar.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_0_bar/materialsymbolsoutlined/battery_0_bar_24px.svg
- `ms_battery_1_bar.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_1_bar/materialsymbolsoutlined/battery_1_bar_24px.svg
- `ms_battery_2_bar.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_2_bar/materialsymbolsoutlined/battery_2_bar_24px.svg
- `ms_battery_3_bar.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_3_bar/materialsymbolsoutlined/battery_3_bar_24px.svg
- `ms_battery_4_bar.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_4_bar/materialsymbolsoutlined/battery_4_bar_24px.svg
- `ms_battery_5_bar.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_5_bar/materialsymbolsoutlined/battery_5_bar_24px.svg
- `ms_battery_6_bar.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_6_bar/materialsymbolsoutlined/battery_6_bar_24px.svg
- `ms_battery_full.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_full/materialsymbolsoutlined/battery_full_24px.svg
- `ms_battery_charging_full.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_charging_full/materialsymbolsoutlined/battery_charging_full_24px.svg
- `ms_battery_charging_20.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_charging_20/materialsymbolsoutlined/battery_charging_20_24px.svg
- `ms_battery_charging_30.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_charging_30/materialsymbolsoutlined/battery_charging_30_24px.svg
- `ms_battery_charging_50.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_charging_50/materialsymbolsoutlined/battery_charging_50_24px.svg
- `ms_battery_charging_60.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_charging_60/materialsymbolsoutlined/battery_charging_60_24px.svg
- `ms_battery_charging_80.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_charging_80/materialsymbolsoutlined/battery_charging_80_24px.svg
- `ms_battery_charging_90.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/battery_charging_90/materialsymbolsoutlined/battery_charging_90_24px.svg

The existing `ms_battery_android_5.xml` and `ms_bolt.xml` are reused without
modification; their official sources are listed above.

### Percentage toggle icon added 2026-11-04

- `ms_percent.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/percent/materialsymbolsoutlined/percent_24px.svg

Official Material Symbols Outlined SVG path retained using the 24dp conversion
above (optical size 24, weight 400, grade 0, fill 0). Same Apache-2.0 license.
