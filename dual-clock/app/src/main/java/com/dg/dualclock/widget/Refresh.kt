package com.dg.dualclock.widget

// Refresh pipeline: one entry point (refreshAll) used by the alarm, system broadcasts,
// widget lifecycle callbacks and the settings screen (SPEC R1–R4).

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.dg.dualclock.core.CallHours
import com.dg.dualclock.core.buildModel
import com.dg.dualclock.core.callWindow
import com.dg.dualclock.core.nextRefresh
import com.dg.dualclock.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

const val TAG = "DualClock"
const val ACTION_REFRESH = "com.dg.dualclock.action.REFRESH"

/** Copy current settings + a fresh tick into every placed widget's state, recompose, then schedule the next refresh. */
suspend fun refreshAll(context: Context, reason: String) {
    val settings = SettingsRepository(context).current()
    val manager = GlanceAppWidgetManager(context)
    val widget = DualClockWidget()
    val ids = manager.getGlanceIds(DualClockWidget::class.java)
    for (id in ids) {
        updateAppWidgetState(context, id) { prefs ->
            prefs[Keys.OPEN] = settings.openHour
            prefs[Keys.CLOSE] = settings.closeHour
            prefs[Keys.PRIMARY] = settings.primary.name
            prefs[Keys.TICK] = System.currentTimeMillis()
        }
        widget.update(context, id)
    }
    if (ids.isEmpty()) {
        cancelRefresh(context)
        Log.i(TAG, "refresh[$reason] no widgets placed; alarm cancelled")
        return
    }
    val now = Instant.now()
    val next = scheduleNext(context, settings.hours, now)
    // One line per refresh. VERIFICATION.md and the emulator CI job read these.
    val m = buildModel(now, settings.hours, settings.primary)
    Log.i(
        TAG,
        "refresh[$reason] widgets=${ids.size} | ${m.contentDescription} | " +
            "${m.primary.dateLine} | ${m.secondary.dateLine} | " +
            "band=${callWindow(now, settings.hours, settings.primary)} | next=$next",
    )
}

private fun refreshIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context, 0, Intent(context, BandRefresh::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/**
 * Arms the single refresh alarm at core.nextRefresh (window edge, clock change, midnight in
 * either city, or the 15-minute marker step, whichever is first).
 *
 * RTC, not RTC_WAKEUP: the alarm never wakes the device. If it falls due while the screen is
 * off, it fires on the next wake, which is the first moment anyone can see the widget anyway.
 * setWindow is inexact (a 60 s window), so no SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM permission
 * is needed, and the system may batch it with other alarms. A status flipping up to a minute
 * late is invisible next to the TextClock digits, which tick on their own.
 *
 * The same PendingIntent is reused, so each call replaces the previous alarm: at most one is pending.
 */
fun scheduleNext(context: Context, hours: CallHours, now: Instant = Instant.now()): Instant {
    val at = nextRefresh(now, hours)
    context.getSystemService(AlarmManager::class.java)
        .setWindow(AlarmManager.RTC, at.toEpochMilli(), 60_000L, refreshIntent(context))
    return at
}

fun cancelRefresh(context: Context) {
    context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
}

/** Asks BandRefresh to run refreshAll. Used where goAsync() is unavailable (see DualClockReceiver). */
fun requestRefresh(context: Context) {
    context.sendBroadcast(Intent(context, BandRefresh::class.java).setAction(ACTION_REFRESH))
}

/** Handles our alarm plus TIMEZONE_CHANGED, TIME_SET, BOOT_COMPLETED, MY_PACKAGE_REPLACED. */
class BandRefresh : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val reason = intent.action?.substringAfterLast('.') ?: "unknown"
        CoroutineScope(Dispatchers.Default).launch {
            try {
                refreshAll(context.applicationContext, reason)
            } finally {
                pending.finish()
            }
        }
    }
}
