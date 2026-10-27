package com.galaxyrio.gracelauncher.data.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Exports and imports never overlap, so an export never captures a half-applied import. */
object BackupLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
