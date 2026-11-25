package com.galaxyrio.gracelauncher.data

import androidx.room.withTransaction
import com.galaxyrio.gracelauncher.data.weather.WeatherConnectionPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

/**
 * Launcher settings and app categories live in Room. Legacy favorites migrate
 * once; profile pins, renames and appearance preferences keep their existing stores.
 * All reads are observable Room queries and every write is a suspending DAO
 * operation, so no database access blocks the main thread.
 */
class LauncherSettingsRepository(private val database: LauncherDatabase) {
    private val dao = database.settingsDao()

    val snapshots: Flow<LauncherStorageSnapshot> = combine(
        dao.observeSettings(),
        dao.observeHiddenApps(),
        dao.observeFolders(),
        database.itemsDao().popups(),
    ) { settings, hiddenApps, folders, popups ->
        val contents = popups.associate { it.ownerKey to PopupItem.decode(it.itemsJson) }
        val loaded = settings?.toSettings() ?: LauncherSettings()
        LauncherStorageSnapshot(
            settings = loaded,
            hiddenAppKeys = hiddenApps.toSet(),
            folders = folders.map { entry ->
                LauncherFolder(
                    id = entry.id,
                    name = entry.name,
                    appKeys = contents["folder:${entry.id}"].orEmpty().filter { it.widget == null }.map { it.key },
                    placement = FolderPlacement.decode(entry.placement),
                    appListAtBottom = entry.appListAtBottom,
                    autoHideApps = entry.autoHideApps,
                    isAppStack = entry.isAppStack,
                    widgetStack = WidgetStackStyle.decode(entry.widgetStackJson),
                )
            },
        )
    }.distinctUntilChanged()

    suspend fun updateSettings(settings: LauncherSettings) {
        // The raw hideFavoriteNames flag is stored untouched; hidden-icon safety is
        // derived on read so the user's preference survives a Hidden round-trip.
        dao.saveSettings(LauncherSettingsEntity(
            clockEnabled = settings.clockEnabled,
            calendarAboveClock = settings.calendarAboveClock,
            mediaAlwaysVisible = settings.mediaAlwaysVisible,
            mediaKeepWhilePlaying = settings.mediaKeepWhilePlaying,
            dismissResistance = settings.dismissResistance.coerceIn(0, 90),
            dismissThresholdDp = settings.dismissThresholdDp.coerceIn(24, 160),
            mediaAppKey = settings.mediaAppKey,
            mediaStyleJson = settings.mediaStyle.encode(),
            graceButtonJson = settings.graceButton.encode(),
            calendarAgenda = settings.calendarAgenda,
            showBatteryPercentage = settings.showBatteryPercentage,
            allowHapticFeedback = settings.allowHapticFeedback,
            allowScreenRotation = settings.allowScreenRotation,
            rememberScreenPosition = settings.rememberScreenPosition,
            useDynamicColors = settings.useDynamicColors,
            themeColor = settings.themeColor,
            paletteStyle = settings.paletteStyle.name,
            darkMode = settings.darkMode.name,
            amoledMode = settings.amoledMode,
            iconPackPackage = settings.iconPackPackage,
            iconDesignJson = settings.iconDesign?.encode(),
            iconPackPackagesJson = normalizeIconPackOrder(settings.iconPackPackages).takeIf { it.isNotEmpty() }
                ?.let { JSONArray(it).toString() },
            mediaPlayer = settings.mediaPlayer,
            weatherEnabled = settings.weatherEnabled,
            weatherForecastDays = settings.weatherForecastDays.coerceIn(1, 14),
            weatherLocationId = settings.weatherLocationId,
            weatherConnectionJson = settings.weatherConnection.encode(),
            clockAppKey = settings.clockAppKey,
            clockStyleJson = settings.clockStyle.encode(),
            agendaAppearanceJson = settings.agendaAppearance.encode(),
            homeLayoutJson = settings.homeLayout.encode(),
            hideStatusBar = settings.hideStatusBar,
            hideAlphabet = settings.hideAlphabet,
            hideFavoriteNames = settings.hideFavoriteNames,
            dimWallpaper = settings.dimWallpaper,
            wallpaperDimAmount = settings.wallpaperDimAmount.coerceIn(0, 100),
            blurWallpaper = settings.blurWallpaper,
            wallpaperBlurRadius = settings.wallpaperBlurRadius.coerceIn(0, 48),
            appFontId = settings.appFontId,
            fontLibraryJson = settings.fontLibrary.encode(),
            fontColorsJson = settings.fontColors.encode(),
            fontShadowJson = settings.fontShadow.encode(),
            listAppearanceJson = settings.listAppearance.encode(),
            applyFontToSettings = settings.applyFontToSettings,
            privateSpaceJson = settings.privateSpace.encode(),
            workProfileJson = settings.workProfile.encode(),
            homeGesturesJson = settings.homeGestures.encode(),
            searchJson = settings.search.encode(),
            shortcutsFoldersJson = settings.shortcutsFolders.encode(),
            digitalWellbeingJson = settings.digitalWellbeing.encode(),
            widgetScreenJson = settings.widgetScreen.encode(),
            appCategoriesJson = settings.appCategories?.let(AppCategory::encode),
            preferNonAccessibility = settings.preferNonAccessibility,
            calendarPreferencesJson = settings.calendar.encode(),
        ))
    }

