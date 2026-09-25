package com.dg.dualclock.widget

// REFERENCE, NOT COMPILED IN THIS PACKAGE. Refresh pipeline: one entry point (refreshAll) used by
// the alarm, system broadcasts, widget lifecycle callbacks and the settings screen.

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.dg.dualclock.core.CallHours
import com.dg.dualclock.core.nextRefresh
import com.dg.dualclock.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/** Copy current settings + a fresh tick into every placed widget's state, recompose, then schedule the next refresh. */
suspend fun refreshAll(context: Context) {
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
    if (ids.isEmpty()) cancelRefresh(context)
    else scheduleNext(context, CallHours(settings.openHour, settings.closeHour))
}

private fun refreshIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context, 0, Intent(context, BandRefresh::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

const val ACTION_REFRESH = "com.dg.dualclock.action.REFRESH"

fun scheduleNext(context: Context, hours: CallHours) {
    val at: Instant = nextRefresh(Instant.now(), hours)
    // RTC (not RTC_WAKEUP): never wakes the device; fires on next wake if due while asleep.
    // setWindow is inexact, so no SCHEDULE_EXACT_ALARM permission is needed.
    context.getSystemService(AlarmManager::class.java)
        .setWindow(AlarmManager.RTC, at.toEpochMilli(), 60_000L, refreshIntent(context))
}

fun cancelRefresh(context: Context) {
    context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
}

/** Handles our alarm plus TIMEZONE_CHANGED, TIME_SET, BOOT_COMPLETED, MY_PACKAGE_REPLACED. */
class BandRefresh : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/*
 * In DualClockReceiver (DualClockWidget.kt) also override:
 *
 *   override fun onUpdate(context, appWidgetManager, appWidgetIds) {
 *       super.onUpdate(context, appWidgetManager, appWidgetIds)
 *       // launch refreshAll(context) via goAsync() as in BandRefresh
 *   }
 *   override fun onDisabled(context) { super.onDisabled(context); cancelRefresh(context) }
 */
