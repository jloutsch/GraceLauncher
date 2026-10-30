package com.galaxyrio.gracelauncher.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLineTest {
    @Test fun oneToOneChatsDoNotRepeatTheSenderButGroupsAndRepliesKeepIt() {
        assertEquals("Hi there", messageLine("Alex", "Hi there", title = "Alex"))
        assertEquals("Sam: On my way", messageLine("Sam", "On my way", title = "Family"))
        assertEquals("You: See you soon", messageLine("You", "See you soon", title = "Alex"))
        assertEquals("No sender", messageLine("", "No sender", title = "Alex"))
    }
}
