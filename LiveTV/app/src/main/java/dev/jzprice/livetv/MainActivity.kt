package dev.jzprice.livetv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Home screen: categories on the left, a channel grid on the right.
 * It opens on Favorites (or All channels while there are no favorites yet).
 */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var favorites: Favorites
    private lateinit var categoriesView: RecyclerView
    private lateinit var channelsView: RecyclerView
    private lateinit var searchBox: EditText
    private lateinit var statusView: TextView
    private lateinit var emptyView: TextView
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val applySearch = Runnable { showCurrent() }

    private var currentList: List<Channel> = emptyList()
    private var selectedId = FAV_ID
    private var loading = false
    private var resumedOnce = false
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        favorites = Favorites.get(this)

        categoriesView = findViewById<RecyclerView>(R.id.categories)
        channelsView = findViewById<RecyclerView>(R.id.channels)
        searchBox = findViewById<EditText>(R.id.search)
        statusView = findViewById<TextView>(R.id.status)
        emptyView = findViewById<TextView>(R.id.empty)

        categoryAdapter = CategoryAdapter { id -> onCategoryChosen(id) }
        channelAdapter = ChannelAdapter(
            favorites,
            onClick = { index -> play(index) },
            onLongClick = { index -> toggleFavorite(index) },
        )

        categoriesView.layoutManager = LinearLayoutManager(this)
        categoriesView.adapter = categoryAdapter
        categoriesView.itemAnimator = null
        channelsView.layoutManager = GridLayoutManager(this, GRID_SPAN)
        channelsView.adapter = channelAdapter
        channelsView.itemAnimator = null

        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettings() }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                handler.removeCallbacks(applySearch)
                handler.postDelayed(applySearch, 250)
            }
        })
        searchBox.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                focusGrid()
                true
            } else {
                false
            }
        }

        // Favorites are stored in full, so they can be shown right away, before the playlist is parsed.
        selectedId = if (favorites.count() > 0) FAV_ID else ALL_ID
        rebuildCategories()
        showCurrent()
        if (favorites.count() > 0) focusGrid()

        setStatus(getString(R.string.loading_playlist))
        Repo.loadCached(this) { count -> onCachedLoaded(count) }
    }

    override fun onResume() {
        super.onResume()
        if (!resumedOnce) {
            resumedOnce = true
            return
        }
        // Back from the player: favorites may have changed there.
        rebuildCategories()
        if (selectedId == FAV_ID && searchBox.text.isNullOrBlank() && favorites.list() != currentList) {
            showCurrent()
        } else {
            channelAdapter.refreshAll()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        dialog?.dismiss()
        super.onDestroy()
    }

    // ---- Loading ----------------------------------------------------------------------------

    private fun onCachedLoaded(count: Int) {
        if (isFinishing || isDestroyed) return
        if (count > 0) {
            favorites.sync(Repo.all)
            if (selectedId == FAV_ID && favorites.count() == 0) selectedId = ALL_ID
            rebuildCategories()
            showCurrent()
            focusGrid()
        }
        val url = prefs.playlistUrl
        when {
            url.isBlank() -> {
                setStatus("")
                if (count == 0) showSettings()
            }
            count == 0 -> refresh(url, silent = false)
            Repo.isStale(this) -> refresh(url, silent = true)
            else -> setStatus("")
        }
    }

    private fun refresh(url: String, silent: Boolean) {
        if (loading) return
        loading = true
        setStatus(getString(R.string.loading_playlist))
        Repo.refresh(this, url, prefs.userAgent) cb@{ result ->
            loading = false
            if (isFinishing || isDestroyed) return@cb
            result.onSuccess { count ->
                favorites.sync(Repo.all)
                if (selectedId == FAV_ID && favorites.count() == 0) selectedId = ALL_ID
                rebuildCategories()
                showCurrent()
                setStatus(getString(R.string.channels_loaded, count))
                handler.postDelayed({ setStatus("") }, 3000)
                if (!silent) focusGrid()
            }
            result.onFailure { e ->
                if (silent) {
                    setStatus(getString(R.string.refresh_failed_saved))
                } else {
                    setStatus("")
                    showError(e.message ?: e.toString())
                }
            }
        }
    }

    // ---- Lists ------------------------------------------------------------------------------

    private fun rebuildCategories() {
        val list = ArrayList<Category>()
        list.add(Category(FAV_ID, "★  Favorites (${favorites.count()})"))
        list.add(Category(ALL_ID, "All channels (${Repo.all.size})"))
        for ((group, channels) in Repo.groups) {
            list.add(Category(GROUP_PREFIX + group, "$group (${channels.size})"))
        }
        categoryAdapter.submit(list, selectedId)
    }

    private fun onCategoryChosen(id: String) {
        selectedId = id
        if (!searchBox.text.isNullOrEmpty()) searchBox.setText("")
        showCurrent()
    }

    private fun showCurrent() {
        val q = searchBox.text.toString().trim()
        currentList = when {
            q.isNotEmpty() -> Repo.all.filter { it.name.contains(q, ignoreCase = true) }
            selectedId == FAV_ID -> favorites.list()
            selectedId == ALL_ID -> Repo.all
            else -> Repo.groups[selectedId.removePrefix(GROUP_PREFIX)] ?: emptyList()
        }
        channelAdapter.submit(currentList)
        channelsView.scrollToPosition(0)
        if (currentList.isEmpty()) {
            emptyView.text = when {
                q.isNotEmpty() -> "No channels match \"$q\""
                selectedId == FAV_ID -> getString(R.string.no_favorites)
                Repo.all.isEmpty() -> getString(R.string.no_playlist)
                else -> getString(R.string.no_channels)
            }
            emptyView.visibility = View.VISIBLE
        } else {
            emptyView.visibility = View.GONE
        }
    }

    private fun focusGrid() {
        channelsView.postDelayed({
            if (dialog?.isShowing != true) {
                channelsView.getChildAt(0)?.requestFocus()
            }
        }, 150)
    }

    private fun focusPosition(position: Int) {
        if (currentList.isEmpty()) return
        val pos = position.coerceIn(0, currentList.size - 1)
        channelsView.scrollToPosition(pos)
        channelsView.postDelayed({
            channelsView.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
        }, 80)
    }

    // ---- Actions ----------------------------------------------------------------------------

    private fun play(index: Int) {
        if (index !in currentList.indices) return
        Repo.playQueue = currentList
        startActivity(
            Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_INDEX, index)
        )
    }

    private fun toggleFavorite(index: Int) {
        val channel = currentList.getOrNull(index) ?: return
        val nowFavorite = favorites.toggle(channel)
        toast(
            getString(
                if (nowFavorite) R.string.added_favorite else R.string.removed_favorite,
                channel.name,
            )
        )
        rebuildCategories()
        if (selectedId == FAV_ID && searchBox.text.isNullOrBlank()) {
            showCurrent()
            focusPosition(index)
        } else {
            channelAdapter.notifyItemChanged(index)
        }
    }

    // ---- Dialogs ----------------------------------------------------------------------------

    private fun showSettings() {
        if (dialog?.isShowing == true) return
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val urlBox = view.findViewById<EditText>(R.id.urlBox)
        val uaBox = view.findViewById<EditText>(R.id.uaBox)
        urlBox.setText(prefs.playlistUrl)
        uaBox.setText(prefs.userAgent)

        dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setPositiveButton(R.string.save_and_load) { _, _ ->
                val url = urlBox.text.toString().trim()
                if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                    toast(getString(R.string.enter_valid_url))
                } else {
                    prefs.userAgent = uaBox.text.toString().trim()
                    prefs.playlistUrl = url
                    refresh(url, silent = false)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showError(message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.load_failed_title)
            .setMessage(message)
            .setPositiveButton(R.string.settings) { _, _ -> showSettings() }
            .setNegativeButton(R.string.ok, null)
            .show()
    }

    private fun setStatus(text: String) {
        statusView.text = text
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    // ---- Keys -------------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            !searchBox.text.isNullOrEmpty() -> {
                searchBox.setText("")
                showCurrent()
            }
            selectedId != FAV_ID && favorites.count() > 0 -> {
                selectedId = FAV_ID
                categoryAdapter.select(FAV_ID)
                showCurrent()
                focusGrid()
            }
            else -> super.onBackPressed()
        }
    }

    companion object {
        private const val FAV_ID = "fav"
        private const val ALL_ID = "all"
        private const val GROUP_PREFIX = "g:"
        private const val GRID_SPAN = 5
    }
}
