package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.settings.GraceButtonActionSettings
import com.galaxyrio.gracelauncher.ui.settings.LabGestureSettings
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PullProtectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var settings by mutableStateOf(GraceButtonSettings.homeDefaults().copy(protectSwipeUp = true, protectSwipeDown = true))
    private var pressed = false
    private var feedback = 0
    private var feedbackWhileHeld = false
    private var committedWhileHeld = false
    private val gestures = mutableListOf<GraceButtonGesture>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            if (hapticFeedbackType == HapticFeedbackType.GestureThresholdActivate) {
                feedback++
                feedbackWhileHeld = feedbackWhileHeld || pressed
            }
        }
    }

    @Test fun quickShortSwipesCannotUseVelocityToBypassEitherThreshold() {
        showHome()
        fastSwipe(-80f)
        fastSwipe(80f)
        compose.runOnIdle { assertTrue(gestures.isEmpty()); assertEquals(0, feedback) }
    }

    @Test fun bothDirectionsSignalWhileHeldAndCommitOnlyOnRelease() {
        showHome()
        for ((distance, expected) in listOf(-200f to GraceButtonGesture.SwipeUp, 200f to GraceButtonGesture.SwipeDown)) {
            pull(distance) // Hold before UP so a ready gesture must still leave Home intact.
            compose.runOnIdle { assertEquals(if (distance < 0) 1 else 2, feedback); assertEquals(if (distance < 0) 0 else 1, gestures.size) }
            home().performTouchInput { advanceEventTime(120); up() }
            compose.runOnIdle { assertEquals(expected, gestures.last()); assertTrue(feedbackWhileHeld); assertFalse(committedWhileHeld) }
        }
    }

    @Test fun reversingOrCancellingAnArmedPullDoesNotOpenAnything() {
        showHome()
        pull(-200f)
        home().performTouchInput { moveBy(Offset(0f, 110f * compose.density.density), 300); advanceEventTime(120); up() }
        compose.runOnIdle { assertEquals(1, feedback); assertTrue(gestures.isEmpty()) }
        pull(200f)
        home().performTouchInput { cancel() }
        compose.runOnIdle { assertEquals(2, feedback); assertTrue(gestures.isEmpty()) }
        pull(-40f, release = true)
        compose.runOnIdle { assertEquals(2, feedback); assertTrue(gestures.isEmpty()) }
    }

    @Test fun secondFingerCancelsAnArmedPull() {
        showHome()
        pull(-200f)
        home().performTouchInput { down(1, Offset(20f, 20f)); up(1); advanceEventTime(120); up(0) }
        compose.runOnIdle { assertEquals(1, feedback); assertTrue(gestures.isEmpty()) }
    }

    @Test fun scrollThatReachesTheEndNeedsAFreshDeliberatePull() {
        showHome(appCount = 25)
        pull(-1500f, release = true)
        compose.runOnIdle { assertTrue(gestures.isEmpty()); assertEquals(0, feedback) }
        pull(-200f, release = true)
        compose.runOnIdle { assertEquals(listOf(GraceButtonGesture.SwipeUp), gestures); assertEquals(1, feedback) }
    }

    @Test fun protectionCanBeDisabledIndependentlyForEachDirection() {
        settings = settings.copy(protectSwipeUp = false)
        showHome()
        fastSwipe(-80f)
        fastSwipe(80f)
        compose.runOnIdle {
            assertEquals(listOf(GraceButtonGesture.SwipeUp), gestures); assertEquals(0, feedback)
            settings = settings.copy(protectSwipeUp = true, protectSwipeDown = false)
        }
        fastSwipe(-80f)
        fastSwipe(80f)
        compose.runOnIdle { assertEquals(listOf(GraceButtonGesture.SwipeUp, GraceButtonGesture.SwipeDown), gestures); assertEquals(0, feedback) }
    }

    @Test fun existingSensitivityControlsBothProtectedDirections() {
        settings = settings.copy(swipeSensitivity = 200)
        showHome()
        pull(-120f, release = true)
        pull(120f, release = true)
        compose.runOnIdle {
            assertEquals(listOf(GraceButtonGesture.SwipeUp, GraceButtonGesture.SwipeDown), gestures)
            assertEquals(2, feedback)
            settings = settings.copy(swipeSensitivity = 50)
        }
        pull(-200f, release = true)
        pull(200f, release = true)
        compose.runOnIdle { assertEquals(2, gestures.size); assertEquals(2, feedback) }
        pull(-340f, release = true)
        pull(340f, release = true)
        compose.runOnIdle {
            assertEquals(listOf(GraceButtonGesture.SwipeUp, GraceButtonGesture.SwipeDown,
                GraceButtonGesture.SwipeUp, GraceButtonGesture.SwipeDown), gestures)
            assertEquals(4, feedback)
        }
    }

    @Test fun deliberatePullAlsoWorksWithThePlatformStretchEffect() {
        showHome(nativeStretch = true)
        // Inject the whole stream before waiting: the native stretch animates while held.
        pull(-240f, release = true)
        compose.runOnIdle { assertEquals(listOf(GraceButtonGesture.SwipeUp), gestures); assertEquals(1, feedback) }
    }

    @Test fun advancedControlsReuseSensitivityAndPersistIndependentProtection() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LabGestureSettings(LauncherUiState(settings = LauncherSettings(homeGestures = settings)),
                    LauncherActions(updateSettings = { transform -> settings = transform(LauncherSettings(homeGestures = settings)).homeGestures }),
                    onBack = {})
            }
        }
        compose.onAllNodesWithTag("lab_swipe_sensitivity").assertCountEquals(1)
        advancedControl("lab_swipe_sensitivity").performSemanticsAction(SemanticsActions.SetProgress) { it(180f) }
        advancedControl("home_pull_protection:SwipeUp").performClick()
        compose.runOnIdle {
            assertFalse(settings.protectSwipeUp); assertTrue(settings.protectSwipeDown)
            assertEquals(180, settings.swipeSensitivity)
        }
        advancedControl("home_pull_protection:SwipeDown").performClick()
        advancedControl("home_pull_protection:SwipeUp").performClick()
        compose.runOnIdle {
            assertTrue(settings.protectSwipeUp); assertFalse(settings.protectSwipeDown)
            val restored = GraceButtonSettings.decode(settings.encode(), GraceButtonSettings.homeDefaults())
            assertEquals(settings, restored)
            val legacy = GraceButtonSettings.decode("{}", GraceButtonSettings.homeDefaults())
            assertFalse(legacy.protectSwipeUp); assertFalse(legacy.protectSwipeDown)
            assertEquals(100, legacy.swipeSensitivity)
            assertFalse(GraceButtonSettings.decode("{}").protectSwipeUp)
        }
        advancedControl("lab_swipe_sensitivity_reset").performClick()
        compose.runOnIdle { assertEquals(100, settings.swipeSensitivity) }
    }

    @Test fun gestureActionPagesDoNotDuplicateAdvancedControls() {
        var home by mutableStateOf(true)
        var direction by mutableStateOf(GraceButtonGesture.SwipeUp)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                GraceButtonActionSettings(direction, LauncherUiState(settings = LauncherSettings(homeGestures = settings)),
                    LauncherActions(), onBack = {}, home = home, onChooseTarget = {})
            }
        }
        for ((isHome, gesture) in listOf(true to GraceButtonGesture.SwipeUp,
            true to GraceButtonGesture.SwipeDown, false to GraceButtonGesture.SwipeUp)) {
            compose.runOnIdle { home = isHome; direction = gesture }
            compose.onNodeWithTag("grace_action_page").assertIsDisplayed()
            compose.onNodeWithTag("home_pull_protection:${gesture.name}").assertDoesNotExist()
            compose.onNodeWithTag("home_pull_sensitivity:${gesture.name}").assertDoesNotExist()
            compose.onNodeWithTag("lab_swipe_sensitivity").assertDoesNotExist()
        }
    }

    private fun advancedControl(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun home() = compose.onNodeWithTag("home_content")
    private fun fastSwipe(distanceDp: Float) {
        home().performTouchInput {
            val start = Offset(centerX, height * if (distanceDp > 0) .35f else .65f)
            swipeWithVelocity(start, start + Offset(0f, distanceDp * compose.density.density),
                endVelocity = 4000f * compose.density.density, durationMillis = 40)
        }
    }
    private fun pull(distanceDp: Float, release: Boolean = false) {
        home().performTouchInput {
            val start = Offset(centerX, height * if (distanceDp > 0) .3f else .7f)
            down(start)
            repeat(12) { moveTo(start + Offset(0f, distanceDp * compose.density.density * (it + 1) / 12), 32) }
            if (release) { advanceEventTime(120); up() }
        }
    }
    private fun showHome(appCount: Int = 0, nativeStretch: Boolean = false) {
        val apps = List(appCount) { LauncherApp(ComponentName("test.pull$it", "Activity"), "App $it", null) }
        compose.setContent {
            val platform = LocalOverscrollFactory.current
            CompositionLocalProvider(LocalOverscrollFactory provides if (nativeStretch) platform else null,
                LocalHapticFeedback provides haptics) {
                GraceLauncherTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(Color.DarkGray).pointerInput(Unit) {
                        awaitPointerEventScope { while (true) pressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed } }
                    }) {
                        HomeScreen(LauncherUiState(apps = apps, favoriteKeys = apps.mapTo(linkedSetOf()) { it.key },
                            isLoadingApps = false, settings = LauncherSettings(clockEnabled = false, calendarAgenda = false,
                                weatherEnabled = false, mediaPlayer = false, homeGestures = settings)),
                            topSpace = 64.dp, onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onAppDetails = {},
                            onAppShortcuts = { _, _ -> }, onHomeGesture = { gesture, _ ->
                                committedWhileHeld = committedWhileHeld || pressed; gestures += gesture
                            })
                    }
                }
            }
        }
    }
}
