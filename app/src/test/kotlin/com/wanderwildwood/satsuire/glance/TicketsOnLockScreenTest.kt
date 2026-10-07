package com.wanderwildwood.satsuire.glance

import android.app.Activity
import android.content.Context
import com.wanderwildwood.satsuire.data.Draft
import com.wanderwildwood.satsuire.data.Prefs
import com.wanderwildwood.satsuire.data.Validity
import com.wanderwildwood.satsuire.data.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import protect.card_locker.TestHelpers
import java.time.LocalDate

/** The lines handed to Glance: today's tickets by name, nothing else, and nothing when off. */
@RunWith(RobolectricTestRunner::class)
class TicketsOnLockScreenTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(Activity::class.java).setup().get()
        TestHelpers.getEmptyDb(context)
    }

    @Test
    fun todaysTicketsOnly() {
        val today = LocalDate.now()
        fun ms(d: LocalDate) = Validity.millis(d)
        Wallet.save(context, Draft(name = "Library", number = "1"))
        Wallet.save(context, Draft(name = "Flight", number = "2", validFrom = ms(today), expiry = ms(today)))
        Wallet.save(context, Draft(name = "Ferry", number = "3", validFrom = ms(today.minusDays(1)), expiry = ms(today.plusDays(1))))
        Wallet.save(context, Draft(name = "Gym", number = "4", expiry = ms(today.plusYears(1))))
        Wallet.save(context, Draft(name = "Train", number = "5", validFrom = ms(today.plusDays(3)), expiry = ms(today.plusDays(3))))
        val putAway = Wallet.save(context, Draft(name = "Old flight", number = "6", validFrom = ms(today), expiry = ms(today)))
        Wallet.setArchived(context, putAway, true)

        val lines = TicketsOnLockScreen.tickets(context, today)
        assertEquals(listOf("Ferry", "Flight"), lines.map { it.text })
        assertEquals(listOf(true, false), lines.map { it.heading != null })

        assertTrue(Prefs.onLockScreen(context))
        Prefs.setOnLockScreen(context, false)
        assertTrue(!Prefs.onLockScreen(context))
    }
}
