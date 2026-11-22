package com.galaxyrio.gracelauncher.data.backup

import androidx.room.withTransaction
import com.galaxyrio.gracelauncher.data.AppPopupEntity
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.PopupItem

/** The saved queue of imported widgets still waiting for a real host ID; it survives process death. */
class WidgetRestoreRepository(private val database: LauncherDatabase) {
    suspend fun pending(): List<PendingWidget> = database.withTransaction { layout().pending() }

    /** Removes the placeholder's slot: the user cancelled its setup. */
    suspend fun drop(placeholderId: Int) = database.withTransaction {
        val before = layout()
        save(before, before.dropWidget(placeholderId))
    }

    /**
     * Binds the placeholder to [newId]. Returns false, changing nothing, when the slot is gone or was saved for another
     * widget than [provider]; the caller then deletes [newId].
     */
    suspend fun resolve(placeholderId: Int, newId: Int, provider: String): Boolean = database.withTransaction {
        val before = layout()
        if (!before.accepts(placeholderId, provider)) return@withTransaction false
        save(before, before.replaceWidget(placeholderId, newId))
        true
    }

    private suspend fun save(before: WidgetLayout, after: WidgetLayout) {
        val dao = database.backupDao()
        val changed = after.popups.filter { (owner, items) -> items != before.popups[owner] }
        if (changed.isNotEmpty()) dao.insertPopups(changed.map { (owner, items) -> AppPopupEntity(owner, PopupItem.encode(items)) })
        if (after.homeOrder != before.homeOrder) database.settingsDao().readSettings()?.let { settings ->
            val home = HomeLayout.decode(settings.homeLayoutJson)
            dao.insertSettings(listOf(settings.copy(homeLayoutJson = home.copy(homeOrder = after.homeOrder).encode())))
        }
    }

    private suspend fun layout(): WidgetLayout {
        val stored = WidgetLayout(
            database.backupDao().allPopups().associate { it.ownerKey to PopupItem.decode(it.itemsJson) },
            database.settingsDao().readSettings()?.let { HomeLayout.decode(it.homeLayoutJson) }?.homeOrder,
        )
        // Fork (#38): an import made before repeats were renumbered may have saved one placeholder ID twice;
        // restoring it would bind both entries to one widget and drop one. Repair it once, where it is read.
        return stored.withUniquePlaceholders().also { if (it != stored) save(stored, it) }
    }
}

/** Placeholders are not host IDs; passing one to AppWidgetHost.deleteAppWidgetId is never valid. */
fun widgetIdsToDelete(removed: List<Int>): List<Int> = removed.filter { it >= 0 }

/** Launcher surfaces render only bound widgets; placeholders stay stored until restore resolves them. */
fun Map<String, List<PopupItem>>.withoutWidgetPlaceholders(): Map<String, List<PopupItem>> =
    mapValues { (_, items) -> items.filter { it.widget?.hasWidget != false } }
