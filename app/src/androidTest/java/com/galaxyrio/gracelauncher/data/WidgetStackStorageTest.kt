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

/** Widget stacks and the weather app are stored in settings JSON, not in columns of their own. */
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
        LauncherFolder(id, "Widgets", emptyList(), FolderPlacement.Favorites, isAppStack = true, isWidgetStack = true)

    private suspend fun folder(id: String) = settings.snapshots.first().folders.single { it.id == id }

    @Test fun savingAWidgetStackMarksOnlyThatFolder() = runBlocking {
        settings.saveFolder(widgetStack())
        settings.saveFolder(LauncherFolder("apps", "Apps", emptyList(), FolderPlacement.Favorites, isAppStack = true))
        settings.saveFolder(LauncherFolder("plain", "Plain", emptyList(), FolderPlacement.AppList))

        val snapshot = settings.snapshots.first()
        assertEquals(mapOf("ws" to true, "apps" to false, "plain" to false), snapshot.folders.associate { it.id to it.isWidgetStack })
        assertEquals(setOf("ws"), snapshot.settings.widgetStackFolderIds)
        assertTrue(folder("ws").isAppStack)
    }

    @Test fun savingTheFolderAsAnAppStackClearsTheMark() = runBlocking {
        settings.saveFolder(widgetStack())
        settings.saveFolder(widgetStack().copy(isWidgetStack = false))

        assertFalse(folder("ws").isWidgetStack)
        assertEquals(emptySet<String>(), settings.snapshots.first().settings.widgetStackFolderIds)
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

    @Test fun deletingAWidgetStackForgetsIt() = runBlocking {
        settings.saveFolder(widgetStack())
        settings.deleteFolder("ws")
        assertEquals(emptySet<String>(), settings.snapshots.first().settings.widgetStackFolderIds)

        // A new folder that reuses the id starts as a normal folder.
        settings.saveFolder(LauncherFolder("ws", "Plain", emptyList(), FolderPlacement.AppList))
        assertFalse(folder("ws").isWidgetStack)
    }

    @Test fun widgetStacksKeepOnlyWidgetsAndAppStacksKeepOnlyApps() = runBlocking {
        settings.saveFolder(widgetStack())
        settings.saveFolder(LauncherFolder("apps", "Apps", emptyList(), FolderPlacement.Favorites, isAppStack = true))
        val mixed = listOf(PopupItem("a/a.Main"), PopupItem("folder:f9"),
            PopupItem("widget:51", HomeLayout(widgetId = 51, widgetProvider = "w/w.Provider")))

        items.updatePopup("folder:ws", emptyList()) { mixed }
        items.updatePopup("folder:apps", emptyList()) { mixed }

        fun stored(key: String) = runBlocking { PopupItem.decode(database.itemsDao().popup(key)!!.itemsJson).map { it.key } }
        assertEquals(listOf("widget:51"), stored("folder:ws"))
        assertEquals(listOf("a/a.Main"), stored("folder:apps"))
    }

    @Test fun weatherAppSurvivesClockStyleChangesAndCanBeCleared() = runBlocking {
        settings.mutateSettings { it.copy(weatherAppKey = "w/w.Weather") }
        // The clock editor replaces the whole clock style.
        settings.mutateSettings { it.copy(clockStyle = ClockStyle.decode(null, legacyShowBattery = false)) }
        assertEquals("w/w.Weather", settings.snapshots.first().settings.weatherAppKey)

        settings.mutateSettings { it.copy(weatherAppKey = null) }
        assertNull(settings.snapshots.first().settings.weatherAppKey)
    }
}
