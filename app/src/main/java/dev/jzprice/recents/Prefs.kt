package dev.jzprice.recents

import android.content.Context

/** Stores the remote keycode that opens the switcher (0 = not set yet). */
object Prefs {
    private const val FILE = "recents_switcher"
    private const val KEY_TRIGGER = "trigger_keycode"

    fun triggerKey(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_TRIGGER, 0)

    fun setTriggerKey(ctx: Context, keyCode: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY_TRIGGER, keyCode).apply()
    }
}
