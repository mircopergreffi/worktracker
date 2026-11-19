package com.example.workhours.data

import java.time.Duration
import java.time.LocalTime

/**
 * Sums IN→OUT pairs. A trailing IN with no OUT counts up to [openUntil]
 * (pass "now" for today, null for past days where it is ignored).
 */
fun workedSeconds(entries: List<EntryView>, openUntil: LocalTime?): Long {
    var total = 0L
    var start: LocalTime? = null
    for (e in entries) {
        val t = LocalTime.parse(e.time)
        if (e.inOut == "IN") {
            if (start == null) start = t
        } else if (start != null) {
            total += Duration.between(start, t).seconds
            start = null
        }
    }
    if (start != null && openUntil != null) total += maxOf(0, Duration.between(start, openUntil).seconds)
    return total
}

/** Anything that is an IN or OUT row. */
interface Marked {
    val id: Long
    val inOut: String
}

/**
 * The paired entry of [id] in a day's list sorted by time:
 * an IN pairs with the OUT right after it, an OUT with the IN right before it.
 * Returns null for an open IN (timer still running) or an orphan OUT.
 */
fun <T : Marked> partnerOf(sorted: List<T>, id: Long): T? {
    val i = sorted.indexOfFirst { it.id == id }
    if (i < 0) return null
    return if (sorted[i].inOut == "IN") sorted.getOrNull(i + 1)?.takeIf { it.inOut == "OUT" }
    else sorted.getOrNull(i - 1)?.takeIf { it.inOut == "IN" }
}
