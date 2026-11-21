package com.example.workhours

import java.time.LocalDateTime

enum class RoundMode { NONE, UP, DOWN, NEAREST }

/** How the time of an IN / OUT button press is rounded before it is saved. */
enum class TimeRounding(val label: String, private val stepSeconds: Int, private val mode: RoundMode) {
    NONE("No rounding", 1, RoundMode.NONE),
    MINUTE_UP("Round minutes up (ceil)", 60, RoundMode.UP),
    MINUTE_DOWN("Round minutes down (floor)", 60, RoundMode.DOWN),
    MINUTE_NEAREST("Round minutes (round)", 60, RoundMode.NEAREST),
    QUARTER_UP("Round 15 minutes up (ceil)", 15 * 60, RoundMode.UP),
    QUARTER_DOWN("Round 15 minutes down (floor)", 15 * 60, RoundMode.DOWN),
    HALF_UP("Round 30 minutes up", 30 * 60, RoundMode.UP),
    HALF_DOWN("Round 30 minutes down", 30 * 60, RoundMode.DOWN),
    HOUR_UP("Round 1 hour up", 60 * 60, RoundMode.UP),
    HOUR_DOWN("Round 1 hour down", 60 * 60, RoundMode.DOWN);

    /** Rounded time on the same day; rounding past midnight stops at 23:59:59. */
    fun apply(t: LocalDateTime): LocalDateTime {
        if (mode == RoundMode.NONE) return t
        val secs = t.toLocalTime().toSecondOfDay()
        val rounded = when (mode) {
            RoundMode.UP -> (secs + stepSeconds - 1) / stepSeconds * stepSeconds
            RoundMode.DOWN -> secs / stepSeconds * stepSeconds
            else -> (secs + stepSeconds / 2) / stepSeconds * stepSeconds
        }
        val day = t.toLocalDate()
        return if (rounded >= 24 * 3600) day.atTime(23, 59, 59)
        else day.atStartOfDay().plusSeconds(rounded.toLong())
    }
}
