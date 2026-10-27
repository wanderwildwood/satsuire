package com.wanderwildwood.satsuire.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract

/**
 * The link from a calendar event back to the card it was made from, in Android's own place for
 * one: the event's `CUSTOM_APP_PACKAGE` and `CUSTOM_APP_URI`. A calendar app that reads them
 * shows an "Open in Wallet" button and opens the card with
 * `CalendarContract.ACTION_HANDLE_CUSTOM_EVENT`.
 *
 * The extras are only sent to a calendar known to read them ([READERS]): a calendar that does
 * not know them has no use for them, and a request it has never seen is the one kind of
 * request that could trip it. Every other calendar gets the plain event, with the card's name
 * in the note.
 */
object CardLink {

    private const val PREFIX = "satsuire://card/"

    /** Google Calendar, and Calendar (koyomi), which read the link and draw the button. */
    private val READERS = setOf("com.google.android.calendar", "com.wanderwildwood.koyomi")

    /**
     * Whether every calendar that could take [intent] reads the link, so that whichever one is
     * chosen (the phone's default, or from a list) is one that does.
     */
    fun readsLink(context: Context, intent: Intent): Boolean = runCatching {
        val all = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        all.isNotEmpty() && all.all { it.activityInfo.packageName in READERS }
    }.getOrDefault(false)

    fun uri(id: Int): String = PREFIX + id

    /** Puts the link to card [id] on an "add to calendar" request. */
    fun put(context: Context, intent: Intent, id: Int): Intent = intent
        .putExtra(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
        .putExtra(CalendarContract.Events.CUSTOM_APP_URI, uri(id))

    /** The card a calendar event asks for, or null when [intent] is no such request. */
    fun id(intent: Intent?): Int? {
        if (intent?.action != CalendarContract.ACTION_HANDLE_CUSTOM_EVENT) return null
        val uri = intent.getStringExtra(CalendarContract.EXTRA_CUSTOM_APP_URI) ?: return null
        if (!uri.startsWith(PREFIX)) return null
        return uri.removePrefix(PREFIX).toIntOrNull()
    }
}
