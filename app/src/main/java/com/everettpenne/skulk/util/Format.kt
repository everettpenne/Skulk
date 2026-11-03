package com.everettpenne.skulk.util

import java.util.Locale
import kotlin.math.abs

/** "1.2k", "3M", "-4" ... the compact counts Reddit shows (and Lurker). */
fun Int.compact(): String {
    val n = abs(this).toDouble()
    val sign = if (this < 0) "-" else ""
    fun trim(v: Double, suffix: String) =
        sign + (if (v % 1.0 == 0.0) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)) + suffix
    return when {
        n >= 1_000_000 -> trim(n / 1_000_000.0, "M")
        n >= 1_000 -> trim(n / 1_000.0, "k")
        else -> this.toString()
    }
}

/** "now", "5m", "3h", "2d": how long ago [epochSeconds] was, as Lurker shows it. */
fun timeAgo(epochSeconds: Double, nowSeconds: Double = System.currentTimeMillis() / 1000.0): String {
    val seconds = nowSeconds - epochSeconds
    val days = (seconds / 86400).toInt(); val hours = (seconds / 3600).toInt(); val minutes = (seconds / 60).toInt()
    return when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "now"
    }
}

/** Reddit sends "&amp;" and friends in some text fields even with raw_json=1. */
fun String.unescapeHtml(): String = this
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x200B;", "").replace("&nbsp;", " ")

/** "#RRGGBB" or "RRGGBB" as an opaque ARGB int, or null when it is not a colour. */
fun parseHexColor(raw: String?): Int? {
    val s = raw?.trim()?.removePrefix("#") ?: return null
    if (s.length != 6 || s.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return (0xFF000000L or s.toLong(16)).toInt()
}