    /** Commit presentation and its widget together; only then release IDs no surface still owns. */
    suspend fun saveAgendaAppearance(style: AgendaAppearance, widget: HomeLayout?): List<Int> = database.withTransaction {
        val items = database.itemsDao()
        val previous = listOf(AgendaWidgetKey, AgendaWidgetDraftKey).flatMap { key ->
            items.popup(key)?.let { PopupItem.decode(it.itemsJson) }.orEmpty()
        }.mapNotNull { it.widget?.widgetId }.toSet()
        val appearance = style.normalized()
        appearance.widgetStackKey?.let { key ->
            require(dao.folder(key.removePrefix("folder:"))?.widgetStackJson != null) { "Widget stack no longer exists" }
        }
        val selected = if (appearance.widgetStackKey != null) null else widget?.takeIf { it.hasWidget }
        require(selected == null || selected.widgetId in previous)
        val current = dao.readSettings()?.toSettings() ?: LauncherSettings()
        // An appearance draft must not reset the last action chosen on the agenda itself.
        updateSettings(current.copy(agendaAppearance = appearance.copy(
            lastCreateAction = current.agendaAppearance.lastCreateAction)))
        if (selected == null) items.deletePopup(AgendaWidgetKey)
        else items.savePopup(AppPopupEntity(AgendaWidgetKey,
            PopupItem.encode(listOf(PopupItem("widget:${selected.widgetId}", selected)))))
        items.deletePopup(AgendaWidgetDraftKey)
        val retained = items.readPopups().flatMap { PopupItem.decode(it.itemsJson) }.mapNotNull { it.widget?.widgetId }.toSet() +
            current.homeLayout.widgetId
        previous.filterNot { it in retained }
    }

    suspend fun discardAgendaWidgetDraft(): List<Int> = database.withTransaction {
        val items = database.itemsDao()
        val removed = items.popup(AgendaWidgetDraftKey)?.let { PopupItem.decode(it.itemsJson) }.orEmpty()
            .mapNotNull { it.widget?.widgetId }
        items.deletePopup(AgendaWidgetDraftKey)
        val retained = items.readPopups().flatMap { PopupItem.decode(it.itemsJson) }.mapNotNull { it.widget?.widgetId }.toSet() +
            (dao.readSettings()?.toSettings()?.homeLayout?.widgetId ?: -1)
        removed.filterNot { it in retained }
    }

