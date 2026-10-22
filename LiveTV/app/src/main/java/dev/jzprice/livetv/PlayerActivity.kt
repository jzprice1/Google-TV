package dev.jzprice.livetv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Full-screen playback.
 *  - Up / Down (or Channel +/-) switch channel within the list you came from.
 *  - OK shows the channel name; long-press OK toggles its favorite star.
 *  - Back returns to the grid.
 */
class PlayerActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var favorites: Favorites
    private lateinit var prefs: Prefs

    private val handler = Handler(Looper.getMainLooper())
    private val hideTitle = Runnable { titleView.visibility = View.GONE }
    private val retry = Runnable { player?.prepare() }

    private var player: ExoPlayer? = null
    private var queue: List<Channel> = emptyList()
    private var index = 0
    private var retries = 0
    private var triedHls = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        playerView = findViewById<PlayerView>(R.id.playerView)
        titleView = findViewById<TextView>(R.id.title)
        statusView = findViewById<TextView>(R.id.status)
        spinner = findViewById<ProgressBar>(R.id.spinner)
        favorites = Favorites.get(this)
        prefs = Prefs(this)

        queue = Repo.playQueue
        index = intent.getIntExtra(EXTRA_INDEX, 0)
        if (queue.isEmpty() || index !in queue.indices) {
            // The process was restarted and the list we were playing from is gone.
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (isFinishing || queue.isEmpty() || index !in queue.indices) return
        initPlayer()
        playCurrent()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---- Player -----------------------------------------------------------------------------

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(prefs.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
            .build()
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
        p.addListener(listener)
        playerView.player = p
        player = p
    }

    private fun releasePlayer() {
        handler.removeCallbacks(retry)
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        playerView.player = null
        player = null
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            spinner.visibility = if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            if (playbackState == Player.STATE_READY) {
                statusView.visibility = View.GONE
                retries = 0
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            // Some providers serve HLS from URLs without a .m3u8 ending; try that once.
            if (!triedHls && error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                triedHls = true
                p.setMediaItem(
                    MediaItem.Builder()
                        .setUri(queue[index].url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                )
                p.prepare()
                p.playWhenReady = true
                return
            }
            if (retries < MAX_RETRIES) {
                retries++
                statusView.text = getString(R.string.player_retrying)
                statusView.visibility = View.VISIBLE
                handler.removeCallbacks(retry)
                handler.postDelayed(retry, 2000)
            } else {
                spinner.visibility = View.GONE
                statusView.text = getString(R.string.player_error, error.errorCodeName)
                statusView.visibility = View.VISIBLE
            }
        }
    }

    private fun playCurrent() {
        val channel = queue[index]
        retries = 0
        triedHls = false
        handler.removeCallbacks(retry)
        statusView.visibility = View.GONE
        showTitle()
        player?.let {
            it.setMediaItem(MediaItem.fromUri(channel.url))
            it.prepare()
            it.playWhenReady = true
        }
    }

    private fun step(delta: Int) {
        index = (index + delta + queue.size) % queue.size
        playCurrent()
    }

    private fun showTitle() {
        val channel = queue[index]
        val star = if (favorites.isFavorite(channel.key)) "  ★" else ""
        titleView.text = "${index + 1}/${queue.size}   ${channel.name}$star"
        titleView.visibility = View.VISIBLE
        handler.removeCallbacks(hideTitle)
        handler.postDelayed(hideTitle, 4000)
    }

    private fun toggleFavorite() {
        val channel = queue[index]
        val nowFavorite = favorites.toggle(channel)
        Toast.makeText(
            this,
            getString(
                if (nowFavorite) R.string.added_favorite else R.string.removed_favorite,
                channel.name,
            ),
            Toast.LENGTH_SHORT,
        ).show()
        showTitle()
    }

    // ---- Keys -------------------------------------------------------------------------------

    private fun isConfirm(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (queue.isEmpty()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (event.repeatCount == 0) step(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (event.repeatCount == 0) step(-1)
                return true
            }
        }
        if (isConfirm(keyCode)) {
            if (event.repeatCount == 0) event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (queue.isNotEmpty() && isConfirm(keyCode)) {
            toggleFavorite()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (queue.isNotEmpty() && isConfirm(keyCode) && event.isTracking && !event.isCanceled) {
            showTitle()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        const val EXTRA_INDEX = "index"
        private const val MAX_RETRIES = 2
    }
}
