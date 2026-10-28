package com.galaxyrio.gracelauncher.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.galaxyrio.gracelauncher.data.AppPopupEntity
import com.galaxyrio.gracelauncher.data.HiddenAppEntity
import com.galaxyrio.gracelauncher.data.ItemIconEntity
import com.galaxyrio.gracelauncher.data.LauncherFolderEntity
import com.galaxyrio.gracelauncher.data.LauncherSettingsEntity
import com.galaxyrio.gracelauncher.data.SavedShortcutEntity

/** Whole-table reads and replacements for backup files; ordered so copies compare equal. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM launcher_settings ORDER BY id") suspend fun allSettings(): List<LauncherSettingsEntity>
    @Query("DELETE FROM launcher_settings") suspend fun clearSettings()
    @Insert(onConflict = REPLACE) suspend fun insertSettings(rows: List<LauncherSettingsEntity>)

    @Query("SELECT * FROM hidden_apps ORDER BY appKey") suspend fun allHiddenApps(): List<HiddenAppEntity>
    @Query("DELETE FROM hidden_apps") suspend fun clearHiddenApps()
    @Insert(onConflict = REPLACE) suspend fun insertHiddenApps(rows: List<HiddenAppEntity>)

    @Query("SELECT * FROM folders ORDER BY id") suspend fun allFolders(): List<LauncherFolderEntity>
    @Query("DELETE FROM folders") suspend fun clearFolders()
    @Insert(onConflict = REPLACE) suspend fun insertFolders(rows: List<LauncherFolderEntity>)

    @Query("SELECT * FROM item_icons ORDER BY itemKey") suspend fun allItemIcons(): List<ItemIconEntity>
    @Query("DELETE FROM item_icons") suspend fun clearItemIcons()
    @Insert(onConflict = REPLACE) suspend fun insertItemIcons(rows: List<ItemIconEntity>)

    @Query("SELECT * FROM saved_shortcuts ORDER BY itemKey") suspend fun allSavedShortcuts(): List<SavedShortcutEntity>
    @Query("DELETE FROM saved_shortcuts") suspend fun clearSavedShortcuts()
    @Insert(onConflict = REPLACE) suspend fun insertSavedShortcuts(rows: List<SavedShortcutEntity>)

    @Query("SELECT * FROM app_popups ORDER BY ownerKey") suspend fun allPopups(): List<AppPopupEntity>
    @Query("DELETE FROM app_popups") suspend fun clearPopups()
    @Insert(onConflict = REPLACE) suspend fun insertPopups(rows: List<AppPopupEntity>)
}
