package com.labbaslabs.jampsfit

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object WatchMessageText {
    fun withTimestamp(text: String, time: ZonedDateTime = ZonedDateTime.now()): String =
        "${time.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))}\n$text"

    fun utf8Prefix(text: String, maxBytes: Int): ByteArray {
        var end = 0
        var size = 0
        while (end < text.length) {
            val next = end + Character.charCount(text.codePointAt(end))
            val bytes = text.substring(end, next).toByteArray(Charsets.UTF_8).size
            if (size + bytes > maxBytes) break
            size += bytes
            end = next
        }
        return text.substring(0, end).toByteArray(Charsets.UTF_8)
    }
}
