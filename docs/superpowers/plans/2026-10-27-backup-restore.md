# Backup and Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user export the whole launcher state to one file, import it on another phone, and see exactly what came back.

**Architecture:** A new package `com.galaxyrio.gracelauncher.data.backup` writes a zip holding a copy of the Room database, the two SharedPreferences files as JSON, and the font and icon files. Import extracts the zip, opens the copied database with Room and the existing migrations (so older files upgrade on their own), and copies its rows into the live database in one transaction. Widgets are stored with placeholder IDs (−2, −3, …) that the UI never renders. `WidgetSetupActivity` gains a restore mode that binds each placeholder, one per home-screen resume, until none are left.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.4, `org.json`, `java.util.zip`, Storage Access Framework (`DocumentsContract`). No new runtime dependencies. One new test dependency: `androidx.room:room-testing` 2.8.4.

**Spec:** `docs/superpowers/specs/2026-10-27-backup-restore-design.md`

## Global Constraints

- Work on branch `feature/backup-restore` in the fork `jloutsch/GraceLauncher`. Do not push to upstream `Galaxy-rio/GraceLauncher`.
- minSdk 28, compileSdk 37, Java 11 source and target, Gradle toolchain JDK 21.
- No new runtime library. Automatic backup uses no scheduling library.
- New user-facing strings go in `res/values/strings.xml` only. No translations.
- Manual export file name: `gracelauncher-YYYY-MM-DD.zip`. Automatic: `gracelauncher-auto-YYYY-MM-DD.zip`.
- Keep the newest 7 automatic files. Delete only files with the `gracelauncher-auto-` prefix.
- Format version starts at 1. Refuse files with a newer format version or a newer Room schema version. Accept older ones.
- Do not include `noBackupFilesDir` contents (`launcher-apps.json`, `private-apps.json`, `usage-ranking.json`, `usage-opens.json`).
- Do not change `res/xml/backup_rules.xml` or `res/xml/data_extraction_rules.xml`.
- Never commit `/Users/justin/Documents/gracelauncher-import-test/`. It lists the user's personal apps and the fork is public.
- Run unit tests JVM-only. Unit tests here cannot use `org.json` (the Android stub throws), so code that touches JSON or Room is tested under `app/src/androidTest`.
- Copy the spec verbatim: "This replaces your current setup.", "Last exported: N days ago" (plural resource), "Never", "Automatic backup failed on [date]: [reason]", "folder not accessible", "Not installed yet (will appear once installed)", "Widgets that need setup".

## Review Focus

1. **Placeholder widget IDs reaching Android.** A negative ID passed to `AppWidgetHost.createView`, `deleteAppWidgetId`, or `getAppWidgetInfo` must never happen. Placeholders must stay hidden until restored. Test in Task 5.
2. **Folder editors dropping uninstalled apps.** Editing a folder that holds keys for apps not yet installed must keep those keys and their order, the same way `FavoritesStore.reorder` does. Test in Task 4.
3. **A backup from an older schema version.** It must import after Room migrates the extracted database. Test in Task 4.
4. **Export and import at the same time** (an automatic export starting during an import). They must run one after the other, never interleaved. Test in Task 4.
5. **Choosing a file that is not a backup** (a random file, a truncated zip, a Niagara `.nlb`). It must be rejected with nothing changed. Test in Task 4.

---

### Task 0: Build environment and baseline

**Files:**
- Create: `local.properties` (already gitignored)

- [ ] **Step 1: Install JDK 21.** Run `brew install --cask temurin@21`. Expected: `/usr/libexec/java_home -v 21` prints a path.
- [ ] **Step 2: Point Gradle at the SDK.** Write `sdk.dir=/opt/homebrew/share/android-commandlinetools` to `local.properties`.
- [ ] **Step 3: Run the existing unit tests.** Run `JAVA_HOME=$(/usr/libexec/java_home -v 21) sh ./gradlew :app:testDebugUnitTest`. (`gradlew` is not executable in this checkout, so call it through `sh`.) Expected: `BUILD SUCCESSFUL`. Record any failures that already exist before changing code.
- [ ] **Step 4: Use the user's phone for instrumented tests.** Serial `R5CW22SH7BM`, a Galaxy S23+. On 2026-10-27, with the user's permission, the F-Droid GraceLauncher was uninstalled from it, so the debug build installs cleanly. Niagara stays the phone's home app. Set `ANDROID_SERIAL=R5CW22SH7BM` for every connected test command.
  - **Never run `StoreScreenshotTest`** on this phone. It changes the wallpaper (`StoreScreenshotTest.kt:231`) and a secure theme setting (`StoreScreenshotTest.kt:218`).
  - **Full suite command:** `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.galaxyrio.gracelauncher.StoreScreenshotTest`.
