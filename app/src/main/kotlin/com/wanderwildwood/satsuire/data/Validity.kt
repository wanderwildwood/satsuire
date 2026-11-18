package com.wanderwildwood.satsuire.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Whether a card can be used on a given day, from its two optional dates. Both are whole days:
 * a ticket valid until the 14th is good all of the 14th, as Catima reads them.
 */
sealed interface Validity {
    /** No dates at all: a library card, a loyalty card. */
    data object Undated : Validity

    data class NotYet(val from: LocalDate, val until: LocalDate?) : Validity

    data class Valid(val from: LocalDate?, val until: LocalDate?) : Validity

    data class Expired(val until: LocalDate) : Validity

    companion object {
        fun of(validFrom: Long?, expiry: Long?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Validity {
            val from = validFrom?.let { day(it, zone) }
            val until = expiry?.let { day(it, zone) }
            return when {
                from == null && until == null -> Undated
                until != null && until.isBefore(today) -> Expired(until)
                from != null && from.isAfter(today) -> NotYet(from, until)
                else -> Valid(from, until)
            }
        }

        fun day(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

        /**
         * Noon of [date] in [zone], which is how a chosen day is stored: read back in a zone up
         * to eleven hours away, it is still the same day (local midnight moved a day west).
         */
        fun millis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
            date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        /**
         * A first day as Catima wants it in a file: Catima counts a card as not valid yet until
         * its start time has passed, so a day kept at noon is written as that day's midnight.
         * Any other time (a pass that starts at 18:00) is written as it is.
         */
        fun startForCatima(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
            val at = Instant.ofEpochMilli(millis).atZone(zone)
            return if (at.toLocalTime() == LocalTime.NOON) at.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() else millis
        }
    }
}

/**
 * True when a card reads as a ticket for [today]: usable today, and either starting or ending
 * today or good for no more than three days in all. A membership valid for a year is usable
 * today too, but it is not today's business, and a card with no dates never is.
 */
fun Validity.isToday(today: LocalDate): Boolean = when (this) {
    is Validity.Valid ->
        from == today || until == today ||
            (from != null && until != null && !until.isAfter(from.plusDays(2)))
    else -> false
}
