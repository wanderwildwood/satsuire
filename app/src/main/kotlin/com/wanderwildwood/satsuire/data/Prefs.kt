package com.wanderwildwood.satsuire.data

import android.content.Context

/** The app's settings, and the group the list was last showing. */
object Prefs {
    private const val FILE = "settings"
    private const val LOCK_SCREEN = "tickets_on_lock_screen"

    /** Today's tickets named on the lock screen, through Glance. On unless turned off; Settings shows it off while Glance is not on the phone. */
    fun onLockScreen(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(LOCK_SCREEN, true)

    fun setOnLockScreen(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(LOCK_SCREEN, on).apply()
    }

    private const val BLOCK_SCREENSHOTS = "block_screenshots"

    /** Screenshots and the Recents thumbnail kept off the cards. On unless turned off. */
    fun blockScreenshots(context: Context): Boolean = prefs(context).getBoolean(BLOCK_SCREENSHOTS, true)

    fun setBlockScreenshots(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(BLOCK_SCREENSHOTS, on).apply()
    }

    /** Puts the screenshot setting on [activity]'s window. */
    fun applyScreenshots(activity: android.app.Activity) {
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (blockScreenshots(activity)) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
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
