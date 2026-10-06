package com.wanderwildwood.satsuire.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ValidityTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 10, 18)
    private fun ms(d: LocalDate) = Validity.millis(d, zone)
    private fun of(from: LocalDate?, until: LocalDate?) = Validity.of(from?.let(::ms), until?.let(::ms), today, zone)

    @Test
    fun undated() = assertEquals(Validity.Undated, of(null, null))

    @Test
    fun lastDayIsStillGood() {
        assertTrue(of(null, today) is Validity.Valid)
        assertEquals(Validity.Expired(today.minusDays(1)), of(null, today.minusDays(1)))
    }

    @Test
    fun firstDayIsGood() {
        assertTrue(of(today, null) is Validity.Valid)
        assertTrue(of(today.plusDays(1), null) is Validity.NotYet)
    }

    /** A day stored as a local midnight reads back as that day, not the evening before. */
    @Test
    fun dayRoundTrips() {
        val d = LocalDate.of(2026, 3, 8) // the night the clocks change in New York
        assertEquals(d, Validity.day(Validity.millis(d, zone), zone))
    }

    @Test
    fun ticketsForToday() {
        // A weekend ferry ticket, a flight today, a ticket ending today.
        assertTrue(of(today.minusDays(1), today.plusDays(1)).isToday(today))
        assertTrue(of(today, null).isToday(today))
        assertTrue(of(null, today).isToday(today))
        // A year's membership is usable today but is not today's business.
        assertFalse(of(today.minusMonths(6), today.plusMonths(6)).isToday(today))
        assertFalse(of(null, today.plusDays(30)).isToday(today))
        assertFalse(of(null, null).isToday(today))
        // Tomorrow's flight is not today's.
        assertFalse(of(today.plusDays(1), today.plusDays(1)).isToday(today))
        // Nor yesterday's.
        assertFalse(of(today.minusDays(1), today.minusDays(1)).isToday(today))
    }
}