- [ ] **Step 5: Run one existing instrumented test.** Run `ANDROID_SERIAL=R5CW22SH7BM sh ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.galaxyrio.gracelauncher.HomeContentPersistenceTest`. Expected: PASS.

In every later command, `./gradlew` means `JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_SERIAL=R5CW22SH7BM sh ./gradlew`.

### Task 1: Manifest, compatibility check, and safe zip reading

**Files:**
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/BackupManifest.kt`
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/BackupArchive.kt`
- Test: `app/src/test/java/com/galaxyrio/gracelauncher/data/backup/BackupArchiveTest.kt`
- Test: `app/src/androidTest/java/com/galaxyrio/gracelauncher/data/backup/BackupManifestTest.kt`

**Interfaces:**
- Produces:
  - `const val BACKUP_FORMAT_VERSION = 1`
  - `enum class BackupSection(val id: String)` with entries `Settings("settings")`, `HiddenApps("hidden_apps")`, `Folders("folders")`, `ItemIcons("item_icons")`, `SavedShortcuts("saved_shortcuts")`, `Popups("app_popups")`, `Favorites("favorites")`, `Appearance("appearance")`, `Fonts("fonts")`, `Icons("icons")`
  - `data class BackupCounts(val folders: Int, val apps: Int, val favorites: Int, val widgets: Int)`
  - `data class BackupProfile(val serial: Long, val type: String)` where type is `"work"` or `"private"`
  - `data class BackupManifest(val formatVersion: Int, val appVersionName: String, val appVersionCode: Long, val schemaVersion: Int, val exportedAtMillis: Long, val sections: Set<BackupSection>, val counts: BackupCounts, val profiles: List<BackupProfile>)` with `fun encode(): String` and `companion object { fun decode(json: String): BackupManifest? }`. Unknown section IDs are ignored on decode.
  - `sealed interface BackupProblem` with `data class NewerFormat(val found: Int)`, `data class NewerSchema(val found: Int)`, `data object NotABackup`, `data object TooLarge`, `data class UnsafeEntry(val name: String)`
  - `fun checkCompatible(manifest: BackupManifest, supportedFormat: Int = BACKUP_FORMAT_VERSION, supportedSchema: Int): BackupProblem?`
  - `object BackupArchive` with `const val MANIFEST = "manifest.json"`, `const val DATABASE = "data/launcher.db"`, `const val PREFERENCES = "data/preferences.json"`, `const val LABELS = "data/labels.json"`, `const val FONTS_DIR = "fonts/"`, `const val ICONS_DIR = "icons/"`, `const val MAX_TOTAL_BYTES = 268_435_456L`, `fun safeEntryName(name: String): String?`, and `fun extract(input: java.io.InputStream, into: java.io.File): BackupProblem?` (null means success)

- [ ] **Step 1: Write failing unit tests** in `BackupArchiveTest`:
  - `safeEntryNameRejectsTraversalAndAbsolutePaths`: `safeEntryName("../x")`, `safeEntryName("/etc/x")`, `safeEntryName("fonts/../../x")`, and `safeEntryName("fonts\\..\\x")` are all `null`. `safeEntryName("fonts/Inter.ttf") == "fonts/Inter.ttf"`.
  - `extractStopsPastSizeCap`: a zip whose entries total `MAX_TOTAL_BYTES + 1` bytes (use a stored entry of zeros, streamed, not held in memory) returns `BackupProblem.TooLarge`.
  - `extractRejectsUnsafeEntry`: a zip with entry `../evil` returns `UnsafeEntry("../evil")`, and nothing is written outside `into`.
  - `extractRejectsNonZip`: 1 KB of random bytes returns `NotABackup`.
  - `compatibilityRules`: `checkCompatible` returns `NewerFormat(2)` for format 2, `NewerSchema(28)` for schema 28 when supported is 27, and `null` for format 1 with schema 20.
- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests 'com.galaxyrio.gracelauncher.data.backup.BackupArchiveTest'`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** `BackupManifest.kt` and `BackupArchive.kt`. `extract` counts bytes as it decompresses and never trusts the size in the zip header. A missing `manifest.json` returns `NotABackup`.
- [ ] **Step 4: Write the instrumented test** `manifestRoundTrips` in `BackupManifestTest`: `decode(m.encode()) == m` for a manifest with every section and two profiles. `decode("{}")` and `decode("not json")` return `null`.
- [ ] **Step 5: Run all of Task 1's tests.** Unit command as in Step 2, plus `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.galaxyrio.gracelauncher.data.backup.BackupManifestTest`. Expected: PASS.
- [ ] **Step 6: Commit.** Message: `Add backup manifest and safe zip extraction`.

### Task 2: Widget placeholders

**Files:**
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/WidgetPlaceholders.kt`
- Test: `app/src/test/java/com/galaxyrio/gracelauncher/data/backup/WidgetPlaceholdersTest.kt`

**Interfaces:**
- Consumes: `PopupItem(key, widget: HomeLayout?, grid)` and `HomeLayout(widgetId, widgetProvider, homeOrder, …)` from `data/LauncherItems.kt` and `data/HomeLayout.kt`. These are data classes. Do not call their `encode`/`decode` here, because that needs `org.json`.
- Produces:
  - `const val FIRST_PLACEHOLDER_ID = -2`
  - `fun isWidgetPlaceholder(id: Int): Boolean` (true when `id <= -2`)
  - `data class WidgetLayout(val popups: Map<String, List<PopupItem>>, val homeOrder: List<String>?)`
  - `data class PendingWidget(val ownerKey: String, val placeholderId: Int, val provider: String?)`
  - `fun WidgetLayout.toPlaceholders(): WidgetLayout`: every widget with `widgetId >= 0` gets −2, −3, … in a stable order (owner keys sorted, then list order). Its item key `widget:<old>` becomes `widget:<new>` in `popups` and in `homeOrder`.
  - `fun WidgetLayout.pending(): List<PendingWidget>` in that same order
  - `fun WidgetLayout.replaceWidget(placeholderId: Int, newId: Int): WidgetLayout`: rewrites `widgetId`, the item key, and `homeOrder`. Keeps size, offsets, and `grid`.
  - `fun WidgetLayout.dropWidget(placeholderId: Int): WidgetLayout`: removes the item and its `homeOrder` entry

- [ ] **Step 1: Write failing tests:**
  - `assignsPlaceholdersAcrossAllOwners`: widgets 41 in `HomeContentKey`, 7 in `folder:a`, and 9 in `WidgetScreenKey`, with `homeOrder = ["grace:calendar", "widget:41"]`. After `toPlaceholders()`, no `widgetId >= 0` remains, the IDs are `{-2, -3, -4}`, `homeOrder == ["grace:calendar", "widget:<home placeholder>"]`, and each item key matches its `widgetId`.
  - `replaceKeepsPlacementAndUpdatesOrder`: after `replaceWidget(-2, 105)`, the item has `widgetId == 105`, key `widget:105`, the same `widgetHeightDp` and `grid`, and `homeOrder` has `widget:105`.
  - `dropRemovesItemAndOrderEntry`.
  - `appEntriesUntouched`: non-widget `PopupItem`s keep their keys and order.
- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests 'com.galaxyrio.gracelauncher.data.backup.WidgetPlaceholdersTest'`. Expected: FAIL.
- [ ] **Step 3: Implement `WidgetPlaceholders.kt`.**
- [ ] **Step 4: Run them.** Expected: PASS.
- [ ] **Step 5: Commit.** Message: `Add widget placeholder remapping for restore`.

### Task 3: App key rules (missing apps, work profile serials)

**Files:**
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/BackupKeys.kt`
- Test: `app/src/test/java/com/galaxyrio/gracelauncher/data/backup/BackupKeysTest.kt`

**Interfaces:**
- Produces:
  - `fun isAppKey(key: String): Boolean`: true for a flattened `ComponentName` (`pkg/cls`) or `profile:<serial>:<component>`. False for `folder:`, `widget:`, `grace:`, and the reserved keys in `HomeContent.kt`.
  - `fun missingAppKeys(referenced: Iterable<String>, installed: Set<String>): List<String>`: distinct, in first-seen order
  - `fun remapProfileKey(key: String, serials: Map<Long, Long>): String`: rewrites the serial when it is mapped. Otherwise returns the key unchanged.

