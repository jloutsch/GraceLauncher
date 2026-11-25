package com.galaxyrio.gracelauncher.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Once writes begin, finish either the restore or its rollback before honouring cancellation. */
internal suspend fun applyBackupWithRollback(
    apply: suspend () -> Unit,
    rollback: suspend () -> Unit,
    discardSnapshot: () -> Unit,
) = withContext(NonCancellable) {
    try {
        apply()
    } catch (failure: Throwable) {
        try {
            rollback()
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressed(rollbackFailure)
            // This is now the only complete copy of the previous setup. Keep it for recovery.
            throw failure
        }
        discardSnapshot()
        throw failure
    }
    discardSnapshot()
}
