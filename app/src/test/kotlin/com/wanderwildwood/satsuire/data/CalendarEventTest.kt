package com.wanderwildwood.satsuire.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CalendarEventTest {
    private val zone = ZoneId.of("America/New_York")
    private fun ms(d: LocalDate) = Validity.millis(d, zone)
    private fun at(millis: Long) = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)

    @Test
    fun undatedHasNoEvent() = assertNull(CalendarEvent.times(null, null, zone))

    @Test
    fun expiryOnlyIsAHalfHourThatDay() {
        val d = LocalDate.of(2026, 12, 31)
        val t = CalendarEvent.times(null, ms(d), zone)!!
        assertEquals(d.atTime(9, 0), at(t.begin))
        assertEquals(d.atTime(9, 30), at(t.end))
    }

    @Test
    fun spansFromTheFirstDayToTheLast() {
        val from = LocalDate.of(2026, 11, 20)
        val until = LocalDate.of(2026, 11, 22)
        val t = CalendarEvent.times(ms(from), ms(until), zone)!!
        assertEquals(from.atTime(9, 0), at(t.begin))
        assertEquals(until.atTime(9, 30), at(t.end))
    }

    /** The end is after the start even for dates entered the wrong way round, and on clock-change days. */
    @Test
    fun endAlwaysAfterBegin() {
        val days = listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4), LocalDate.of(2025, 5, 5))
        for (a in days) for (b in days) {
            val t = CalendarEvent.times(ms(a), ms(b), zone)!!
            assertTrue("$a $b", t.end > t.begin)
        }
    }
}
