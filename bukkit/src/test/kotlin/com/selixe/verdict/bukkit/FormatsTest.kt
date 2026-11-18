package com.selixe.verdict.bukkit

import com.selixe.verdict.bukkit.util.formatDuration
import com.selixe.verdict.bukkit.util.isIp

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormatsTest {

    @Test
    fun `durations keep the two largest units`() {
        assertEquals("7d", formatDuration(Duration.ofDays(7)))
        assertEquals("1d 12h", formatDuration(Duration.ofHours(36)))
        assertEquals("2h 30m", formatDuration(Duration.ofMinutes(150)))
        assertEquals("1d 1h", formatDuration(Duration.ofSeconds(90_061)))
        assertEquals("45s", formatDuration(Duration.ofSeconds(45)))
        assertEquals("0s", formatDuration(Duration.ofSeconds(-5)))
    }

    @Test
    fun `ip addresses are told apart from names`() {
        assertTrue(isIp("127.0.0.1"))
        assertTrue(isIp("2001:db8::1"))
        assertFalse(isIp("Steve"))
        assertFalse(isIp("Steve_123"))
        assertFalse(isIp("example.com"))
    }

}
