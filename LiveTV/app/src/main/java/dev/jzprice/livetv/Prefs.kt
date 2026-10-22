package dev.jzprice.livetv

import android.content.Context

/** Small settings store: the playlist URL and the User-Agent used for downloading and playback. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var playlistUrl: String
        get() = p.getString(KEY_URL, "") ?: ""
        set(value) {
            p.edit().putString(KEY_URL, value).apply()
        }

    var userAgent: String
        get() = (p.getString(KEY_UA, "") ?: "").ifBlank { DEFAULT_UA }
        set(value) {
            p.edit().putString(KEY_UA, value).apply()
        }

    companion object {
        const val FILE = "livetv"
        const val DEFAULT_UA = "VLC/3.0.20 LibVLC/3.0.20"
        private const val KEY_URL = "playlist_url"
        private const val KEY_UA = "user_agent"
    }
}
