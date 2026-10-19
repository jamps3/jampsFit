package com.labbaslabs.jampsfit

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchMessageTextTest {
    @Test fun timestampPrecedesContent() {
        assertEquals("07/10 14:05\nHello", WatchMessageText.withTimestamp("Hello", ZonedDateTime.parse("2026-10-07T14:05:00+03:00")))
    }

    @Test fun truncationPreservesUtf8CharactersAndByteBudget() {
        assertEquals("Hi ", WatchMessageText.utf8Prefix("Hi 😀!", 6).toString(Charsets.UTF_8))
        assertEquals("Hi 😀", WatchMessageText.utf8Prefix("Hi 😀!", 7).toString(Charsets.UTF_8))
        assertEquals("ä", WatchMessageText.utf8Prefix("äö", 3).toString(Charsets.UTF_8))
    }
}
