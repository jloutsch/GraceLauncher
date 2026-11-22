package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.widgets.StackViewOwners
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Fork (#39): one copy of a widget stack hosts its widget views. */
class StackViewOwnersTest {
    private val home = Any()
    private val widgetScreen = Any()
    private val popup = Any()

    @Test fun nobodyHostsUntilACopyReportsThatItIsVisible() {
        val owners = StackViewOwners()
        assertNull(owners.owner())
        owners.report(home, 0f)
        assertNull(owners.owner())
    }

    @Test fun theMostVisibleCopyHostsAndTheViewsFollowASwipe() {
        val owners = StackViewOwners()
        owners.report(home, 1f)
        owners.report(widgetScreen, .3f)
        assertSame(home, owners.owner())
        owners.report(home, .4f)
        owners.report(widgetScreen, .9f)
        assertSame(widgetScreen, owners.owner())
        owners.report(widgetScreen, 0f)
        assertSame(home, owners.owner())
    }

    @Test fun onATieTheCopyShownLastHostsAndHandsBackWhenItCloses() {
        val owners = StackViewOwners()
        owners.report(home, 1f)
        owners.report(popup, 1f)
        assertSame(popup, owners.owner())
        // Layout noise on the copy behind does not take the views back.
        owners.report(home, .999f)
        assertSame(popup, owners.owner())
        owners.remove(popup)
        assertSame(home, owners.owner())
    }

    @Test fun aCopyThatWasHiddenCountsAsNewWhenShownAgain() {
        val owners = StackViewOwners()
        owners.report(home, 1f)
        owners.report(popup, 1f)
        owners.report(home, 0f)
        owners.report(home, 1f)
        assertSame(home, owners.owner())
    }
}
