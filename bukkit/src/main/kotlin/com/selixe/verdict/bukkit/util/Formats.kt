package com.selixe.verdict.bukkit.util

import java.time.Duration

private val IPV4 = Regex("^(\\d{1,3})(\\.\\d{1,3}){3}$")

fun formatDuration(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)

    val parts = listOf(
        seconds / 86_400 to "d",
        seconds % 86_400 / 3_600 to "h",
        seconds % 3_600 / 60 to "m",
        seconds % 60 to "s"
    ).filter { it.first > 0 }.take(2)

    if (parts.isEmpty()) {
        return "0s"
    }

    return parts.joinToString(" ") { "${it.first}${it.second}" }
}

fun isIp(input: String): Boolean {
    if (IPV4.matches(input)) {
        return true
    }

    return ':' in input && input.all { it.isLetterOrDigit() || it == ':' || it == '.' }
}
