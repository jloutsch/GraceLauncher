package com.galaxyrio.gracelauncher.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A temporary pause (for example a permission dialog) is not a departure from the launcher. */
@Composable
internal fun ScreenPositionEffect(
    rememberPosition: Boolean?,
    preservePosition: () -> Boolean,
    onReturnHome: suspend () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preserve by rememberUpdatedState(preservePosition)
    val returnHome by rememberUpdatedState(onReturnHome)
    var pendingReturn by rememberSaveable { mutableStateOf(false) }
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                Lifecycle.Event.ON_STOP -> {
                    if ((context as? Activity)?.isChangingConfigurations != true && !preserve()) pendingReturn = true
                }
                else -> Unit
            }
        }
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF && !preserve()) pendingReturn = true
            }
        }
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(screenOff)
        }
    }

    LaunchedEffect(resumed, pendingReturn, rememberPosition) {
        // Wait for stored preferences before deciding how to restore a stopped screen.
        if (resumed && pendingReturn && rememberPosition != null) {
            if (!rememberPosition && !preserve()) returnHome()
            pendingReturn = false
        }
    }
}
