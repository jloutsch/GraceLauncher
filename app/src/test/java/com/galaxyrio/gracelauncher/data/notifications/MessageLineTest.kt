package com.galaxyrio.gracelauncher.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLineTest {
    @Test fun oneToOneChatsDoNotRepeatTheSenderButGroupsAndRepliesKeepIt() {
        val alexOnly = setOf("Alex")
        assertEquals("Hi there", messageLine("Alex", "Hi there", title = "Alex", otherSenders = alexOnly))
        assertEquals("Sam: On my way", messageLine("Sam", "On my way", title = "Family", otherSenders = setOf("Sam")))
        assertEquals("You: See you soon", messageLine("You", "See you soon", title = "Alex", otherSenders = alexOnly))
        assertEquals("No sender", messageLine("", "No sender", title = "Alex", otherSenders = alexOnly))
    }

    @Test fun untitledGroupKeepsTheNameOfTheSenderAndroidUsedAsTitle() {
        // Without a conversation title, Android titles the notification with the latest sender.
        assertEquals("Bob: Second message",
            messageLine("Bob", "Second message", title = "Bob", otherSenders = setOf("Alice", "Bob")))
    }
}
