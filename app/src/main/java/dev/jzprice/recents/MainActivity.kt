package dev.jzprice.recents

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Setup / status screen. Also captures the remote button to use as the trigger. */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(64), dp(40), dp(64), dp(40))
        root.setBackgroundColor(0xFF121212.toInt())

        fun text(s: String, size: Float, color: Int): TextView {
            val t = TextView(this)
            t.text = s
            t.textSize = size
            t.setTextColor(color)
            t.setPadding(0, 0, 0, dp(12))
            return t
        }

        fun button(label: String, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = label
            b.isAllCaps = false
            b.setOnClickListener { onClick() }
            return b
        }

        root.addView(text("Recents Switcher", 28f, Color.WHITE))
        status = text("", 18f, 0xFFCCCCCC.toInt())
        root.addView(status)

        val wrap = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(button("Open Accessibility settings") { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, wrap)
        root.addView(button("Open Usage access settings") { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) }, wrap)
        root.addView(button("Show switcher now") {
            val s = SwitcherService.instance
            if (s != null) s.show() else toast("Accessibility service is not running yet")
        }, wrap)

        root.addView(
            text(
                "\nTo choose the trigger button, press it on your remote while this screen is open. " +
                    "D-pad, OK, Back, Home and volume keys can't be used. " +
                    "If the button does nothing here, the system keeps it for itself.",
                16f,
                0xFF999999.toInt(),
            ),
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0 && keyCode !in NOT_ASSIGNABLE) {
            Prefs.setTriggerKey(this, keyCode)
            refresh()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun refresh() {
        val key = Prefs.triggerKey(this)
        val keyText = if (key == 0) "not set - press a remote button now" else "${KeyEvent.keyCodeToString(key)} ($key)"
        status.text = "Accessibility service: ${if (serviceEnabled()) "ON" else "OFF"}\n" +
            "Usage access: ${if (RecentApps.hasUsageAccess(this)) "granted" else "NOT granted"}\n" +
            "Trigger button: $keyText"
    }

    private fun serviceEnabled(): Boolean {
        if (SwitcherService.instance != null) return true
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(this, SwitcherService::class.java)
        return enabled.contains(cn.flattenToString()) || enabled.contains(cn.flattenToShortString())
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("This TV has no settings page for that. Use the adb commands in the README.")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private val NOT_ASSIGNABLE = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
        )
    }
}
