package com.galaxyrio.gracelauncher.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.GraceWidget
import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.LauncherItemsRepository
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.WidgetScreenKey
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRestoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "widget-restore-${UUID.randomUUID()}.db"
    private val database = Room.databaseBuilder(context, LauncherDatabase::class.java, databaseName)
        .addMigrations(*LauncherDatabase.Migrations).build()
    private val settings = LauncherSettingsRepository(database)
    private val items = LauncherItemsRepository(database)
    private val provider = "com.example/.ClockWidget"

    @After
    fun cleanUp() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun widget(id: Int) = PopupItem("widget:$id", HomeLayout(widgetId = id, widgetProvider = provider, widgetHeightDp = 180))

    @Test
    fun resolveReplacesPlaceholderEverywhere() = runBlocking {
        settings.updateSettings(LauncherSettings(homeLayout = HomeLayout(homeOrder = listOf(GraceWidget.Clock.key, "widget:-2"))))
        settings.saveFolder(LauncherFolder("f1", "Work", listOf("a/a.Main"), FolderPlacement.AppList))
        items.updatePopup(HomeContentKey, emptyList()) { listOf(widget(-2)) }
        items.updatePopup("folder:f1", emptyList()) { listOf(PopupItem("a/a.Main"), widget(-3)) }
        val restore = WidgetRestoreRepository(database)
        assertEquals(listOf(PendingWidget(HomeContentKey, -2, provider), PendingWidget("folder:f1", -3, provider)), restore.pending())

        restore.resolve(-2, 120)
        assertEquals(listOf(PopupItem("widget:120", HomeLayout(widgetId = 120, widgetProvider = provider, widgetHeightDp = 180))),
            items.snapshots.first().popups[HomeContentKey])
        assertEquals(listOf(GraceWidget.Clock.key, "widget:120"), settings.snapshots.first().settings.homeLayout.homeOrder)

        restore.resolve(-3, null)
        assertEquals(listOf(PopupItem("a/a.Main")), items.snapshots.first().popups["folder:f1"])
        assertEquals(emptyList<PendingWidget>(), restore.pending())
    }

    @Test
    fun placeholdersNeverReachWidgetHost() = runBlocking {
        settings.saveFolder(LauncherFolder("f1", "Work", listOf("a/a.Main"), FolderPlacement.AppList))
        items.updatePopup("folder:f1", emptyList()) { listOf(PopupItem("a/a.Main"), widget(-2), widget(7)) }
        // The same repository call and guard LauncherViewModel.updatePopup uses.
        val removed = items.updatePopup("folder:f1", emptyList()) { current -> current.filter { it.widget == null } }
        assertEquals(listOf(-2, 7), removed)
        assertEquals(listOf(7), widgetIdsToDelete(removed))

        items.updatePopup("folder:f1", emptyList()) { listOf(widget(-3), widget(8)) }
        assertEquals(listOf(8), widgetIdsToDelete(settings.deleteFolder("f1")))
    }

    @Test
    fun placeholdersAreHiddenFromLauncherSurfaces() {
        val stored = mapOf(
            HomeContentKey to listOf(widget(-2), widget(5)),
            "folder:f1" to listOf(PopupItem("a/a.Main"), widget(-3)),
            WidgetScreenKey to listOf(widget(-4)),
        )
        val visible = stored.withoutWidgetPlaceholders()
        assertEquals(mapOf(HomeContentKey to listOf(widget(5)), "folder:f1" to listOf(PopupItem("a/a.Main")),
            WidgetScreenKey to emptyList()), visible)
        val state = LauncherUiState(popups = visible,
            settings = LauncherSettings(homeLayout = HomeLayout(homeOrder = listOf("widget:-2", "widget:5"))))
        assertEquals(listOf("widget:5"), state.homeItems.filter { it.widget != null }.map { it.key })
    }
}
