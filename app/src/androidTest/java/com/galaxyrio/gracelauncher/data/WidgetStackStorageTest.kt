package com.galaxyrio.gracelauncher.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Widget stacks use upstream's column; the fork's older stacks and weather app choice move into upstream's storage. */
@RunWith(AndroidJUnit4::class)
class WidgetStackStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "widget-stack-test-${UUID.randomUUID()}.db"
    private val database = Room.databaseBuilder(context, LauncherDatabase::class.java, databaseName).build()
    private val settings = LauncherSettingsRepository(database)
    private val items = LauncherItemsRepository(database)

    @After fun cleanUp() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun widgetStack(id: String = "ws") =
        LauncherFolder(id, "Widgets", emptyList(), FolderPlacement.Favorites, widgetStack = WidgetStackStyle())

    private suspend fun folder(id: String) = settings.snapshots.first().folders.single { it.id == id }

    @Test fun savingAWidgetStackMarksOnlyThatFolder() = runBlocking {
        settings.saveFolder(widgetStack())
        settings.saveFolder(LauncherFolder("apps", "Apps", emptyList(), FolderPlacement.Favorites, isAppStack = true))
        settings.saveFolder(LauncherFolder("plain", "Plain", emptyList(), FolderPlacement.AppList))

        val snapshot = settings.snapshots.first()
        assertEquals(mapOf("ws" to true, "apps" to false, "plain" to false), snapshot.folders.associate { it.id to it.isWidgetStack })
        assertFalse(folder("ws").isAppStack)
    }

    @Test fun widgetStackSurvivesFolderEditsAndUnrelatedSettings() = runBlocking {
        settings.saveFolder(widgetStack())
        settings.updateFolder("ws", name = "Renamed", placement = FolderPlacement.AppList)
        settings.setFolderFavorites(mapOf("ws" to true))
        settings.mutateSettings { it.copy(clockEnabled = !it.clockEnabled) }
        settings.mutateSettings { it.copy(shortcutsFolders = it.shortcutsFolders.copy(enabled = !it.shortcutsFolders.enabled)) }

        val stack = folder("ws")
        assertTrue(stack.isWidgetStack)
        assertEquals("Renamed", stack.name)
    }

    @Test fun forkWidgetStacksBecomeUpstreamWidgetStacksOnce() = runBlocking {
        // Before upstream's column, a fork stack was an app stack listed in shortcutsFoldersJson.
        settings.saveFolder(LauncherFolder("ws", "Widgets", emptyList(), FolderPlacement.Favorites, isAppStack = true))
        settings.saveFolder(LauncherFolder("apps", "Apps", emptyList(), FolderPlacement.Favorites, isAppStack = true))
        val members = listOf(PopupItem("a/a.Main"),
            PopupItem("widget:51", HomeLayout(widgetId = 51, widgetProvider = "w/w.Provider", widgetHeightDp = 180)),
            PopupItem("widget:52", HomeLayout(widgetId = 52, widgetProvider = "w/w.Provider", widgetHeightDp = 320)))
        database.itemsDao().savePopup(AppPopupEntity("folder:ws", PopupItem.encode(members)))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE launcher_settings SET shortcutsFoldersJson = '{\"widgetStackFolderIds\":[\"ws\"]}'")

        settings.migrateWidgetStackBackgrounds()

        val stack = folder("ws")
        assertEquals(WidgetStackStyle(320), stack.widgetStack)
        assertFalse(stack.isAppStack)
        assertTrue(folder("apps").isAppStack)
        assertNull(folder("apps").widgetStack)
        // Members, apps included, are kept; the old list is cleared so the conversion runs only once.
        assertEquals(members.map { it.key }, PopupItem.decode(database.itemsDao().popup("folder:ws")!!.itemsJson).map { it.key })
        assertEquals(emptySet<String>(), stringSetField(database.settingsDao().readSettings()!!.shortcutsFoldersJson, WidgetStackFoldersField))
    }

    @Test fun widgetStacksKeepWidgetsAndAppsAndAppStacksKeepOnlyApps() = runBlocking {
        settings.saveFolder(widgetStack())
        settings.saveFolder(LauncherFolder("apps", "Apps", emptyList(), FolderPlacement.Favorites, isAppStack = true))
        val mixed = listOf(PopupItem("a/a.Main"), PopupItem("folder:f9"),
            PopupItem("widget:51", HomeLayout(widgetId = 51, widgetProvider = "w/w.Provider")))

        items.updatePopup("folder:ws", emptyList()) { mixed }
        items.updatePopup("folder:apps", emptyList()) { mixed }

        fun stored(key: String) = runBlocking { PopupItem.decode(database.itemsDao().popup(key)!!.itemsJson).map { it.key } }
        assertEquals(listOf("a/a.Main", "widget:51"), stored("folder:ws"))
        assertEquals(listOf("a/a.Main"), stored("folder:apps"))
    }

    @Test fun forkWeatherAppMovesIntoUpstreamsWeatherSetting() = runBlocking {
        settings.mutateSettings { it }
        // Where the fork kept its own weather app choice before upstream added one.
        database.openHelper.writableDatabase.execSQL("UPDATE launcher_settings SET clockStyleJson = '{\"weatherAppKey\":\"w/w.Weather\"}'")
        assertEquals("w/w.Weather", settings.snapshots.first().settings.weatherConnection.appKey)

        // The next save stores it in upstream's setting, so replacing the clock style keeps it.
        settings.mutateSettings { it.copy(clockStyle = ClockStyle.decode(null, legacyShowBattery = false)) }
        assertEquals("w/w.Weather", settings.snapshots.first().settings.weatherConnection.appKey)

        // A choice made in upstream's setting wins, and clearing it is not undone by the old value.
        settings.mutateSettings { it.copy(weatherConnection = it.weatherConnection.copy(appKey = null)) }
        assertNull(settings.snapshots.first().settings.weatherConnection.appKey)
    }
}
