package dev.jzprice.recents

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Accessibility service that draws a non-focusable overlay listing recent apps.
 *
 * The overlay never takes window focus, so the app underneath keeps playing.
 * While it is open we consume D-pad / OK / Back ourselves and drive the
 * selection manually.
 *
 * Keys while open:
 *   Up/Down  move          OK     launch (list) or run the option (menu)
 *   Right    open options  Left   back to the list
 *   Back     menu -> list, list -> close
 */
class SwitcherService : AccessibilityService() {

    private enum class Mode { LIST, MENU }

    private class Row(
        val title: String,
        val icon: Drawable? = null,
        val dim: Boolean = false,
        val isMessage: Boolean = false,
        val onSelect: (() -> Unit)? = null,
    )

    private class HiddenInfo(val pkg: String, val label: String, val icon: Drawable)

    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }

    private var overlay: View? = null
    private var listView: ListView? = null
    private var titleView: TextView? = null
    private var adapter: RowAdapter? = null

    private var entries: List<RecentApps.Entry> = emptyList()
    private var rows: List<Row> = emptyList()
    private var mode = Mode.LIST
    private var selected = 0
    private var listSelected = 0
    private var menuTarget: RecentApps.Entry? = null
    private var hasUsageAccess = false
    private var currentPkg: String? = null

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hide()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        hide()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED_PKGS) return
        currentPkg = pkg
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        // Only act while the overlay is open; everything else passes through untouched.
        if (overlay == null) return false

        // Overlay is open: keep it alive while the user is interacting.
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, AUTO_HIDE_MS)

        when (code) {
            KeyEvent.KEYCODE_BACK -> {
                if (!down) {
                    if (mode == Mode.MENU) backToList() else hide()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (down) move(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (down) move(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (down && event.repeatCount == 0 && mode == Mode.MENU) backToList()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down && event.repeatCount == 0 && mode == Mode.LIST) openMenu()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!down) rows.getOrNull(selected)?.onSelect?.invoke()
                return true
            }
        }
        return false // volume etc. pass through
    }

    /** Opens the overlay, or closes it if it is already open (so one mapped button toggles it). */
    fun toggle() {
        if (overlay != null) hide() else show()
    }

    fun show() {
        if (overlay != null) return
        hasUsageAccess = RecentApps.hasUsageAccess(this)
        entries = loadEntries()

        val view = buildOverlay()
        // Start on the previous app if the first entry is the one on screen.
        showList(if (entries.size > 1 && entries[0].isCurrent) 1 else 0)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, lp)
            overlay = view
            handler.removeCallbacks(autoHide)
            handler.postDelayed(autoHide, AUTO_HIDE_MS)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            listView = null
            titleView = null
            adapter = null
        }
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        val v = overlay ?: return
        overlay = null
        listView = null
        titleView = null
        adapter = null
        menuTarget = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "removeView: $e")
        }
    }

    private fun loadEntries(): List<RecentApps.Entry> {
        if (!hasUsageAccess) return emptyList()
        return try {
            RecentApps.load(this, currentPkg)
        } catch (e: Exception) {
            Log.e(TAG, "load failed", e)
            emptyList()
        }
    }

    // ---- list / menu content -------------------------------------------------

    private fun showList(sel: Int) {
        mode = Mode.LIST
        val r = ArrayList<Row>()
        if (entries.isEmpty()) {
            val msg = if (hasUsageAccess) {
                "No recent apps yet. Open a few apps, then try again."
            } else {
                "Usage access not granted. Run the appops step from the README."
            }
            r.add(Row(msg, isMessage = true))
        } else {
            for (e in entries) {
                r.add(Row(e.label, e.icon, dim = e.isCurrent, onSelect = { launch(e) }))
            }
        }
        applyRows("Recent apps", r, sel.coerceIn(0, maxOf(r.size - 1, 0)))
    }

    private fun openMenu() {
        val target = entries.getOrNull(selected) ?: return
        listSelected = selected
        menuTarget = target
        showMenu(0)
    }

    private fun showMenu(sel: Int) {
        val target = menuTarget ?: return
        mode = Mode.MENU
        val r = ArrayList<Row>()
        r.add(
            Row("Hide ${target.label}", target.icon, onSelect = {
                HiddenApps.hide(this, target.pkg)
                entries = loadEntries()
                backToList()
            }),
        )
        for (h in hiddenApps()) {
            r.add(
                Row("Show ${h.label}", h.icon, onSelect = {
                    HiddenApps.unhide(this, h.pkg)
                    entries = loadEntries()
                    showMenu(selected)
                }),
            )
        }
        applyRows("Options", r, sel.coerceIn(0, r.size - 1))
    }

    private fun hiddenApps(): List<HiddenInfo> {
        val pm = packageManager
        val out = ArrayList<HiddenInfo>()
        for (pkg in HiddenApps.all(this)) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                out.add(HiddenInfo(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info)))
            } catch (e: PackageManager.NameNotFoundException) {
                // Uninstalled; leave it in storage, just don't offer it.
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun backToList() {
        val idx = entries.indexOfFirst { it.pkg == menuTarget?.pkg }
        menuTarget = null
        showList(if (idx >= 0) idx else listSelected)
    }

    private fun launch(entry: RecentApps.Entry) {
        hide()
        try {
            startActivity(entry.launch)
        } catch (e: Exception) {
            Log.e(TAG, "launch failed for ${entry.pkg}", e)
        }
    }

    private fun move(delta: Int) {
        val n = rows.size
        if (n == 0) return
        selected = (selected + delta + n) % n
        adapter?.notifyDataSetChanged()
        listView?.smoothScrollToPosition(selected)
    }

    // ---- drawing -------------------------------------------------------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rowHeight(row: Row): Int = dp(if (row.isMessage) MESSAGE_DP else ROW_DP)

    private fun applyRows(title: String, newRows: List<Row>, sel: Int) {
        rows = newRows
        selected = sel
        titleView?.text = title
        adapter?.notifyDataSetChanged()
        val list = listView ?: return
        val total = rows.sumOf { rowHeight(it) }
        val max = (resources.displayMetrics.heightPixels * 0.7).toInt()
        list.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, minOf(total, max))
        list.post { list.setSelection(selected) }
    }

    private fun buildOverlay(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(0x99000000.toInt())

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(12), dp(16), dp(12), dp(16))
        panel.background = GradientDrawable().apply {
            setColor(0xF2202124.toInt())
            cornerRadius = dp(16).toFloat()
        }

        val title = TextView(this)
        title.setTextColor(Color.WHITE)
        title.textSize = 18f
        title.setPadding(dp(10), 0, 0, dp(12))
        panel.addView(title)

        val a = RowAdapter()
        val list = ListView(this)
        list.divider = null
        list.isFocusable = false
        list.adapter = a
        panel.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_DP)))

        adapter = a
        listView = list
        titleView = title

        root.addView(panel, FrameLayout.LayoutParams(dp(PANEL_DP), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        return root
    }

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = rows[position]
            val row = LinearLayout(this@SwitcherService)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(10), 0, dp(10), 0)
            row.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight(item))
            if (position == selected && !item.isMessage) {
                row.background = GradientDrawable().apply {
                    setColor(0xFF3B82F6.toInt())
                    cornerRadius = dp(10).toFloat()
                }
            }

            val icon = item.icon
            if (icon != null) {
                val iv = ImageView(this@SwitcherService)
                iv.setImageDrawable(icon)
                row.addView(iv, LinearLayout.LayoutParams(dp(36), dp(36)))
            }

            val label = TextView(this@SwitcherService)
            label.text = item.title
            label.ellipsize = TextUtils.TruncateAt.END
            if (item.isMessage) {
                label.setTextColor(0xFFBBBBBB.toInt())
                label.textSize = 14f
                label.maxLines = 4
            } else {
                label.setTextColor(if (item.dim && position != selected) 0xFF9AA0A6.toInt() else Color.WHITE)
                label.textSize = 17f
                label.maxLines = 1
                label.setPadding(if (icon != null) dp(12) else 0, 0, 0, 0)
            }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }
    }

    companion object {
        private const val TAG = "RecentsSwitcher"
        private const val PANEL_DP = 240
        private const val ROW_DP = 60
        private const val MESSAGE_DP = 96
        private const val AUTO_HIDE_MS = 30_000L
        private val IGNORED_PKGS = setOf("com.android.systemui", "android")

        @Volatile
        var instance: SwitcherService? = null
    }
}
