package dev.jzprice.recents

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible entry point: shows the switcher and exits immediately.
 * Useful for testing from adb and for button-remapper apps that can launch an activity:
 *   adb shell am start -n dev.jzprice.recents/.TriggerActivity
 */
class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = SwitcherService.instance
        if (service != null) {
            service.show()
        } else {
            Toast.makeText(this, "Enable the Recents Switcher accessibility service first", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
