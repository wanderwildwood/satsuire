package com.wanderwildwood.satsuire.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Add to calendar" row shows only when Datebook answers its request. */
class DatebookTest {

    @Test
    fun shownWhenDatebookAnswers() =
        assertTrue(Datebook.takes { listOf(Datebook.PACKAGE) })

    @Test
    fun hiddenWithoutDatebook() =
        assertFalse(Datebook.takes { emptyList() })

    /** Another calendar answering is no reason to show the row. */
    @Test
    fun hiddenWhenOnlyAnotherCalendarAnswers() =
        assertFalse(Datebook.takes { listOf("com.google.android.calendar", "com.mudita.calendar") })

    @Test
    fun hiddenWhenTheQuestionFails() =
        assertFalse(Datebook.takes { throw SecurityException("no") })
}
