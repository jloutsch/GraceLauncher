package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchivedAppsTest {
    private fun app(id: String, label: String = id, archived: Boolean = false) =
        LauncherApp(ComponentName("fixture.$id", "Main"), label, null, isArchived = archived)

    @Test fun archivedAppsLeaveEveryListButRemainInSearch() {
        val archived = app("archived", archived = true)
        val visible = app("visible")
        val folder = LauncherFolder("f", "Folder", listOf(archived.key, visible.key), FolderPlacement.None)
        val category = AppCategory("cat", inclusion = CategoryInclusion.Manual, itemKeys = listOf(archived.key, visible.key))
        val state = LauncherUiState(apps = listOf(archived, visible), isLoadingApps = false, folders = listOf(folder),
            favoriteKeys = setOf(archived.key, visible.key), favoriteOrder = listOf(archived.key, visible.key),
            settings = LauncherSettings(appCategories = listOf(category)))

        assertEquals(listOf(visible.key), state.appListApps.map { it.key })
        assertEquals(listOf(visible.key), state.favoriteApps.map { it.key })
        assertEquals(listOf(visible.key), state.categoryItems(category))
        assertEquals(listOf(visible.key), state.popupItems(visible.copy(folderId = folder.id), emptyList()).map { it.key })
        // Search reads allApps; archived apps must stay there.
        assertTrue(state.allApps.any { it.key == archived.key })
    }

    @Test fun unarchivedAppReturnsToTheList() {
        val app = app("restored")
        val state = LauncherUiState(apps = listOf(app), isLoadingApps = false)
        assertEquals(listOf(app.key), state.appListApps.map { it.key })
    }
}