- [ ] **Step 1: Write failing tests:**
  - `missingKeepsOrderAndIgnoresNonApps`: referenced `["a/a.Main", "folder:x", "b/b.Main", "widget:-2", "a/a.Main"]` with installed `{"a/a.Main"}` gives `["b/b.Main"]`.
  - `profileSerialRemapped`: `remapProfileKey("profile:10:p/p.M", mapOf(10L to 13L)) == "profile:13:p/p.M"`. An unmapped serial is unchanged, and a plain key is unchanged.
- [ ] **Step 2: Run.** `./gradlew :app:testDebugUnitTest --tests 'com.galaxyrio.gracelauncher.data.backup.BackupKeysTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Check the shortcut key format in `LauncherApp.key` / `shortcut?.key` (`data/LauncherApp.kt:51`) and make `isAppKey` return false for shortcut keys.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** Message: `Add backup key rules for missing apps and profiles`.

### Task 4: Export and import

**Files:**
- Modify: `app/src/main/java/com/galaxyrio/gracelauncher/data/LauncherDatabase.kt` (companion near line 330, `@Database` at line 126)
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/BackupDao.kt`
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/BackupExporter.kt`
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/BackupImporter.kt`
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/BackupLock.kt`
- Modify: `app/build.gradle.kts` (add `androidTestImplementation` for `androidx.room:room-testing` 2.8.4 through `gradle/libs.versions.toml`, and the Room schema dir as an androidTest asset if not already set)
- Test: `app/src/androidTest/java/com/galaxyrio/gracelauncher/data/backup/BackupRoundTripTest.kt`

**Interfaces:**
- Consumes: Task 1 (`BackupArchive`, `BackupManifest`, `checkCompatible`), Task 2 (`toPlaceholders`), Task 3 (`missingAppKeys`, `remapProfileKey`)
- Produces:
  - In `LauncherDatabase.companion`: `const val SCHEMA_VERSION = 27` (use it in `@Database(version = SCHEMA_VERSION)`), `val Migrations: Array<Migration>` (the list now inline in `getInstance`), and `abstract fun backupDao(): BackupDao` on the database. Adding a DAO does not change the schema. Confirm `27.json` is unchanged after the build.
  - `BackupDao`: for each of `launcher_settings`, `hidden_apps`, `folders`, `item_icons`, `saved_shortcuts`, `app_popups`, add `suspend fun all…(): List<Entity>`, `suspend fun clear…()`, and `@Insert(onConflict = REPLACE) suspend fun insert…(rows: List<Entity>)`
  - `object BackupLock { suspend fun <T> withLock(block: suspend () -> T): T }`: one process-wide `Mutex`
  - `class BackupExporter(context: Context, database: LauncherDatabase = LauncherDatabase.getInstance(context))` with:
    - `suspend fun export(output: OutputStream, labels: Map<String, String>): BackupManifest`: checkpoint the WAL (`PRAGMA wal_checkpoint(FULL)`), copy `grace_launcher.db` to `data/launcher.db`, write `grace_launcher_preferences` and `launcher_appearance` as typed JSON (`{"name": {"key": {"type": "string|boolean|int|long|float|stringSet", "value": …}}}`), write `data/labels.json`, copy `clock_fonts/` to `fonts/` and `item_icons/` to `icons/`, and write the manifest with every section, the counts, and the work and private profile serials.
    - `suspend fun contentHash(): String`: SHA-256 hex over the checkpointed database bytes, the preferences JSON, and the sorted name and size of each font and icon file
  - `sealed interface ImportResult` with `data class Success(val summary: ImportSummary)`, `data class Rejected(val problem: BackupProblem)`, `data class Failed(val error: Throwable, val restored: Boolean)`
  - `data class ImportSummary(val folders: Int, val apps: Int, val favorites: Int, val widgets: Int, val settingsRestored: Boolean, val notInstalled: List<String>, val widgetsMissingProvider: List<String>)`. List entries are labels from `labels.json`, falling back to the package name.
  - `class BackupImporter(context: Context, database: LauncherDatabase = LauncherDatabase.getInstance(context), private val faultAfter: BackupSection? = null)` with `suspend fun import(input: InputStream, installedAppKeys: Set<String>, installedProviders: Set<String>): ImportResult`. `faultAfter` is a test hook that throws after applying that section.

Import order, all inside `BackupLock.withLock`:
1. Extract to a fresh dir under `cacheDir`. Decode and check the manifest. Any problem returns `Rejected`, with the live state untouched.
2. Open the extracted DB with `Room.databaseBuilder(context, LauncherDatabase::class.java, file.absolutePath).addMigrations(*Migrations).build()`, read every table it holds, then close it.
3. Write a safety export of the current state to `noBackupFilesDir/backup-safety.zip` with `BackupExporter`.
4. In one `database.withTransaction`, replace only the tables whose section is in `manifest.sections`. Before inserting `app_popups` and `launcher_settings`, apply `remapProfileKey` with a map from manifest profile serials to this phone's serials of the same type (through `UserManager`/`LauncherApps`; skip types this phone lacks), and apply `toPlaceholders()` to the popups together with `settings.homeLayout.homeOrder`.
5. Replace the SharedPreferences sections with `commit()`. Replace `clock_fonts/` and `item_icons/` by moving the extracted dirs into place.
6. If steps 4 or 5 throw, import `backup-safety.zip` with the same code path and return `Failed(error, restored = true)`, or `restored = false` if that also fails.
7. Build the summary: `notInstalled` from `missingAppKeys` over favorites and every popup's app keys; `widgetsMissingProvider` from placeholders whose provider is not in `installedProviders`.

- [ ] **Step 1: Write failing instrumented tests** in `BackupRoundTripTest`. Use the real-file database pattern of `LauncherSettingsPersistenceTest`, not in-memory, because export copies the file:
  - `roundTripRestoresEveryTable`: seed settings (a non-default `clockStyle`), 2 folders with apps, 1 hidden app, favorites `["a/a.Main", "folder:f1"]`, a home widget 41, and one font file. Export, wipe all tables and prefs, then import with `installedAppKeys` holding every key. Assert that tables and prefs equal the seed, except that widget 41 is now placeholder −2 with the same provider and height, and `homeOrder` holds `widget:-2`.
  - `missingSectionLeavesStateUnchanged`: a backup whose manifest lists only `Folders` and `Popups` leaves `launcher_settings`, favorites, and fonts untouched.
  - `missingAppsKeepKeysAndAreListed`: with `"b/b.Main"` missing from `installedAppKeys`, the key stays in its folder at its position, and `summary.notInstalled == ["B label"]`.
  - `failureRestoresSafetyExport`: with `faultAfter = Popups`, the result is `Failed(restored = true)` and every table equals its value before the import.
  - `newerFormatRejectedWithoutChanges`: manifest `formatVersion = 2` gives `Rejected(NewerFormat(2))`, and the DB is unchanged.
  - Review Focus 3, `olderSchemaBackupImports`: with `MigrationTestHelper`, create a schema-26 database holding a folder row and a popup row, zip it with a manifest of `schemaVersion = 26`, import it, and assert the folder and popup are present.
  - Review Focus 4, `exportDuringImportWaits`: start an import whose `faultAfter` hook suspends on a `CompletableDeferred`, launch an export, and assert it does not finish until the deferred completes.
  - Review Focus 5, `nonBackupFileRejected`: random bytes, a zip without `manifest.json`, and a file starting with the bytes `nlb` each give `Rejected(NotABackup)`, with the DB unchanged.
  - Review Focus 2, `folderEditKeepsUninstalledKeys`: read the transforms passed to `updatePopup` at `ui/overlays/LauncherSheets.kt:445`, `ui/overlays/ItemSelectionScreen.kt:182`, and `ui/overlays/ItemSelectionScreen.kt:216`. Any that rebuilds the list from visible apps only must be changed to keep keys not in the visible set, at their positions, following `FavoritesStore.reorder` (`data/FavoritesStore.kt:49`). Extract each changed transform into a pure top-level function and test that a folder `["a", "missing", "b"]`, edited to reorder `a` and `b`, gives `["b", "missing", "a"]`. If none of them drops keys, write the test against `LauncherItemsRepository.updatePopup` and note in the commit message that no change was needed.
- [ ] **Step 2: Run them.** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.galaxyrio.gracelauncher.data.backup.BackupRoundTripTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the `LauncherDatabase` changes, `BackupDao`, `BackupLock`, `BackupExporter`, and `BackupImporter`.
- [ ] **Step 4: Run Task 4's tests, then the full unit suite.** Expected: PASS. `git diff app/schemas` shows no change.
- [ ] **Step 5: Commit.** Message: `Export and import launcher state as a backup file`.

### Task 5: Restore widgets after import

**Files:**
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/WidgetRestoreRepository.kt`
- Modify: `app/src/main/java/com/galaxyrio/gracelauncher/WidgetSetupActivity.kt` (`select` at line 129, `finishAdding` at line 249, `cancelSetup` near line 280, companion at line 306)
- Modify: `app/src/main/java/com/galaxyrio/gracelauncher/MainActivity.kt` (`onResume` at line 45)
- Modify: `app/src/main/java/com/galaxyrio/gracelauncher/ui/LauncherViewModel.kt:945` (skip placeholder IDs in `deleteAppWidgetId`)
- Test: `app/src/androidTest/java/com/galaxyrio/gracelauncher/data/backup/WidgetRestoreTest.kt`

