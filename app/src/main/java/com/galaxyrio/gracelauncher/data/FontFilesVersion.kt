package com.galaxyrio.gracelauncher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fork #7: bumped when an import replaces the imported font files. A font shown before its file arrived was cached as
 * the fallback face; a new version makes every text surface load its fonts again instead of waiting for a restart.
 */
object FontFilesVersion {
    private val _value = MutableStateFlow(0)
    val value: StateFlow<Int> = _value.asStateFlow()

    fun bump() = _value.update { it + 1 }
}
