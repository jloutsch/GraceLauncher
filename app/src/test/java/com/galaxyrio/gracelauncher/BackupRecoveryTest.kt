package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.applyBackupWithRollback
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class BackupRecoveryTest {
    @Test fun cancellationDuringCommitStillFinishesTheRestore() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val steps = mutableListOf<String>()
        val job = launch(Dispatchers.Default) {
            applyBackupWithRollback(
                apply = { started.complete(Unit); resume.await(); steps += "committed" },
                rollback = { steps += "rolled back" },
                discardSnapshot = { steps += "deleted snapshot" },
            )
        }
        started.await()
        job.cancel()
        resume.complete(Unit)
        job.join()
        assertEquals(listOf("committed", "deleted snapshot"), steps)
    }

    @Test fun writeFailureRollsBackBeforeDeletingTheSnapshot() = runBlocking {
        val failure = IOException("file write failed")
        val steps = mutableListOf<String>()
        val result = runCatching {
            applyBackupWithRollback(
                apply = { steps += "partial restore"; throw failure },
                rollback = { yield(); steps += "restored original" },
                discardSnapshot = { steps += "deleted snapshot" },
            )
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(failure.message, result.exceptionOrNull()?.message)
        assertEquals(listOf("partial restore", "restored original", "deleted snapshot"), steps)
    }

    @Test fun rollbackFailurePreservesTheOnlyRecoveryCopy() = runBlocking {
        val failure = IOException("restore failed")
        val rollbackFailure = IOException("disk unavailable")
        var deleted = false
        val result = runCatching {
            applyBackupWithRollback({ throw failure }, { throw rollbackFailure }, { deleted = true })
        }
        val reported = result.exceptionOrNull()
        assertTrue(reported is IOException)
        assertEquals(failure.message, reported?.message)
        assertTrue(generateSequence(reported) { it.cause }.flatMap { it.suppressed.asSequence() }
            .any { it is IOException && it.message == rollbackFailure.message })
        assertFalse(deleted)
    }

    @Test fun cancellationExceptionAlsoAllowsSuspendingRollback() = runBlocking {
        var rolledBack = false
        var deleted = false
        val result = runCatching {
            applyBackupWithRollback(
                { throw CancellationException("interrupted write") },
                { yield(); rolledBack = true },
                { deleted = true },
            )
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(rolledBack)
        assertTrue(deleted)
    }
}
