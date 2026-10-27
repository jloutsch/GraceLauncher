# Backup and restore: design

Date: 2026-10-27
Branch: `feature/backup-restore` in the fork `jloutsch/GraceLauncher`
Status: draft, awaiting approval

## Why

The user moves to a new phone (or wipes this one), installs GraceLauncher, and wants their launcher back exactly as it was without rebuilding it by hand. Last time they moved phones they had to recreate every folder, re-add every app to each folder, rebuild favorites, and redo every setting.

Other launchers that rely on Google's backup (Niagara is the example the user gave) give no way to check that a backup exists or worked. The user wants something they can trust before they need it: a real file they can see, and an import that tells them exactly what it restored and what it could not.

Android Auto Backup is not a fit for this app. GraceLauncher ships through F-Droid and GitHub Releases, which are signed with different keys, so a backup from one install does not restore into the other. De-Googled phones do not use Google's backup at all, and Google's backup runs at most daily under conditions the user does not control.

## Goals

1. Export the full launcher state to one file the user can see and move.
2. Import that file on another phone (or the same phone) and end up with the same launcher.
3. Show the user exactly what the import restored and what it skipped.
4. Keep backups fresh without the user having to remember: an optional automatic export, plus a visible "last exported" date.

## Non-goals

- Importing from Niagara or any other launcher. Checked 2026-10-27: Niagara's `.nlb` backup is encrypted (an `nlb` header followed by bytes with 8.0 bits/byte entropy, not zip or deflate) and, by Niagara's own text, tied to the account that created it.
- Built-in cloud accounts (Drive, Nextcloud, etc.). The user picks a location through Android's file picker, and whatever provider they pick handles the upload.
- Visible placeholders for apps that are not installed. Missing apps keep a hidden spot instead (see "Missing apps"). The expected flow is to import after Samsung Smart Switch (or similar) has reinstalled nearly all apps.
- Changing the Android Auto Backup rules (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`). They stay as they are.
- Restoring settings stored inside third-party widgets (for example, which city a weather widget shows). That state belongs to the widget's own app.

## Anti-goals (would count as failure even if it technically works)

- An import that silently drops folder contents, puts apps in the wrong folders, or leaves settings half applied.
- An import that fails partway and leaves the launcher in a mixed state.
- Anything the user has to inspect by hand to find out what is missing.

## User-facing behavior

### Settings: new "Backup and restore" section

Placed in the settings home list (`ui/settings/LauncherSettingsScreen.kt`, `SettingsHome`). Contains:

- **Export backup.** Opens Android's save screen (`ActivityResultContracts.CreateDocument`). Default file name `gracelauncher-YYYY-MM-DD.zip`. The user picks any location the picker offers.
- **Import backup.** Opens Android's open screen (`ActivityResultContracts.OpenDocument`). Then shows a confirmation: "This replaces your current setup." Then runs the import and shows the summary (below).
- **Last exported: N days ago.** Covers both manual and automatic exports. Shows "Never" before the first export.
- **Automatic backup** toggle, plus **Backup folder** (picked once with `ActivityResultContracts.OpenDocumentTree`, permission persisted with `takePersistableUriPermission`).
- **Automatic backup failure line**, shown only after a failure: "Automatic backup failed on [date]: [reason]", for example "folder not accessible".

### Import summary

Shown after every import. It must state counts and list anything that did not come back, for example:

> Restored 5 folders, 50 apps, 13 favorites, 6 widgets, all settings.
> Not installed yet (will appear once installed): Simple MBTA, Transit Now.
> Widgets that need setup: GeauxWeather.

Every skipped item is named. No silent drops.

### Missing apps

Decided by the user 2026-10-27: apps referenced by the backup but not installed at import time keep their saved spot in favorites, folders, and home order. They are not shown while missing and appear in that spot once the app is installed. The summary lists them as "Not installed yet".

This follows what the code already does for favorites: `FavoritesStore.reorder` keeps entries for hidden or uninstalled apps "so unhiding or reinstalling restores them" (`FavoritesStore.kt:50`). Folder contents are stored as item keys in `app_popups` and looked up at display time (`LauncherViewModel.popupItems`). I have not checked whether editing a folder drops keys of uninstalled apps. The builder must check this and make sure an edit keeps them.

This also covers importing while Smart Switch is still installing apps.

### Widgets

Widget IDs do not carry over between phones, so each widget in the backup gets a new ID at import time and goes back into its original place (home, folder pop-up, or widget screen) with its saved size and position.

- Binding uses the same path as today: `AppWidgetManager.bindAppWidgetIdIfAllowed` first, then the `ACTION_APPWIDGET_BIND` dialog if Android refuses (see `WidgetSetupActivity.kt:142`). Android's dialog has an "Always allow" checkbox; once the user ticks it, the remaining widgets bind with no prompt.
- Widgets whose provider declares a configuration activity open that screen, one at a time, as today's add-widget flow does.
- Widgets whose provider app is not installed are left out and listed in the summary.
- Order of work (my call): all non-widget data is committed first, in one transaction. Widgets are then bound from a saved queue that survives the launcher process being killed, and binding resumes on the next start until the queue is empty.
- If the user denies a bind or cancels a widget's setup screen, that widget is skipped, its slot is removed, and it is listed in the summary. It is not a failure and does not roll back the rest of the import (my call).

### Automatic backup

- Trigger: when the home screen comes to the foreground. If automatic backup is on, at least 24 hours have passed since the last successful export, and the launcher state has changed since then, write a new file in the background. No new scheduling library.
- File name: `gracelauncher-auto-YYYY-MM-DD.zip` in the chosen folder.
- Retention: keep the newest 7 automatic files. Delete older ones, but only files the launcher created with the `gracelauncher-auto-` prefix. Never touch manual exports or anything else in the folder.
- Failure: record the date and reason, show the failure line in Settings, and retry on the next trigger.

## What the backup contains

All of this exists in the code today:

| Data | Where it lives now |
|---|---|
| Settings (one row, many JSON columns) | Room `grace_launcher.db`, table `launcher_settings` (`LauncherDatabase.kt`, schema version 27) |
| Folders | Room table `folders` (`LauncherFolderEntity`) |
| Folder and app pop-up contents, including widgets in pop-ups | Room table `app_popups` (`AppPopupEntity.itemsJson`, a list of `PopupItem`) |
| Hidden apps | Room table `hidden_apps` |
| Custom item icons (metadata) | Room table `item_icons` |
| Saved shortcuts | Room table `saved_shortcuts` |
| Favorites | SharedPreferences `grace_launcher_preferences` (`FavoritesStore.kt`) |
| Appearance preferences | SharedPreferences `launcher_appearance` (`LauncherPreferences.kt`) |
| Imported clock fonts | `filesDir/clock_fonts/` |
| Custom item icon images | `filesDir/item_icons/` |

Where widget IDs live (checked in code 2026-10-27):

- Home widgets: `app_popups` row with owner `HomeContentKey` (`HomeContent.kt:5`). `launcher_settings.homeLayoutJson` holds a widget only in older data, which `migrateLegacyHomeWidget` (`LauncherSettingsRepository.kt:104`) moves into `app_popups`.
- Widget screen widgets: `app_popups` row with owner `WidgetScreenKey`. `widgetScreenJson` holds only the screen's settings (enabled, swipe, columns, lock).
- Folder and app pop-up widgets: `app_popups.itemsJson` (`PopupItem.widget`).
- The ID also appears inside item keys of the form `widget:<id>` and in `homeLayout.homeOrder`.

Import must rewrite the ID in every one of these places. Each stored widget also has `widgetProvider` (the flattened provider `ComponentName`), which is what import uses to re-create it.

Not included: caches and usage data in `noBackupFilesDir` (`launcher-apps.json`, `private-apps.json`, `usage-ranking.json`, `usage-opens.json`). They rebuild on their own and are device-specific.

## File format

A zip file containing:

- `manifest.json`: format version (start at 1), app version name and code, Room schema version, export timestamp, and counts (folders, apps, favorites, widgets) so the file can be checked without importing it.
- The launcher data, in a form the builder chooses (see "Left to the builder").
- `fonts/` and `icons/` with the files from `clock_fonts/` and `item_icons/`.

Rules:

- Import refuses a file whose format version is newer than the app supports, and says so in plain words.
- Import accepts files from older app versions.
- Every section is optional. A section missing from the file leaves that part of the current state unchanged (my call). This lets the converted Niagara recording, which has no GraceLauncher settings, import favorites, folders, and widgets without resetting settings.
- App and folder references use GraceLauncher's existing keys: the flattened `ComponentName` for apps (`LauncherApp.key`), and `folder:<id>` for folders.

## Import safety

- Validate the whole file first (manifest, version, every entry parses) before changing anything.
- Before replacing anything, write a safety export of the current state to app-private storage. If applying the data fails, restore from it automatically.
- No "Undo last import" button (my call). After an import, the launcher's existing cleanup deletes unused widget IDs (`WidgetSetupActivity.kt:94`, `LauncherViewModel.kt:945`), so an undo could not bring old widgets back as they were. Keeping them alive would mean changing that cleanup, which is not worth it. If the user wants the old setup back, they import an earlier backup file.
- Apply all non-widget data in one Room transaction plus SharedPreferences commits. If any step fails, restore the safety export. Widgets follow afterwards (see "Widgets").
- Validate zip entry names (no `..`, no absolute paths) and cap total extracted size, since the file comes from outside the app.

## Test fixture from the user's Niagara phone

`/Users/justin/Documents/gracelauncher-import-test/niagara-home-2026-10-27.json` (outside the repo) records the user's current Niagara home screen: 13 favorites, 5 folders (50 apps), and 6 widgets, with exact component names and widget providers. Once the format exists, convert this into a real backup file. It serves two purposes:

1. The user's one-time move from Niagara to GraceLauncher.
2. A real-world import test: after import, GraceLauncher's favorites and folders must match the recording in content and order. Every widget the recording lists outside `bound_but_not_seen`, and not marked broken, must appear in its folder or on home. Widgets marked "likely" are checked by eye on the phone.

The recording has no widget sizes or positions. The converter uses each provider's default size (my call).

The converter is a one-off script kept outside the repo, next to the recording (my call). The recording lists the user's banking and personal apps and must not be committed to the fork, which is public. Unit tests in the repo use a synthetic fixture instead.

## Testing

- Unit tests (`app/src/test`): round trip (export, then import into an empty state, then compare), missing apps keep their keys and position after import and after a folder edit, widget ID remapping in every place listed under "Where widget IDs live" (including `widget:<id>` keys and `homeOrder`), a denied bind skips one widget without rolling back the rest, bind queue resumes after process death, zip entry validation, rejection of a newer format version, retention of 7 automatic files that never deletes other files.
- Instrumented tests (`app/src/androidTest`), following the existing pattern (for example `LauncherSettingsPersistenceTest.kt`): import on a real database, failure halfway restores the safety export.
- Manual test on the user's Galaxy S23+: import the converted Niagara recording and check it against the recording.

## Left to the builder

- How launcher data is stored inside the zip: copy the Room database file (after a WAL checkpoint) and the SharedPreferences XML, or serialize to JSON. JSON is easier to check and survives schema changes better. A raw database copy is less code. Either way, import must work when the file came from an older schema version.
- How "state changed since last export" is detected (for example, a hash of the exported content, or a change counter).
- Exact wording, layout, and placement of the new settings rows, following `SettingsComponents.kt`.
- Whether to re-pin saved shortcuts through `LauncherApps.pinShortcuts` on import, or list them as skipped. Some apps no longer publish the same shortcut IDs on a new phone.
- Work profile and private space apps: keys of the form `profile:<serial>:<component>` use a user serial that differs between phones. Map them to the matching profile on the new phone if one exists, otherwise list them as skipped.

## Size

About 1.5 to 2 days: half a day for export and import of settings, folders, favorites, fonts, and icons; the rest for widget re-binding, the summary, automatic backup, and tests. New user-facing strings go in `res/values/strings.xml` only. Translations are deferred until an upstream PR.
