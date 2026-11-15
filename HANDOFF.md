# Grace Launcher fork: handoff

## Repositories
- **Fork:** `jloutsch/GraceLauncher` (default branch `master`).
- **Upstream:** `Galaxy-rio/GraceLauncher` (default branch `master`, not `main`). Add it as a remote: `git remote add upstream https://github.com/Galaxy-rio/GraceLauncher && git fetch upstream --tags`.
- **Versions:** the fork is on 1.2.3 (schema 33). Upstream released **v1.2.4** on Nov 14 (schema 34), and has a few commits after the tag.

## Current task: finish PR #33, "Sync with upstream v1.2.4"
- **Branch:** `sync-v1.2.4`, with merge commit `d912603` merging the `v1.2.4` tag into the fork's `master`. Pushed, PR open: https://github.com/jloutsch/GraceLauncher/pull/33
- **Never built or tested:** the cloud session couldn't download the Android SDK. Next steps:
  1. `./gradlew :app:testDebugUnitTest`
  2. `./gradlew :app:assembleDebug`, which produces the APK at `app/build/outputs/apk/debug/`
  3. `./gradlew :app:connectedDebugAndroidTest` with the phone connected (Galaxy Note10+, Android 12)
  4. Fix anything that fails, push to `sync-v1.2.4`, and merge PR #33.
- **Build setup:** compileSdk 37, build-tools 36.1.0, AGP 9.3.3, Gradle 9.5.0 (wrapper), Kotlin 2.4.20, KSP 2.3.10, Java target 11 (JDK 17+ to run Gradle).
- **Signing:** there's no `signingConfig`, so debug builds are signed with the building machine's debug key. Build on the same machine as the APK currently installed and it will install as an update. Otherwise Android refuses it, and you'd have to export a backup first (Settings → Backup and restore), uninstall, install, then import.

### Conflict resolutions in `d912603`, to review
- **Weather app: upstream's replaces the fork's.** Upstream 1.2.4 has its own weather app choice (`weatherConnection.appKey`, Settings → Weather; unset opens Breezy Weather) with the same `SettingsPage.WeatherApp` and `WeatherAppSettings` names as the fork's.
  - Removed: the fork's `LauncherSettings.weatherAppKey`, its settings item, its picker and its four strings.
  - **Migration:** `LauncherSettingsRepository.toSettings()` reads the fork's old value from `clockStyleJson` (field `WeatherAppKeyField` in `data/ForkSettingsJson.kt`) into `weatherConnection.appKey` when upstream's value is unset. Writes no longer store the fork field.
  - **Kept as a fork-only change:** `ui/weather/HourlyWeather.kt` makes the whole hourly forecast clickable to open the weather app. Upstream only opens it from the location line beneath. Scrolling doesn't trigger it.
  - **Behaviour change:** with no app chosen, a tap now opens Breezy Weather instead of doing nothing.
- **Settings home:** the fork's Backup entry is kept next to upstream's new Language entry (Android 13+).
- **`WidgetSetupActivity`:** a widget re-bound after a backup import still goes to its saved slot via `restoreRepository.resolve`. Otherwise it uses upstream's logic, including replacing the Agenda widget draft and deleting the IDs it removes.
- **Database:** `SCHEMA_VERSION = 34`; `Migration33To34` added to the fork's `Migrations` array and to `LauncherSettingsPersistenceTest`. `34.json` is identical to upstream's.
- **Tests changed:**
  - `WeatherUiTest`: kept the tap-versus-scroll test; removed the fork's home-weather fallback test.
  - `WidgetStackStorageTest`: now tests the old-weather-app migration.
  - `BackupRoundTripTest` and `ForkLegacyBackupTest`: now assert `weatherConnection.appKey`.
- **Check on the phone after installing:** Settings → Weather still shows your chosen app; tapping the hourly forecast opens it while scrolling doesn't; Backup and Language entries both appear; Tasks.org and the Agenda widget work.

## Fork conventions
- **Database:** the fork's Room schema mirrors upstream's exactly, so upstream migrations apply as-is. Store fork-only settings as extra fields inside upstream's JSON columns (`data/ForkSettingsJson.kt`). Don't add columns, because version numbers would collide with upstream's.
  - `ForkLegacyBackup` converts backups from the old fork builds that did add columns (schema-30 era).
  - Room regenerates `app/schemas/.../<N>.json` on each build; commit it.
- **Syncing upstream:** done as `sync-…` branches plus a PR (see #27, #31, #33). The fork's earlier headphone-apps feature was replaced by upstream's App Categories "Music" category (condition: headphones connected) in PR #26.
- **Contributing upstream:** open an issue first, then a PR. The README names GitHub Issues as the channel. The maintainer asked for contributions credited to Justin only, with no Claude author or co-author trailers. PR #171 (renamed-apps fix) was accepted that way.

## Other loose ends
- **Not merged, still worth doing:** `widget-screen-stacks` (1 commit). When a new stack or folder is created from an editor's "Add app or widget stack" picker, it's added to that surface straight away. Today a stack created from the Widget Screen editor isn't added until you tick it. It needs rebasing onto current `master`, because `FolderSettings` and `AppGroupPickerScreen` in `ui/settings/` have changed.
- **Stale branches to delete on GitHub** (all superseded or merged): `ccr-f372cc36-e9savh`, `upstream-headphone-apps`, `folder-shortcuts`, `notification-sender`, `weather`, plus the merged feature branches.
- **Open fork issues:** #25 (test the renamed-app fix with a real app update), #6 (unchecking a Favorites-only folder hides it everywhere), #9 (ask for calendar/weather access after importing a backup), #7 (backup follow-ups), #4 (media player shows only one player at a time). #20 (car stereos count as headphones) is closed as intended.
- **Possible feature:** sorting the app list by usage currently works only within each letter (Settings → Productivity → Digital wellbeing → Sort by app usage). For a "most used" section, use App Categories' "Most frequently opened" dynamic rule shown in the app list.

## Cloud environment
- The cloud environment "Android weather apps" allows `dl.google.com`, so Android builds can run in future cloud sessions that use it. The Default environment blocks it.
