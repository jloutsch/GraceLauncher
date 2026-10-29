package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.notifications.messageLine
import com.galaxyrio.gracelauncher.data.notifications.withoutTitleSender
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSenderTest {
    @Test fun oneToOneConversationDoesNotRepeatTheTitleAsSender() {
        assertEquals("no can you bring", messageLine("Britney Bell", "Britney Bell", "no can you bring"))
        assertEquals("no can you bring", messageLine("britney bell ", "Britney Bell", "no can you bring"))
    }

    @Test fun groupConversationsAndUnnamedMessagesKeepTheirSenders() {
        assertEquals("Alice: First message", messageLine("", "Alice", "First message"))
        assertEquals("Bob: hi", messageLine("Weekend plans", "Bob", "hi"))
        assertEquals("hi", messageLine("Weekend plans", "", "hi"))
    }

    @Test fun plainTextDropsOnlyALeadingTitleSender() {
        assertEquals("no can you bring", withoutTitleSender("Britney Bell", "Britney Bell: no can you bring"))
        assertEquals("one\ntwo", withoutTitleSender("Britney Bell", "Britney Bell: one\nBritney Bell: two"))
        assertEquals("Alex: hi", withoutTitleSender("Britney Bell", "Alex: hi"))
        assertEquals("Ask Britney Bell: later", withoutTitleSender("Britney Bell", "Ask Britney Bell: later"))
        assertEquals("text", withoutTitleSender("", "text"))
    }
}
