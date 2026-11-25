package com.galaxyrio.gracelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.ui.ScreenPositionEffect
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScreenPositionEffectTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var owner: TestOwner
    private lateinit var context: ScreenContext
    private var rememberPosition by mutableStateOf<Boolean?>(false)
    private var preserve = false
    private var returns = 0

    private class TestOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    private class ScreenContext(base: Context) : ContextWrapper(base) {
        lateinit var receiver: BroadcastReceiver
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? {
            this.receiver = requireNotNull(receiver)
            return null
        }
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?): Intent? =
            registerReceiver(receiver, filter, 0)
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?, flags: Int): Intent? =
            registerReceiver(receiver, filter, flags)
        override fun unregisterReceiver(receiver: BroadcastReceiver) = Unit
        fun screenOff() = receiver.onReceive(this, Intent(Intent.ACTION_SCREEN_OFF))
    }

    private fun show() {
        compose.runOnUiThread {
            owner = TestOwner().apply { lifecycle.currentState = Lifecycle.State.RESUMED }
            context = ScreenContext(InstrumentationRegistry.getInstrumentation().targetContext)
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalLifecycleOwner provides owner) {
                ScreenPositionEffect(rememberPosition, { preserve }) {
                    // Returning a pager can suspend; consuming the request must not cancel it.
                    delay(20)
                    returns++
                }
            }
        }
    }

    private fun moveTo(state: Lifecycle.State) {
        compose.runOnIdle { owner.lifecycle.currentState = state }
        compose.waitForIdle()
    }

    @Test fun returningFromAnAppReturnsHomeOnce() {
        show()
        moveTo(Lifecycle.State.CREATED)
        moveTo(Lifecycle.State.RESUMED)
        compose.waitUntil { returns == 1 }
        moveTo(Lifecycle.State.STARTED)
        moveTo(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertEquals(1, returns) }
    }

    @Test fun screenOffReturnsHomeEvenWithoutAStopEvent() {
        show()
        moveTo(Lifecycle.State.STARTED)
        compose.runOnIdle { context.screenOff() }
        moveTo(Lifecycle.State.RESUMED)
        compose.waitUntil { returns == 1 }
    }

    @Test fun rememberedPositionSurvivesAppReturnAndScreenOff() {
        rememberPosition = true
        show()
        moveTo(Lifecycle.State.CREATED)
        compose.runOnIdle { context.screenOff() }
        moveTo(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertEquals(0, returns); rememberPosition = false }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, returns) }
        moveTo(Lifecycle.State.CREATED)
        moveTo(Lifecycle.State.RESUMED)
        compose.waitUntil { returns == 1 }
    }

    @Test fun temporaryPausesAndUnfinishedFlowsKeepTheirScreen() {
        show()
        moveTo(Lifecycle.State.STARTED)
        moveTo(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertEquals(0, returns); preserve = true }
        moveTo(Lifecycle.State.CREATED)
        compose.runOnIdle { context.screenOff(); preserve = false }
        moveTo(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertEquals(0, returns) }
    }

    @Test fun pendingReturnWaitsForSavedSettings() {
        rememberPosition = null
        show()
        moveTo(Lifecycle.State.CREATED)
        moveTo(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertEquals(0, returns); rememberPosition = false }
        compose.waitUntil { returns == 1 }
    }
}
