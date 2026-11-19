package com.example.workhours

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class HistoryPeriod(val label: String) {
    LAST_30("Last 30 days"),
    THIS_WEEK("Current week"),
    LAST_WEEK("Last week"),
    THIS_MONTH("Current month"),
    LAST_MONTH("Last month");

    /** Inclusive date range. "Current" periods end today; weeks start on the locale's first day. */
    fun range(today: LocalDate): Pair<LocalDate, LocalDate> {
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val weekStart = today.with(TemporalAdjusters.previousOrSame(first))
        return when (this) {
            LAST_30 -> today.minusDays(29) to today
            THIS_WEEK -> weekStart to today
            LAST_WEEK -> weekStart.minusWeeks(1) to weekStart.minusDays(1)
            THIS_MONTH -> today.withDayOfMonth(1) to today
            LAST_MONTH -> {
                val start = today.withDayOfMonth(1).minusMonths(1)
                start to start.withDayOfMonth(start.lengthOfMonth())
            }
        }
    }
}
