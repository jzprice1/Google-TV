package dev.jzprice.recents

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
 * Accessibility service that (1) sees remote key presses globally, and
 * (2) draws a non-focusable overlay listing recent apps.
 *
 * The overlay never takes window focus, so the app underneath keeps playing.
 * While it is open we consume D-pad / OK / Back ourselves and drive the
 * selection manually.
 */
class SwitcherService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }

    private var overlay: View? = null
    private var listView: ListView? = null
    private var adapter: EntryAdapter? = null
    private var entries: List<RecentApps.Entry> = emptyList()
    private var selected = 0
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
        val trigger = Prefs.triggerKey(this)

        if (down && event.repeatCount == 0) {
            Log.d(TAG, "key ${KeyEvent.keyCodeToString(code)} ($code)")
        }

        if (overlay == null) {
            if (trigger != 0 && code == trigger) {
                if (down && event.repeatCount == 0) show()
                return true
            }
            return false
        }

        // Overlay is open: keep it alive while the user is interacting.
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, AUTO_HIDE_MS)

        if (trigger != 0 && code == trigger) {
            if (down && event.repeatCount == 0) hide()
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_BACK -> {
                if (!down) hide()
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
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> return true
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!down) activateSelected()
                return true
            }
        }
        return false // volume etc. pass through
    }

    fun show() {
        if (overlay != null) return
        val hasAccess = RecentApps.hasUsageAccess(this)
        entries = if (hasAccess) {
            try {
                RecentApps.load(this, currentPkg)
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                emptyList()
            }
        } else {
            emptyList()
        }
        // Start on the previous app if the first entry is the one on screen.
        selected = if (entries.size > 1 && entries[0].isCurrent) 1 else 0

        val view = buildOverlay(hasAccess)
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
            if (entries.isNotEmpty()) listView?.post { listView?.setSelection(selected) }
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
        }
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        val v = overlay ?: return
        overlay = null
        listView = null
        adapter = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "removeView: $e")
        }
    }

    private fun move(delta: Int) {
        val n = entries.size
        if (n == 0) return
        selected = (selected + delta + n) % n
        adapter?.notifyDataSetChanged()
        listView?.smoothScrollToPosition(selected)
    }

    private fun activateSelected() {
        val entry = entries.getOrNull(selected)
        hide()
        if (entry == null) return
        try {
            startActivity(entry.launch)
        } catch (e: Exception) {
            Log.e(TAG, "launch failed for ${entry.pkg}", e)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildOverlay(hasUsageAccess: Boolean): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(0x99000000.toInt())

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(20), dp(16), dp(20), dp(16))
        panel.background = GradientDrawable().apply {
            setColor(0xF2202124.toInt())
            cornerRadius = dp(16).toFloat()
        }

        val title = TextView(this)
        title.text = "Recent apps"
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.setPadding(dp(12), 0, 0, dp(12))
        panel.addView(title)

        if (entries.isEmpty()) {
            val msg = TextView(this)
            msg.text = if (hasUsageAccess) {
                "No recent apps yet. Open a few apps, then try again."
            } else {
                "Usage access is not granted.\nRun: adb shell appops set $packageName GET_USAGE_STATS allow"
            }
            msg.setTextColor(0xFFBBBBBB.toInt())
            msg.textSize = 16f
            msg.setPadding(dp(12), dp(8), dp(12), dp(8))
            panel.addView(msg)
        } else {
            val a = EntryAdapter()
            val list = ListView(this)
            list.divider = null
            list.isFocusable = false
            list.adapter = a
            val rowsHeight = entries.size * dp(ROW_DP)
            val maxHeight = (resources.displayMetrics.heightPixels * 0.7).toInt()
            panel.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, minOf(rowsHeight, maxHeight)))
            adapter = a
            listView = list
        }

        root.addView(
            panel,
            FrameLayout.LayoutParams(dp(440), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dp(48)
            },
        )
        return root
    }

    private inner class EntryAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val entry = entries[position]
            val row = LinearLayout(this@SwitcherService)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), 0, dp(12), 0)
            row.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_DP))
            if (position == selected) {
                row.background = GradientDrawable().apply {
                    setColor(0xFF3B82F6.toInt())
                    cornerRadius = dp(10).toFloat()
                }
            }

            val icon = ImageView(this@SwitcherService)
            icon.setImageDrawable(entry.icon)
            row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

            val label = TextView(this@SwitcherService)
            label.text = if (entry.isCurrent) "${entry.label}  (current)" else entry.label
            label.setTextColor(Color.WHITE)
            label.textSize = 18f
            label.maxLines = 1
            label.setPadding(dp(16), 0, 0, 0)
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }
    }

    companion object {
        private const val TAG = "RecentsSwitcher"
        private const val ROW_DP = 64
        private const val AUTO_HIDE_MS = 30_000L
        private val IGNORED_PKGS = setOf("com.android.systemui", "android")

        @Volatile
        var instance: SwitcherService? = null
    }
}
