package com.galaxyrio.gracelauncher.data.media

import android.content.Context
import android.media.session.MediaSession
import android.os.Looper
import androidx.annotation.MainThread
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Platform transport controls lack shuffle. Media3 bridges both modern and legacy sessions. */
@MainThread
internal class MediaShuffleController(context: Context, token: MediaSession.Token,
    private val onChanged: (available: Boolean, active: Boolean) -> Unit) {
    private val executor = context.mainExecutor
    private var closed = false
    private var tokenFuture: ListenableFuture<SessionToken>? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    var available = false
        private set
    var active = false
        private set

    private val listener = object : Player.Listener {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) { publish() }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { publish() }
    }

    init {
        runCatching {
            val future = SessionToken.createSessionToken(context, token)
            tokenFuture = future
            future.addListener({
                if (!closed) runCatching {
                    val connection = MediaController.Builder(context, future.get())
                        .setApplicationLooper(Looper.getMainLooper())
                        .setListener(object : MediaController.Listener {
                            override fun onDisconnected(controller: MediaController) {
                                this@MediaShuffleController.controller = null
                                publish()
                            }
                        }).buildAsync()
                    controllerFuture = connection
                    connection.addListener({
                        if (!closed) runCatching {
                            controller = connection.get().also { it.addListener(listener) }
                            publish()
                        }.onFailure { publish() }
                    }, executor)
                }.onFailure { publish() }
            }, executor)
        }
    }

    private fun publish() {
        if (closed) return
        val current = controller
        available = current?.isConnected == true && current.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)
        active = current?.isConnected == true && current.shuffleModeEnabled
        onChanged(available, active)
    }

    fun toggle(): Boolean {
        val current = controller ?: return false
        if (closed || !current.isConnected || !current.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) return false
        return runCatching { current.shuffleModeEnabled = !current.shuffleModeEnabled; publish() }.isSuccess
    }

    fun close() {
        if (closed) return
        closed = true
        tokenFuture?.cancel(false)
        controller?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
    }
}
