package dev.jzprice.recents

import android.content.Context

/** Packages the user has hidden from the switcher list. */
object HiddenApps {
    private const val FILE = "recents_switcher"
    private const val KEY = "hidden_packages"

    fun all(ctx: Context): Set<String> {
        val stored = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY, null)
        return HashSet<String>(stored ?: emptySet())
    }

    fun hide(ctx: Context, pkg: String) = save(ctx, HashSet<String>(all(ctx)).apply { add(pkg) })

    fun unhide(ctx: Context, pkg: String) = save(ctx, HashSet<String>(all(ctx)).apply { remove(pkg) })

    private fun save(ctx: Context, set: Set<String>) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
    }
}
