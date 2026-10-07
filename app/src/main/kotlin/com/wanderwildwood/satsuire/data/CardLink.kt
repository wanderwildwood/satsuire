package com.wanderwildwood.satsuire.data

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/**
 * The link from a calendar event back to the card it was made from, in Android's own place for
 * one: the event's `CUSTOM_APP_PACKAGE` and `CUSTOM_APP_URI`, which "Add to calendar" fills.
 * A calendar app that reads them (Calendar does) opens the card with
 * `CalendarContract.ACTION_HANDLE_CUSTOM_EVENT`.
 */
object CardLink {

    private const val PREFIX = "satsuire://card/"

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
