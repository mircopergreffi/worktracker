package com.example.workhours.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

const val DAY_SECONDS = 86_400

/**
 * Entry times are stored as "HH:mm:ss" counted from the start of the entry's day.
 * An OUT that closes a session started the day before is stored on the IN's day with
 * hours 24 and up (01:30 the next morning becomes "25:30:00"), so a shift through
 * midnight stays one IN/OUT pair on one day, and sorting by time still works.
 */
fun parseSeconds(time: String): Int {
    val p = time.split(":")
    return p[0].toInt() * 3600 + p[1].toInt() * 60 + p[2].toInt()
}

fun formatSeconds(sec: Int): String = "%02d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)

/** Clock time for display: "25:30:00" becomes "01:30:00 (+1)". */
fun prettyTime(time: String): String {
    val s = parseSeconds(time)
    return if (s >= DAY_SECONDS) formatSeconds(s - DAY_SECONDS) + " (+1)" else formatSeconds(s)
}

fun absSeconds(day: LocalDate, secs: Int): Long = day.toEpochDay() * DAY_SECONDS + secs

/**
 * "Now" in seconds counted from the start of [day]. Null when [day] is more than one day back,
 * so a forgotten OUT from days ago is never counted as still running.
 */
fun nowOn(day: LocalDate, now: LocalDateTime): Int? {
    val diff = ChronoUnit.DAYS.between(day, now.toLocalDate())
    return if (diff in 0..1) (diff * DAY_SECONDS).toInt() + now.toLocalTime().toSecondOfDay() else null
}

/**
 * Sums IN→OUT pairs. A trailing IN with no OUT counts up to [openUntil]
 * (seconds from the start of that day, see [nowOn]); null ignores it.
 */
fun workedSeconds(entries: List<Marked>, openUntil: Int?): Long {
    var total = 0L
    var start: Int? = null
    for (e in entries) {
        val t = parseSeconds(e.time)
        if (e.inOut == "IN") {
            if (start == null) start = t
        } else if (start != null) {
            total += maxOf(0, t - start)
            start = null
        }
    }
    if (start != null && openUntil != null) total += maxOf(0, openUntil - start)
    return total
}

/**
 * Seconds from the start of the day at which the worked time reached [target], or null if not yet.
 * Same pairing rules as [workedSeconds].
 */
fun targetReachedAt(entries: List<Marked>, target: Long, openUntil: Int?): Int? {
    var total = 0L
    var start: Int? = null
    for (e in entries) {
        val t = parseSeconds(e.time)
        if (e.inOut == "IN") {
            if (start == null) start = t
        } else if (start != null) {
            val len = maxOf(0, t - start)
            if (total + len >= target) return start + (target - total).toInt()
            total += len
            start = null
        }
    }
    if (start != null && openUntil != null && total + maxOf(0, openUntil - start) >= target) {
        return start + (target - total).toInt()
    }
    return null
}

/**
 * Deleting the very last entry removes only that entry when it is from today, or an OUT that
 * ended after midnight of yesterday's session: the IN stays open and the live timer runs again.
 */
fun deletesAlone(day: String, time: String, isOverallLast: Boolean, today: LocalDate): Boolean {
    if (!isOverallLast) return false
    val d = LocalDate.parse(day)
    return d == today || (d == today.minusDays(1) && parseSeconds(time) >= DAY_SECONDS)
}

/** Anything that is an IN or OUT row. */
interface Marked {
    val id: Long
    val inOut: String
    val time: String
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
