package com.wanderwildwood.satsuire.data

import android.content.Context

/** The app's settings, and the group the list was last showing. */
object Prefs {
    private const val FILE = "settings"
    private const val LOCK_SCREEN = "tickets_on_lock_screen"

    /** Today's tickets named on the lock screen, through Glance. On unless turned off. */
    fun onLockScreen(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(LOCK_SCREEN, true)

    fun setOnLockScreen(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(LOCK_SCREEN, on).apply()
    }

    private const val ORDER = "order"
    private const val GROUP = "group"

    fun order(context: Context): Wallet.Order =
        runCatching { Wallet.Order.valueOf(prefs(context).getString(ORDER, null) ?: "") }.getOrDefault(Wallet.Order.NAME)

    fun setOrder(context: Context, order: Wallet.Order) {
        prefs(context).edit().putString(ORDER, order.name).apply()
    }

    /** The group the list shows, or null for every card. */
    fun group(context: Context): String? = prefs(context).getString(GROUP, null)

    fun setGroup(context: Context, group: String?) {
        prefs(context).edit().putString(GROUP, group).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
