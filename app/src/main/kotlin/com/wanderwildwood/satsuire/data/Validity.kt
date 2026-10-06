package com.wanderwildwood.satsuire.data

import java.time.Instant
import java.time.LocalDate
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

        /** The start of [date] in [zone], which is how a chosen day is stored. */
        fun millis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
            date.atStartOfDay(zone).toInstant().toEpochMilli()
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
