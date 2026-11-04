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
    private val today = LocalDate.of(2026, 11, 4)
    private fun ms(d: LocalDate) = Validity.millis(d, zone)
    private fun at(millis: Long) = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)

    @Test
    fun undatedHasNoEvent() = assertNull(CalendarEvent.times(null, null, false, today, zone))

    @Test
    fun expiryOnlyIsAHalfHourThatDay() {
        val d = LocalDate.of(2026, 12, 31)
        val t = CalendarEvent.times(null, ms(d), false, today, zone)!!
        assertEquals(d.atTime(9, 0), at(t.begin))
        assertEquals(d.atTime(9, 30), at(t.end))
    }

    /** A plain calendar never gets an event that runs past its first day. */
    @Test
    fun plainCalendarGetsOneDay() {
        val from = LocalDate.of(2026, 11, 20)
        val until = LocalDate.of(2027, 11, 19)
        val t = CalendarEvent.times(ms(from), ms(until), false, today, zone)!!
        assertEquals(from.atTime(9, 0), at(t.begin))
        assertEquals(from.atTime(9, 30), at(t.end))
        assertEquals(until, t.lastDay)
    }

    /** A membership that began in the past is added for its end. */
    @Test
    fun pastStartGoesToTheLastDay() {
        val from = LocalDate.of(2026, 1, 1)
        val until = LocalDate.of(2026, 12, 31)
        val t = CalendarEvent.times(ms(from), ms(until), false, today, zone)!!
        assertEquals(until.atTime(9, 0), at(t.begin))
        assertEquals(until.atTime(9, 30), at(t.end))
    }

    @Test
    fun knownCalendarGetsTheSpan() {
        val from = LocalDate.of(2026, 11, 20)
        val until = LocalDate.of(2026, 11, 22)
        val t = CalendarEvent.times(ms(from), ms(until), true, today, zone)!!
        assertEquals(from.atTime(9, 0), at(t.begin))
        assertEquals(until.atTime(9, 30), at(t.end))
    }

    /** The end is after the start even for dates entered the wrong way round, and on clock-change days. */
    @Test
    fun endAlwaysAfterBegin() {
        val days = listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1), today, LocalDate.of(2025, 5, 5))
        for (a in days) for (b in days) for (span in listOf(true, false)) {
            val t = CalendarEvent.times(ms(a), ms(b), span, today, zone)!!
            assertTrue("$a $b $span", t.end > t.begin)
        }
    }
}
