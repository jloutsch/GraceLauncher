package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.notifications.isTitleRedundant
import com.galaxyrio.gracelauncher.data.notifications.stripSenderFromTitle
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.data.notifications.forDisplay
import com.galaxyrio.gracelauncher.data.notifications.formatNotificationMessages
import com.galaxyrio.gracelauncher.ui.notificationDismissNeedsConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTitleTest {
    @Test fun repeatedSendersCanBeRestoredWithoutReadingNotificationsAgain() {
        val messages = listOf("Ana" to "One", "Ana" to "Two", "Bob" to "Three")
        assertEquals("Ana: One\nTwo\nBob: Three", formatNotificationMessages(messages, true))
        assertEquals("Ana: One\nAna: Two\nBob: Three", formatNotificationMessages(messages, false))
        val notification = AppNotification("key", "fixture.chat", "Conversation", "Ana: One\nTwo", 1, 2, true, true,
            originalTitle = "Conversation: Ana", originalText = "Ana: One\nAna: Two")
        assertEquals("Conversation: Ana", notification.forDisplay(false).title)
        assertEquals("Ana: One\nAna: Two", notification.forDisplay(false).text)
        assertEquals("Conversation", notification.forDisplay(true).title)
        assertEquals(2L, notification.forDisplay(false).revision)
    }

    @Test fun confirmationOnlyAppliesToMultipleDismissableApps() {
        val first = AppNotification("one", "first", "Title", "Body", 1, 1, true, true)
        assertFalse(notificationDismissNeedsConfirmation(listOf(first, first.copy(key = "two"))))
        assertTrue(notificationDismissNeedsConfirmation(listOf(first, first.copy(key = "two", packageName = "second"))))
        assertFalse(notificationDismissNeedsConfirmation(listOf(first, first.copy(packageName = "second", canDismiss = false))))
    }

    @Test fun titleRepeatingTheMessageSenderIsRedundant() {
        assertTrue(isTitleRedundant("Dada", "Dada: Good morning"))
        assertTrue(isTitleRedundant("Dada", "Dada: hi\nDada: again"))
        assertTrue(isTitleRedundant("Dada", "Dada"))
    }

    @Test fun channelIsKeptAndSenderDroppedFromTitle() {
        assertEquals("nms-cops-extended", stripSenderFromTitle("nms-cops-extended: Josef Krentz", "Josef Krentz: hi"))
        assertEquals("chan: Josef", stripSenderFromTitle("chan: Josef", "Anna: hi"))
        assertEquals("Dada", stripSenderFromTitle("Dada", "Dada: hi"))
    }

    @Test fun groupAndPlainTitlesAreKept() {
        assertFalse(isTitleRedundant("Family", "Dada: hi"))
        assertFalse(isTitleRedundant("Meeting moved", "Room 4 at 3pm"))
        assertFalse(isTitleRedundant("Dada", "Good morning"))
        assertFalse(isTitleRedundant("Dad", "Dada: Good morning"))
        assertFalse(isTitleRedundant("", "Dada: Good morning"))
    }
}
