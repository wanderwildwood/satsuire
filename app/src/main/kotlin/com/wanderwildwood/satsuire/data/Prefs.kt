package com.wanderwildwood.satsuire.data

import android.content.Context

/** The app's one setting. */
object Prefs {
    private const val FILE = "settings"
    private const val LOCK_SCREEN = "tickets_on_lock_screen"

    /** Today's tickets named on the lock screen, through Glance. On unless turned off. */
    fun onLockScreen(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(LOCK_SCREEN, true)

    fun setOnLockScreen(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(LOCK_SCREEN, on).apply()
    }
}
