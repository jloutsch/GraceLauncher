package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.AppCategory
import com.galaxyrio.gracelauncher.data.AppPopupEntity
import com.galaxyrio.gracelauncher.data.FavoritesCategoryId
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.ItemIconEntity
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.LauncherPreferences
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.renameValue
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenamedAppsPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "renamed-apps-test-${UUID.randomUUID()}.db"
    private val favoritesName = "renamed-apps-favorites-${UUID.randomUUID()}"
    private val database = Room.databaseBuilder(context, LauncherDatabase::class.java, databaseName).build()
    private val renames = mapOf("old/old.Main" to "old/old.Renamed")

    @After fun cleanUp() {
        database.close()
        context.deleteDatabase(databaseName)
        context.deleteSharedPreferences(favoritesName)
        context.getSharedPreferences("launcher_appearance", Context.MODE_PRIVATE).edit()
            .remove("label:old/old.Main").remove("label:old/old.Renamed").commit()
    }

    @Test fun renamesReplaceWholeKeysOnly() {
        assertEquals("old/old.Renamed", renameValue("old/old.Main", renames))
        assertEquals("old/old.Main2", renameValue("old/old.Main2", renames))
        val json = JSONObject().put("app", "old/old.Main").put("list", JSONArray(listOf("old/old.Main", "old/old.Mainx"))).toString()
        val renamed = JSONObject(renameValue(json, renames))
        assertEquals("old/old.Renamed", renamed.getString("app"))
        assertEquals(listOf("old/old.Renamed", "old/old.Mainx"), renamed.getJSONArray("list").let { a -> List(a.length()) { a.getString(it) } })
        // Unrelated JSON keeps its exact text.
        val other = """{"app":"other/other.Main"}"""
        assertEquals(other, renameValue(other, renames))
    }

    @Test fun renamedAppKeepsItsPlacesEverywhere() = runBlocking {
        val repository = LauncherSettingsRepository(database)
        val items = database.itemsDao()
        repository.mutateSettings {
            it.copy(mediaAppKey = "old/old.Main", appCategories = listOf(
                AppCategory(FavoritesCategoryId, itemKeys = listOf("old/old.Main", "kept/kept.Main"), showOnHome = true)))
        }
        repository.hideApp("old/old.Main")
        repository.saveFolder(LauncherFolder("f", "Folder", listOf("kept/kept.Main", "old/old.Main"), FolderPlacement.AppList))
        items.savePopup(AppPopupEntity("old/old.Main", PopupItem.encode(listOf(PopupItem("kept/kept.Main")))))
        items.saveIcon(ItemIconEntity("old/old.Main", """{"kind":"image"}"""))
        val prefs = context.getSharedPreferences(favoritesName, Context.MODE_PRIVATE)
        val favorites = FavoritesStore(prefs)
        val apps = listOf("old/old.Main", "kept/kept.Main").map { LauncherApp(ComponentName.unflattenFromString(it)!!, it, null) }
        favorites.toggle("old/old.Main", favorites.favoritesFor(apps).filter { it == "kept/kept.Main" })
        val preferences = LauncherPreferences(context)
        preferences.rename("old/old.Main", "My app")

        repository.renameApps(renames)
        favorites.renameApps(renames)
        preferences.renameApps(renames)

        val snapshot = repository.snapshots.first()
        assertEquals("old/old.Renamed", snapshot.settings.mediaAppKey)
        assertEquals(listOf("old/old.Renamed", "kept/kept.Main"), snapshot.settings.appCategories!!.single().itemKeys)
        assertEquals(setOf("old/old.Renamed"), snapshot.hiddenAppKeys)
        assertEquals(listOf("kept/kept.Main", "old/old.Renamed"), snapshot.folders.single().appKeys)
        assertNull(items.popup("old/old.Main"))
        assertEquals(listOf("kept/kept.Main"), PopupItem.decode(items.popup("old/old.Renamed")!!.itemsJson).map { it.key })
        assertEquals(listOf("old/old.Renamed"), items.readIcons().map { it.itemKey })
        assertEquals(listOf("kept/kept.Main", "old/old.Renamed"), favorites.savedFavorites())
        assertEquals(mapOf("old/old.Renamed" to "My app"), preferences.renames().filterKeys { it.startsWith("old/") })
    }
}
