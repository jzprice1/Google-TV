package dev.jzprice.livetv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favorite channels, kept in insertion order and stored as a full copy of each channel so the
 * favorites grid can show instantly at launch, before the playlist file has been parsed.
 * One instance is shared by every screen.
 */
class Favorites private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    private val map = LinkedHashMap<String, Channel>()

    init {
        val raw = prefs.getString(KEY, null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val c = Channel(
                        key = o.getString("k"),
                        name = o.getString("n"),
                        url = o.getString("u"),
                        logo = o.optString("l"),
                        group = o.optString("g"),
                    )
                    map[c.key] = c
                }
            } catch (e: Exception) {
                map.clear()
            }
        }
    }

    fun count(): Int = map.size

    fun isFavorite(key: String): Boolean = map.containsKey(key)

    fun list(): List<Channel> = map.values.toList()

    /** Adds or removes the channel. Returns true if it is now a favorite. */
    fun toggle(channel: Channel): Boolean {
        val nowFavorite: Boolean
        if (map.remove(channel.key) != null) {
            nowFavorite = false
        } else {
            map[channel.key] = channel
            nowFavorite = true
        }
        save()
        return nowFavorite
    }

    /** After a playlist refresh, update stored favorites with the newest URL, logo and name. */
    fun sync(all: List<Channel>) {
        if (map.isEmpty()) return
        val byKey = HashMap<String, Channel>(all.size * 2)
        for (c in all) byKey.putIfAbsent(c.key, c)
        var changed = false
        for (k in map.keys.toList()) {
            val fresh = byKey[k] ?: continue
            if (fresh != map[k]) {
                map[k] = fresh
                changed = true
            }
        }
        if (changed) save()
    }

    private fun save() {
        val arr = JSONArray()
        for (c in map.values) {
            arr.put(
                JSONObject()
                    .put("k", c.key)
                    .put("n", c.name)
                    .put("u", c.url)
                    .put("l", c.logo)
                    .put("g", c.group)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "favorites_v1"

        @Volatile
        private var instance: Favorites? = null

        fun get(context: Context): Favorites =
            instance ?: synchronized(this) {
                instance ?: Favorites(context.applicationContext).also { instance = it }
            }
    }
}
