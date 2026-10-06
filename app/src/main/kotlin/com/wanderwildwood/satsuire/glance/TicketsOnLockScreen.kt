package com.wanderwildwood.satsuire.glance

import android.content.Context
import com.wanderwildwood.satsuire.R
import com.wanderwildwood.satsuire.data.Prefs
import com.wanderwildwood.satsuire.data.Validity
import com.wanderwildwood.satsuire.data.Wallet
import com.wanderwildwood.satsuire.data.isToday
import java.time.LocalDate

/**
 * The tickets for today, by name, for Glance to show on the lock screen: a boarding pass on the
 * day of the flight, a ferry ticket good for the weekend. Only names; the barcode is one press
 * away, in the app.
 */
class TicketsOnLockScreen : GlanceProvider() {

    override fun enabled(context: Context): Boolean = Prefs.onLockScreen(context)

    override fun lines(context: Context): List<Line> {
        val today = LocalDate.now()
        val names = Wallet.rows(context, archived = false)
            .filter { Validity.of(it.validFrom?.time, it.expiry?.time, today).isToday(today) }
            .map { it.name }
        if (names.isEmpty()) return emptyList()
        val heading = context.getString(R.string.glance_heading)
        return names.mapIndexed { i, name -> Line(text = name, heading = if (i == 0) heading else null) }
    }
}
