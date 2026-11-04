package com.wanderwildwood.satsuire.data

import java.time.ZoneId

/**
 * When the event made by "Add to calendar" starts and ends.
 *
 * Always a timed half-hour, 09:00 to 09:30, so the end is after the start in every zone (09:00
 * exists on every day, clock changes included) and nothing about all-day events or local
 * midnights is left for the calendar to work out. The half-hour is stretched from the card's
 * first day to its last, which Datebook draws as one event over those days.
 */
object CalendarEvent {

    data class Times(val begin: Long, val end: Long)

    /** Null when the card has no dates at all. */
    fun times(validFrom: Long?, expiry: Long?, zone: ZoneId = ZoneId.systemDefault()): Times? {
        val first = (validFrom ?: expiry)?.let { Validity.day(it, zone) } ?: return null
        val last = maxOf(first, Validity.day((expiry ?: validFrom)!!, zone))
        val begin = first.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val end = last.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        return Times(begin, end)
    }
}