**Interfaces:**
- Consumes: Task 2 (`WidgetLayout`, `pending`, `replaceWidget`, `dropWidget`, `isWidgetPlaceholder`)
- Produces:
  - `class WidgetRestoreRepository(database: LauncherDatabase)` with `suspend fun pending(): List<PendingWidget>` and `suspend fun resolve(placeholderId: Int, newId: Int?)`. `null` drops the widget. It applies to `app_popups` and `settings.homeLayout.homeOrder` in one transaction.
  - `WidgetSetupActivity.EXTRA_RESTORE_PLACEHOLDER = "restorePlaceholderId"` and `EXTRA_RESTORE_OWNER = "restoreOwner"`

Restore mode in `WidgetSetupActivity`. When `EXTRA_RESTORE_PLACEHOLDER` is set:
- Skip the provider picker.
- Find the `AppWidgetProviderInfo` whose `provider.flattenToString()` equals the placeholder's `widgetProvider` in `getInstalledProvidersForProfile(myUserHandle())`.
- If none is found, call `resolve(id, null)` and finish. Otherwise run the existing `select(info)` path.
- In `finishAdding`, call `resolve(placeholderId, pendingId)` instead of appending a new item.
- In `cancelSetup` (which `fail` also calls), call `resolve(placeholderId, null)` after the existing ID cleanup.
- Save and restore the placeholder ID in `onSaveInstanceState` alongside the existing keys.

