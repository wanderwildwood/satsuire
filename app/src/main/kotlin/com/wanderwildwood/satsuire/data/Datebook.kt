package com.wanderwildwood.satsuire.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.wanderwildwood.satsuire.ui.HouseApps

/**
 * Datebook (com.wanderwildwood.koyomi), the only calendar "Add to calendar" talks to.
 *
 * The request is addressed to Datebook by package, so it can never reach another calendar app:
 * a calendar that stumbles on it is one Wallet has not been checked against. Datebook reads the
 * whole of it, the span from the first day to the last and the link back to the card
 * ([CardLink]) included. Without Datebook the row is not offered at all.
 */
object Datebook {

    const val PACKAGE = HouseApps.DATEBOOK

    /** The apps that answer Datebook's request ([request]), by package. */
    fun interface Answering {
        fun packages(): List<String>
    }

    /** Android's "add an event" request, for Datebook alone. */
    fun request(): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .setPackage(PACKAGE)

    /** Whether Datebook is installed and takes the request; false when that cannot be asked. */
    fun takes(answering: Answering): Boolean = runCatching {
        PACKAGE in answering.packages()
    }.getOrDefault(false)

    /**
     * Whether the real Datebook is on the phone to take the request: it answers it, and it is
     * signed with Datebook's release certificate ([HouseApps]), so an app that only took its name
     * is never handed the event.
     */
    fun isThere(context: Context): Boolean =
        takes(answering(context.packageManager)) && HouseApps.isReal(context, PACKAGE)

    /** The real answer, from the package manager. */
    fun answering(pm: PackageManager) = Answering {
        pm.queryIntentActivities(request(), PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
    }
}
