package com.galaxyrio.gracelauncher

import android.appwidget.AppWidgetProviderInfo
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.platform.HomeWidgetHostView
import com.galaxyrio.gracelauncher.ui.widgets.WidgetViewCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetViewCacheTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aWidgetKeepsOneViewThatMovesBetweenSlots() {
        val cache = WidgetViewCache()
        val info = AppWidgetProviderInfo()
        val first = Any()
        val second = Any()
        val view = HomeWidgetHostView(context)
        val oldSlot = FrameLayout(context).apply { addView(view) }
        cache.keep(7, info, first, view)

        assertSame(view, cache.take(7, info, second))
        assertNull("the view leaves its previous slot", view.parent)
        assertTrue(oldSlot.childCount == 0)
        assertFalse("the slot it moved out of must not clear its callbacks", cache.owns(7, first))
        assertTrue(cache.owns(7, second))
    }

    @Test fun aChangedProviderOrRemovedWidgetGetsAFreshView() {
        val cache = WidgetViewCache()
        val info = AppWidgetProviderInfo()
        cache.keep(7, info, Any(), HomeWidgetHostView(context))

        assertNull(cache.take(7, AppWidgetProviderInfo(), Any()))
        cache.retain(listOf(8))
        assertNull(cache.take(7, info, Any()))
    }
}
