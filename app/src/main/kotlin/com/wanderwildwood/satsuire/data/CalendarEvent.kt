package com.wanderwildwood.satsuire.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * When the event made by "Add to calendar" starts and ends.
 *
 * Always a timed half-hour, 09:00 to 09:30, so the end is after the start in every zone (09:00
 * exists on every day, clock changes included) and nothing about all-day events or local
 * midnights is left for the calendar to work out.
 *
 * A calendar that is not known to draw long events well ([span] false) gets one half-hour on
 * one day: a small calendar may keep an event to a single day and stumble on one that runs for
 * months. That day is the first day of the card, or its last day when the first has already
 * gone by (a year's membership is added for its end, not for a January long past). A known
 * calendar ([span] true) gets the half-hour stretched from the first day to the last.
 */
object CalendarEvent {

    data class Times(val begin: Long, val end: Long, val day: LocalDate, val lastDay: LocalDate)

    /** Null when the card has no dates at all. */
    fun times(
        validFrom: Long?,
        expiry: Long?,
        span: Boolean,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Times? {
        val first = (validFrom ?: expiry)?.let { Validity.day(it, zone) } ?: return null
        val last = maxOf(first, Validity.day((expiry ?: validFrom)!!, zone))
        val startDay = when {
            span -> first
            first.isBefore(today) && last != first -> last
            else -> first
        }
        val endDay = if (span) last else startDay
        val begin = startDay.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val end = endDay.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        return Times(begin, end, startDay, last)
    }
}