In `MainActivity.onResume`: if no restore launch is in flight in this activity instance, take the first `pending()` entry and start `WidgetSetupActivity` with both extras. The in-flight flag clears in `onResume` when the setup activity has finished.

- [ ] **Step 1: Write failing instrumented tests:**
  - `resolveReplacesPlaceholderEverywhere`: seed home placeholder −2 (in `homeOrder`) and folder placeholder −3. `resolve(-2, 120)` gives home item `widget:120` with `widgetId` 120 and `homeOrder` holding `widget:120`. `resolve(-3, null)` removes the folder item. `pending()` is then empty.
  - Review Focus 1, `placeholdersNeverReachWidgetHost`: seed placeholder −2 in a folder, call `LauncherViewModel.updatePopup` with a transform that removes it, and assert no exception and that `HomeWidgetHost.deleteAppWidgetId` was never called with a negative value. Do this by guarding the call site and testing the guard function `widgetIdsToDelete(removed: List<Int>): List<Int>`. Also grep every `widget?.widgetId` / `.widgetId` read under `ui/` and confirm each render path checks `hasWidget` (`widgetId >= 0`). List them in the commit message.
- [ ] **Step 2: Run.** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.galaxyrio.gracelauncher.data.backup.WidgetRestoreTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `WidgetRestoreRepository`, restore mode, the `onResume` trigger, and the `widgetIdsToDelete` guard.
- [ ] **Step 4: Run, then run `WidgetConfigurationRoutingTest` (unit) and `PopupWidgetEditingTest` (instrumented)** to confirm the add-widget flow still works. Expected: PASS.
- [ ] **Step 5: Manual check on the phone.** Install with `./gradlew :app:installDebug`. Import a backup holding two widgets from apps installed on the phone (for example Google Clock and Breezy Weather). Open GraceLauncher's home screen (`adb shell am start -n com.galaxyrio.gracelauncher/.MainActivity`; no need to make it the default home app). Each widget's bind or setup screen appears in turn, and both end up in their saved spots. Deny the second: it is removed, and the next resume shows nothing.
- [ ] **Step 6: Commit.** Message: `Restore widgets from backup placeholders`.

