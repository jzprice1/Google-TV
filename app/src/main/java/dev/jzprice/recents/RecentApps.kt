package dev.jzprice.recents

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.Log

/** Builds the list of recently used apps from usage-stats events. */
object RecentApps {

    data class Entry(
        val pkg: String,
        val label: String,
        val icon: Drawable,
        val launch: Intent,
        val isCurrent: Boolean,
    )

    private const val TAG = "RecentsSwitcher"
    private const val LOOKBACK_MS = 3L * 24 * 60 * 60 * 1000
    private val ALWAYS_SKIP = setOf("com.android.systemui", "android")

    @Suppress("DEPRECATION")
    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    fun load(ctx: Context, currentPkg: String?, limit: Int = 12): List<Entry> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = ctx.packageManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - LOOKBACK_MS, end)

        // package -> time of its most recent move to foreground
        val lastUsed = HashMap<String, Long>()
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val prev = lastUsed[ev.packageName] ?: 0L
                if (ev.timeStamp > prev) lastUsed[ev.packageName] = ev.timeStamp
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePkgs = pm.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.toSet()

        val hidden = HiddenApps.all(ctx)
        val result = ArrayList<Entry>()
        for ((pkg, _) in lastUsed.entries.sortedByDescending { it.value }) {
            if (pkg == ctx.packageName || pkg in homePkgs || pkg in ALWAYS_SKIP || pkg in hidden) continue
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                result.add(
                    Entry(
                        pkg = pkg,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info),
                        launch = launch,
                        isCurrent = pkg == currentPkg,
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "skipping $pkg: $e")
            }
            if (result.size >= limit) break
        }
        return result
    }
}
