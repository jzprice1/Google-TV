package dev.jzprice.livetv

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Holds the loaded channel list in memory, plus the queue the player steps through.
 * The playlist itself is cached as a file so the app starts offline and without re-downloading.
 */
object Repo {
    private const val FILE_NAME = "playlist.m3u"
    private const val TMP_NAME = "playlist.tmp"
    private const val STALE_MS = 24L * 60 * 60 * 1000

    @Volatile
    var all: List<Channel> = emptyList()
        private set

    /** Groups in the order they first appear in the playlist. */
    @Volatile
    var groups: Map<String, List<Channel>> = emptyMap()
        private set

    /** The list the player is stepping through (set by whichever screen starts playback). */
    @Volatile
    var playQueue: List<Channel> = emptyList()

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun isStale(context: Context): Boolean {
        val f = file(context)
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > STALE_MS
    }

    /** Parses the saved playlist (if any). [done] runs on the main thread with the channel count. */
    fun loadCached(context: Context, done: (Int) -> Unit) {
        if (all.isNotEmpty()) {
            done(all.size)
            return
        }
        val app = context.applicationContext
        io.execute {
            var count = 0
            val f = file(app)
            if (f.exists()) {
                try {
                    val list = f.bufferedReader().use { M3uParser.parse(it) }
                    publish(list)
                    count = list.size
                } catch (e: Exception) {
                    // A damaged cache is not fatal; the user can refresh from Settings.
                }
            }
            main.post { done(count) }
        }
    }

    /** Downloads and parses the playlist. The saved copy is only replaced if the new one has channels. */
    fun refresh(context: Context, url: String, userAgent: String, done: (Result<Int>) -> Unit) {
        val app = context.applicationContext
        io.execute {
            val result = runCatching {
                val tmp = File(app.filesDir, TMP_NAME)
                download(url, userAgent, tmp)
                val list = tmp.bufferedReader().use { M3uParser.parse(it) }
                if (list.isEmpty()) {
                    tmp.delete()
                    throw IOException("No channels found. Check that this is an M3U playlist URL.")
                }
                val dest = file(app)
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) throw IOException("Couldn't save the playlist on this device.")
                publish(list)
                list.size
            }
            main.post { done(result) }
        }
    }

    private fun publish(list: List<Channel>) {
        val g = LinkedHashMap<String, MutableList<Channel>>()
        for (c in list) g.getOrPut(c.group) { ArrayList() }.add(c)
        groups = g
        all = list
    }

    private fun download(urlString: String, userAgent: String, dest: File) {
        var current = URL(urlString)
        var redirects = 0
        while (true) {
            val conn = current.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "*/*")
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Redirect without a location (HTTP $code)")
                    redirects++
                    if (redirects > 5) throw IOException("Too many redirects")
                    current = URL(current, location)
                    continue
                }
                if (code !in 200..299) {
                    throw IOException("The server answered HTTP $code. If it is 403 or 401, check the URL or try another User-Agent in Settings.")
                }
                conn.inputStream.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                return
            } finally {
                conn.disconnect()
            }
        }
    }
}
