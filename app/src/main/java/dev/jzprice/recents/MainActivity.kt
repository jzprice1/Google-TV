package dev.jzprice.recents

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * No UI. Launching the app (from the launcher, a button remapper, or adb)
 * toggles the recent-apps overlay and exits immediately.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = SwitcherService.instance
        if (service != null) {
            service.toggle()
        } else {
            Toast.makeText(
                this,
                "Recents Switcher: accessibility service is not enabled (see README)",
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
    }
}