### Task 6: Automatic backup

**Files:**
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/BackupStatusStore.kt`
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/data/backup/AutoBackup.kt`
- Modify: `app/src/main/java/com/galaxyrio/gracelauncher/MainActivity.kt` (`onResume`)
- Test: `app/src/test/java/com/galaxyrio/gracelauncher/data/backup/AutoBackupTest.kt`

**Interfaces:**
- Consumes: Task 4 (`BackupExporter.export`, `BackupExporter.contentHash`, `BackupLock`)
- Produces:
  - `class BackupStatusStore(context: Context)` over SharedPreferences `"backup_status"` (not in the backup, since the exporter copies only the two named files). Properties: `lastExportAtMillis: Long?`, `autoEnabled: Boolean`, `autoFolderUri: String?`, `lastAutoHash: String?`, `lastFailureAtMillis: Long?`, `lastFailureReason: String?`
  - `fun shouldAutoExport(enabled: Boolean, folderSet: Boolean, nowMillis: Long, lastSuccessMillis: Long?, lastHash: String?, currentHash: String): Boolean`: true only when enabled, the folder is set, it has been at least 86 400 000 ms since the last success (or there is none), and the hash differs
  - `fun manualBackupName(date: java.time.LocalDate): String` and `fun autoBackupName(date: java.time.LocalDate): String`
  - `fun autoFilesToDelete(names: List<String>, keep: Int = 7): List<String>`: only names matching `^gracelauncher-auto-\d{4}-\d{2}-\d{2}( \(\d+\))?\.zip$`, newest first by date then suffix, returning everything after the first `keep`
  - `class AutoBackupRunner(context: Context)` with `suspend fun runIfDue(labels: Map<String, String>)`. It uses `DocumentsContract` (`buildChildDocumentsUriUsingTree`, `createDocument`, `deleteDocument`) on the persisted tree URI. On `SecurityException` or a missing folder, it records `lastFailureReason = "folder not accessible"`.

- [ ] **Step 1: Write failing unit tests:**
  - `dueOnlyAfter24hAndChange`: false at 23h59m with a changed hash, false at 25h with the same hash, true at 25h with a changed hash, true with no previous success, and false when disabled or no folder is set.
  - `retentionKeepsNewestSevenAutoFiles`: 9 auto files, plus `gracelauncher-2026-10-01.zip`, `notes.txt`, and `gracelauncher-auto-old.zip`. The result is exactly the 2 oldest dated auto files.
  - `namesUseIsoDate`: `autoBackupName(LocalDate.of(2026, 10, 27)) == "gracelauncher-auto-2026-10-27.zip"` and `manualBackupName(...) == "gracelauncher-2026-10-27.zip"`.
- [ ] **Step 2: Run.** `./gradlew :app:testDebugUnitTest --tests 'com.galaxyrio.gracelauncher.data.backup.AutoBackupTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** In `MainActivity.onResume`, launch `AutoBackupRunner.runIfDue` on `Dispatchers.IO` from `lifecycleScope`, with at most one run in flight. Labels come from `launcherViewModel.uiState.value.apps`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** Message: `Add daily automatic backup to a chosen folder`.

### Task 7: Settings page

**Files:**
- Create: `app/src/main/java/com/galaxyrio/gracelauncher/ui/settings/BackupSettings.kt`
- Modify: `app/src/main/java/com/galaxyrio/gracelauncher/ui/settings/LauncherSettingsScreen.kt` (`SettingsPage` enum line 76, `SettingsHome` line 355, nav graph)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/com/galaxyrio/gracelauncher/BackupSettingsTest.kt`

**Interfaces:**
- Consumes: Tasks 4 and 6 (`BackupExporter`, `BackupImporter`, `ImportResult`, `ImportSummary`, `BackupStatusStore`, `manualBackupName`)
- Produces: `SettingsPage.Backup`, a root row in `SettingsHome` using the existing row pattern (`SettingsActionItem`) and an existing `LauncherSymbol`, and `@Composable fun BackupSettings(uiState: LauncherUiState, onBack: () -> Unit)`