    /** Read-modify-write is atomic even when a UI emission has not arrived yet. */
    suspend fun mutateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        database.withTransaction {
            val current = dao.readSettings()?.toSettings() ?: LauncherSettings()
            updateSettings(synchronizeGraceWidgetOrder(current, synchronizeCategoryPlacement(current, transform(current))))
        }
    }

    suspend fun migrateAppCategories(favorites: List<String>) = database.withTransaction {
        val current = dao.readSettings()?.toSettings() ?: LauncherSettings()
        if (current.appCategories == null) updateSettings(migrateHomeCategories(current, favorites))
    }

    suspend fun mutateAppCategories(favorites: List<String>, transform: (List<AppCategory>) -> List<AppCategory>) =
        database.withTransaction {
            val current = migrateHomeCategories(dao.readSettings()?.toSettings() ?: LauncherSettings(), favorites)
            val categories = transform(requireNotNull(current.appCategories))
            updateSettings(synchronizeCategoryPlacement(current, current.copy(appCategories = categories)))
        }

    /** Move the old single widget into the mixed home collection without rebinding it. */
    suspend fun migrateLegacyHomeWidget() = database.withTransaction {
        val current = dao.readSettings()?.toSettings() ?: LauncherSettings()
        if (current.homeLayout.hasWidget) {
            val layout = current.homeLayout
            val items = database.itemsDao()
            val previous = items.popup(HomeContentKey)?.let { PopupItem.decode(it.itemsJson) }.orEmpty()
            val widget = PopupItem("widget:${layout.widgetId}", layout.copy(homeOrder = null))
            items.savePopup(AppPopupEntity(HomeContentKey, PopupItem.encode((listOf(widget) + previous).distinctBy { it.key })))
            updateSettings(current.copy(homeLayout = layout.withoutWidget().copy(
                homeOrder = layout.homeOrder ?: legacyHomeOrder(current))))
        }
    }

    suspend fun setHiddenApps(keys: Set<String>) {
        // This changes app-list visibility only; other surfaces and memberships are unaffected.
        dao.replaceHiddenApps(keys.filter(String::isNotBlank).map(::HiddenAppEntity))
    }

    suspend fun hideApp(key: String) {
        if (key.isNotBlank()) dao.hideApp(HiddenAppEntity(key))
    }

    /** Move the retired stack-wide background onto unchanged members before the first edit/read. */
    suspend fun migrateWidgetStackBackgrounds() = database.withTransaction {
        migrateForkWidgetStacks()
        val items = database.itemsDao()
        dao.readFolders().forEach { folder ->
            val encoded = folder.widgetStackJson ?: return@forEach
            val legacy = runCatching { JSONObject(encoded) }.getOrNull() ?: return@forEach
            if (!legacy.has("backgroundColor") && !legacy.has("backgroundAlpha")) return@forEach
            val popup = items.popup("folder:${folder.id}")
            // Work on the stored JSON so unrelated fields, identities and order survive untouched.
            val members = if (popup == null) JSONArray() else
                runCatching { JSONArray(popup.itemsJson) }.getOrNull() ?: return@forEach
            val changed = migrateWidgetStackMemberBackgrounds(members, legacy)
            if (changed && popup != null) items.savePopup(popup.copy(itemsJson = members.toString()))
            // This same transaction marks the conversion complete, including empty stacks.
            dao.upsertFolder(folder.copy(widgetStackJson = requireNotNull(WidgetStackStyle.decode(encoded)).encode()))
        }
    }

    /**
     * Fork stacks predate upstream's column: they were app stacks whose ids were listed in shortcutsFoldersJson.
     * Convert them once, keeping their members (widgets and apps) and the height their widgets were given.
     */
    private suspend fun migrateForkWidgetStacks() {
        val row = dao.readSettings() ?: return
        val ids = stringSetField(row.shortcutsFoldersJson, WidgetStackFoldersField)
        if (ids.isEmpty()) return
        val items = database.itemsDao()
        dao.readFolders().filter { it.id in ids && it.widgetStackJson == null }.forEach { folder ->
            val height = items.popup("folder:${folder.id}")?.let { PopupItem.decode(it.itemsJson) }.orEmpty()
                .maxOfOrNull { it.widget?.widgetHeightDp ?: 0 }?.takeIf { it > 0 }
            dao.upsertFolder(folder.copy(isAppStack = false, autoHideApps = false,
                widgetStackJson = (height?.let { WidgetStackStyle(it) } ?: WidgetStackStyle()).encode()))
        }
        dao.saveSettings(row.copy(shortcutsFoldersJson = row.shortcutsFoldersJson?.withField(WidgetStackFoldersField, null)))
    }

    suspend fun saveFolder(folder: LauncherFolder) {
        require(folder.id.isNotBlank()) { "A folder must have a stable id" }
        require(!folder.isAppStack || !folder.isWidgetStack) { "A group cannot be both an app and widget stack" }
        val name = folder.name.trim()
        require(name.isNotEmpty()) { "A folder name must not be blank" }
        val keys = if (folder.isWidgetStack) emptyList() else folder.appKeys.filter(String::isNotBlank).distinct()
        database.withTransaction {
            migrateWidgetStackBackgrounds()
            dao.upsertFolder(LauncherFolderEntity(folder.id, name, folder.placement.name, folder.appListAtBottom,
                folder.autoHideApps && !folder.isWidgetStack, folder.isAppStack, folder.widgetStack?.encode()))
            val itemsDao = database.itemsDao()
            val previous = itemsDao.popup(folder.key)?.let { PopupItem.decode(it.itemsJson) }.orEmpty()
            // Membership callers must not discard widgets or move them to the end.
            val remaining = keys.toMutableList()
            val next = previous.mapNotNull { item ->
                if (item.widget != null) item else remaining.firstOrNull()?.let { remaining.removeAt(0); PopupItem(it) }
            } + remaining.map { PopupItem(it) }
            itemsDao.savePopup(AppPopupEntity(folder.key, PopupItem.encode(next)))
        }
    }

    /** Style edits must neither restore a deleted stack nor overwrite newer placement/name edits. */
    suspend fun updateWidgetStackStyle(id: String, style: WidgetStackStyle) = database.withTransaction {
        migrateWidgetStackBackgrounds()
        val folder = checkNotNull(dao.folder(id)?.takeIf { it.widgetStackJson != null }) { "Widget stack no longer exists" }
        dao.upsertFolder(folder.copy(widgetStackJson = style.encode()))
    }

    suspend fun updateFolder(id: String, name: String? = null, placement: FolderPlacement? = null,
        appListAtBottom: Boolean? = null, autoHideApps: Boolean? = null) = database.withTransaction {
        val folder = dao.folder(id) ?: return@withTransaction
        dao.upsertFolder(folder.copy(name = name?.trim()?.takeIf { it.isNotEmpty() } ?: folder.name,
            placement = placement?.name ?: folder.placement, appListAtBottom = appListAtBottom ?: folder.appListAtBottom,
            autoHideApps = autoHideApps ?: folder.autoHideApps))
    }

    /** The favorites picker edits the same placement as the folder's settings. */
    suspend fun setFolderFavorites(selection: Map<String, Boolean>) = database.withTransaction {
        selection.forEach { (id, favorite) ->
            val folder = dao.folder(id) ?: return@forEach
            // The favorites picker must not change app-list visibility or sorting.
            val placement = FolderPlacement.decode(folder.placement).withFavorites(favorite)
            dao.upsertFolder(folder.copy(placement = placement.name))
        }
    }

    suspend fun deleteFolder(id: String): List<Int> = database.withTransaction {
        val key = "folder:$id"
        val itemsDao = database.itemsDao()
        val widgets = itemsDao.popup(key)?.let { PopupItem.decode(it.itemsJson) }.orEmpty().mapNotNull { it.widget?.widgetId }
        itemsDao.deletePopup(key)
        // Stacks can be referenced by several surfaces. Remove references, not their apps.
        itemsDao.readPopups().forEach { popup ->
            val items = PopupItem.decode(popup.itemsJson)
            if (items.any { it.key == key }) itemsDao.savePopup(popup.copy(itemsJson = PopupItem.encode(items.filterNot { it.key == key })))
        }
        itemsDao.resetIcon(key)
        dao.deleteFolder(id)
        val settings = dao.readSettings()?.toSettings()
        if (settings != null) updateSettings(settings.copy(
            appCategories = settings.appCategories?.map { it.copy(itemKeys = it.itemKeys - key) },
            agendaAppearance = if (settings.agendaAppearance.widgetStackKey == key)
                settings.agendaAppearance.copy(widgetStackKey = null) else settings.agendaAppearance,
        ))
        val retained = itemsDao.readPopups().flatMap { PopupItem.decode(it.itemsJson) }
            .mapNotNull { it.widget?.widgetId }.toSet() + (settings?.homeLayout?.widgetId ?: -1)
        widgets.filterNot { it in retained }
    }

    /** Moves saved references to apps whose launcher activity an update renamed ([renamedAppKeys]). */
    suspend fun renameApps(renames: Map<String, String>) = database.withTransaction {
        fun key(value: String) = renameValue(value, renames)
        fun json(value: String?) = value?.let(::key)
        dao.readSettings()?.let {
            dao.saveSettings(it.copy(iconDesignJson = json(it.iconDesignJson), mediaAppKey = json(it.mediaAppKey),
                graceButtonJson = json(it.graceButtonJson), clockAppKey = json(it.clockAppKey), clockStyleJson = json(it.clockStyleJson),
                weatherConnectionJson = WeatherConnectionPreferences.decode(it.weatherConnectionJson).let { weather ->
                    weather.copy(appKey = weather.appKey?.let(::key)).encode()
                },
                homeLayoutJson = json(it.homeLayoutJson), listAppearanceJson = json(it.listAppearanceJson),
                privateSpaceJson = json(it.privateSpaceJson), workProfileJson = json(it.workProfileJson),
                homeGesturesJson = json(it.homeGesturesJson), searchJson = json(it.searchJson),
                shortcutsFoldersJson = json(it.shortcutsFoldersJson), digitalWellbeingJson = json(it.digitalWellbeingJson),
                widgetScreenJson = json(it.widgetScreenJson), appCategoriesJson = json(it.appCategoriesJson)))
        }
        dao.replaceHiddenApps(dao.readHiddenApps().map(::key).distinct().map(::HiddenAppEntity))
        val items = database.itemsDao()
        items.readIcons().filter { key(it.itemKey) != it.itemKey }.forEach {
            items.resetIcon(it.itemKey)
            items.saveIcon(it.copy(itemKey = key(it.itemKey)))
        }
        items.readPopups().forEach { popup ->
            val renamed = AppPopupEntity(key(popup.ownerKey), key(popup.itemsJson))
            if (renamed == popup) return@forEach
            if (renamed.ownerKey != popup.ownerKey) items.deletePopup(popup.ownerKey)
            items.savePopup(renamed)
        }
    }
}

