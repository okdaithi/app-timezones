package com.dg.dualclock

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dg.dualclock.core.City
import com.dg.dualclock.data.Settings
import com.dg.dualclock.data.SettingsRepository
import com.dg.dualclock.widget.DualClockReceiver
import com.dg.dualclock.widget.DualClockWidget
import com.dg.dualclock.widget.Keys
import com.dg.dualclock.widget.refreshAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator-only helpers for scripts/emulator-checks.sh. There is no launcher to drag a widget
 * onto in CI, so these bind one to a private AppWidgetHost. The binding outlives the test process,
 * which is what the later checks need: a placed widget with the app process dead.
 *
 * Needs `adb shell appwidget grantbind --package com.dg.dualclock` first.
 */
@RunWith(AndroidJUnit4::class)
class WidgetHostTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val provider = ComponentName(context, DualClockReceiver::class.java)

    @Test fun placeWidget() {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.getAppWidgetIds(provider).isEmpty()) {
            val id = AppWidgetHost(context, HOST_ID).allocateAppWidgetId()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
            }
            assertTrue("bind refused; run appwidget grantbind first", manager.bindAppWidgetIdIfAllowed(id, provider, options))
        }
        runBlocking {
            refreshAll(context, "test-place")
            assertTrue(GlanceAppWidgetManager(context).getGlanceIds(DualClockWidget::class.java).isNotEmpty())
        }
    }

    /** Acceptance 10: a settings change reaches every placed widget's state. Restores defaults afterwards. */
    @Test fun settingsReachEveryWidget() = runBlocking {
        val repo = SettingsRepository(context)
        val changed = Settings(openHour = 9, closeHour = 20, primary = City.GALWAY)
        try {
            repo.save(changed)
            refreshAll(context, "test-settings")
            val ids = GlanceAppWidgetManager(context).getGlanceIds(DualClockWidget::class.java)
            assertTrue(ids.isNotEmpty())
            for (id in ids) {
                val prefs: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                assertEquals(9, prefs[Keys.OPEN])
                assertEquals(20, prefs[Keys.CLOSE])
                assertEquals("GALWAY", prefs[Keys.PRIMARY])
            }
        } finally {
            repo.save(Settings())
            refreshAll(context, "test-settings-restore")
        }
    }

    private companion object {
        const val HOST_ID = 4242
    }
}