Page contents, in order:
- Export backup (`CreateDocument("application/zip")`, default name `manualBackupName(today)`)
- Import backup (`OpenDocument(arrayOf("application/zip", "application/octet-stream"))`, then a confirmation dialog with "This replaces your current setup.", then the import, then the summary dialog)
- Last exported line
- Automatic backup switch
- Backup folder (`OpenDocumentTree` + `takePersistableUriPermission` with read and write)
- Failure line, shown only when `lastFailureAtMillis` is set and later than `lastExportAtMillis`

`installedAppKeys` comes from `uiState.apps`. `installedProviders` comes from `AppWidgetManager.getInstalledProvidersForProfile(myUserHandle())`. A `Rejected` result shows one plain sentence per `BackupProblem`, for example "This backup was made by a newer version of GraceLauncher. Update the app and try again." After `Success`, home picks up favorites and appearance through the existing `refreshOnResume`. Confirm this on the phone.

- [ ] **Step 1: Write failing Compose tests,** following `SettingsNavigationTest`:
  - `backupPageReachableFromRoot`: the root shows the Backup row, and tapping it shows "Export backup" and "Import backup".
  - `lastExportedShowsNeverThenDays`: with an empty `backup_status` the page shows "Never". With `lastExportAtMillis` 3 days ago it shows "Last exported: 3 days ago".
  - `failureLineOnlyAfterFailure`.
  - `summaryListsSkippedItems`: rendering the summary dialog for an `ImportSummary` with `notInstalled = ["Simple MBTA"]` and `widgetsMissingProvider = ["GeauxWeather"]` shows "Not installed yet (will appear once installed): Simple MBTA" and "Widgets that need setup: GeauxWeather".
- [ ] **Step 2: Run.** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.galaxyrio.gracelauncher.BackupSettingsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the page, the strings (plural resource `backup_last_exported_days`), and navigation. Run `PluralResourcesTest`.
- [ ] **Step 4: Run Task 7's tests, the full unit suite, and the full instrumented suite.** Expected: PASS, apart from failures already recorded in Task 0.
- [ ] **Step 5: Commit.** Message: `Add Backup and restore settings page`.

### Task 8: Niagara recording converter and the real-phone test

**Files (outside the repo; do not commit):**
- Create: `/Users/justin/Documents/gracelauncher-import-test/convert.py`
- Output: `/Users/justin/Documents/gracelauncher-import-test/gracelauncher-from-niagara.zip`

- [ ] **Step 1: Write `convert.py`** (Python 3.13, standard library only). It reads `niagara-home-2026-10-27.json` and `app/schemas/com.galaxyrio.gracelauncher.data.LauncherDatabase/27.json`. It creates a SQLite DB from the schema's `createSql` statements and its `room_master_table` identity hash, with `PRAGMA user_version = 27`.
  - **Folders:** one `folders` row per folder (id from the recording; placement and flags at the defaults in `LauncherFolderEntity`).
  - **Popups:** one `app_popups` row per folder (`ownerKey = "folder:<id>"`, `itemsJson` holding that folder's widgets first, each with `widgetId` 0, 1, 2, … and `widgetProvider` set, then its apps in order), plus one for `HomeContentKey` holding the Google search widget.
  - **Favorites:** written to `data/preferences.json` as `grace_launcher_preferences` (check the key name in `FavoritesStore.kt`).
  - **Manifest:** sections `Folders`, `Popups`, `Favorites` only.
  - **Skipped:** entries marked broken or in `bound_but_not_seen`.
- [ ] **Step 2: Install the debug build on the phone.** Run `./gradlew :app:installDebug`. The F-Droid build was already removed in Task 0. Do not make GraceLauncher the default home app; the user does that when they are ready.
- [ ] **Step 3: Real-phone test,** with the user. Import `gracelauncher-from-niagara.zip`.
  - **Favorites:** the 13 favorites appear in recorded order.
  - **Folders:** the 5 folders hold the recorded apps in recorded order.
  - **Widgets:** the 6 recorded widgets (Google search, Podcast Addict, Libby, GeauxWeather, Breezy Weather, the mobi.lockdown clock) come back after the bind and setup screens. Widgets marked "likely" are checked by eye.

## Done when

- **Tests:** every task's tests pass, along with the full unit and instrumented suites (apart from failures already recorded in Task 0).
- **Schema:** `app/schemas` is unchanged.
- **Real phone:** the user has confirmed the Task 8 real-phone test.