private fun LauncherSettingsEntity.toSettings() = LauncherSettings(
    clockEnabled = clockEnabled,
    calendarAboveClock = calendarAboveClock,
    mediaAlwaysVisible = mediaAlwaysVisible,
    mediaKeepWhilePlaying = mediaKeepWhilePlaying,
    dismissResistance = dismissResistance.coerceIn(0, 90),
    dismissThresholdDp = dismissThresholdDp.coerceIn(24, 160),
    mediaAppKey = mediaAppKey,
    mediaStyle = MediaPlayerStyle.decode(mediaStyleJson),
    graceButton = GraceButtonSettings.decode(graceButtonJson),
    calendarAgenda = calendarAgenda,
    showBatteryPercentage = showBatteryPercentage,
    allowHapticFeedback = allowHapticFeedback,
    allowScreenRotation = allowScreenRotation,
    rememberScreenPosition = rememberScreenPosition,
    useDynamicColors = useDynamicColors,
    themeColor = themeColor,
    paletteStyle = decodePaletteStyle(paletteStyle),
    darkMode = ThemeMode.entries.firstOrNull { it.name == darkMode } ?: ThemeMode.System,
    amoledMode = amoledMode,
    iconPackPackage = iconPackPackage,
    iconDesign = iconDesignJson?.let(ItemIcon::decode),
    iconPackPackages = decodeIconPackOrder(iconPackPackagesJson),
    mediaPlayer = mediaPlayer,
    weatherEnabled = weatherEnabled,
    weatherForecastDays = weatherForecastDays.coerceIn(1, 14),
    weatherLocationId = weatherLocationId,
    // The fork's own weather app choice predates upstream's; carry it over until the user picks one there.
    weatherConnection = WeatherConnectionPreferences.decode(weatherConnectionJson).let { weather ->
        if (weather.appKey != null) weather else weather.copy(appKey = stringField(clockStyleJson, WeatherAppKeyField))
    },
    clockAppKey = clockAppKey,
    clockStyle = ClockStyle.decode(clockStyleJson, legacyShowBattery = showBatteryPercentage),
    agendaAppearance = AgendaAppearance.decode(agendaAppearanceJson),
    homeLayout = HomeLayout.decode(homeLayoutJson),
    hideStatusBar = hideStatusBar,
    hideAlphabet = hideAlphabet,
    hideFavoriteNames = hideFavoriteNames,
    dimWallpaper = dimWallpaper,
    wallpaperDimAmount = wallpaperDimAmount.coerceIn(0, 100),
    blurWallpaper = blurWallpaper,
    wallpaperBlurRadius = wallpaperBlurRadius.coerceIn(0, 48),
    appFontId = appFontId,
    fontLibrary = FontLibrary.decode(fontLibraryJson, appFontId),
    fontColors = FontColors.decode(fontColorsJson),
    fontShadow = FontShadow.decode(fontShadowJson),
    listAppearance = ListAppearance.decode(listAppearanceJson, hideFavoriteNames),
    applyFontToSettings = applyFontToSettings,
    privateSpace = ProfileSettings.decode(privateSpaceJson),
    workProfile = ProfileSettings.decode(workProfileJson, ProfileSettings.workDefaults()),
    homeGestures = GraceButtonSettings.decode(homeGesturesJson, GraceButtonSettings.homeDefaults()),
    search = SearchSettings.decode(searchJson),
    shortcutsFolders = ShortcutsFoldersSettings.decode(shortcutsFoldersJson),
    digitalWellbeing = DigitalWellbeingSettings.decode(digitalWellbeingJson),
    widgetScreen = WidgetScreenSettings.decode(widgetScreenJson),
    appCategories = AppCategory.decode(appCategoriesJson),
    preferNonAccessibility = preferNonAccessibility,
    calendar = CalendarPreferences.decode(calendarPreferencesJson),
)

private fun decodeIconPackOrder(json: String?): List<String> = runCatching {
    if (json == null) emptyList() else JSONArray(json).let { array ->
        normalizeIconPackOrder((0 until array.length()).mapNotNull { index ->
            (array.opt(index) as? String)?.takeIf(String::isNotBlank)
        })
    }
}.getOrDefault(emptyList())
