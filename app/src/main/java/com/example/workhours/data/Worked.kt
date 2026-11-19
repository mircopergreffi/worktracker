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
