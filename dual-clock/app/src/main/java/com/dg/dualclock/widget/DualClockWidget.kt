package com.dg.dualclock.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dg.dualclock.MainActivity
import com.dg.dualclock.R
import com.dg.dualclock.core.CallHours
import com.dg.dualclock.core.City
import com.dg.dualclock.core.CityModel
import com.dg.dualclock.core.WidgetModel
import com.dg.dualclock.core.buildModel
import java.time.Instant

// ---- Design tokens (see docs/handoff/design/tokens.json) ----
object Tok {
    val Surface = Color(0xFF16181D)
    val Track = Color(0xFF2A2E36)
    val Window = Color(0xFF5E8C6A)
    val Marker = Color(0xFFFFFFFF)
    val Text = Color(0xFFF3EFE6)
    val Muted = Color(0xFFA3A7AF)
    val Perth = Color(0xFFF0A060)
    val Galway = Color(0xFF86B8E6)
    val DotOpen = Color(0xFF9FD3AE)
    val DotClosed = Color(0xFF6B7079)
}

// ---- Per-widget Glance state keys (written by refreshAll in Refresh.kt) ----
object Keys {
    val OPEN = intPreferencesKey("open_hour")
    val CLOSE = intPreferencesKey("close_hour")
    val PRIMARY = stringPreferencesKey("primary") // "PERTH" | "GALWAY"
    val TICK = longPreferencesKey("tick")         // written on every refresh to force recomposition
}

class DualClockWidget : GlanceAppWidget() {

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 60.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val MEDIUM_TALL = DpSize(250.dp, 150.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, MEDIUM, MEDIUM_TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            @Suppress("UNUSED_VARIABLE") val tick = prefs[Keys.TICK] // read so state writes recompose
            val hours = CallHours(prefs[Keys.OPEN] ?: 8, prefs[Keys.CLOSE] ?: 21)
            val primary = City.valueOf(prefs[Keys.PRIMARY] ?: City.PERTH.name)
            val model = buildModel(Instant.now(), hours, primary)

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .widgetShape()
                    .clickable(actionStartActivity<MainActivity>())
                    .semantics { contentDescription = model.contentDescription },
            ) {
                when (LocalSize.current) {
                    SMALL -> SmallLayout(model)
                    WIDE -> WideLayout(model)
                    MEDIUM -> MediumLayout(model, ticks = false)
                    else -> MediumLayout(model, ticks = true)
                }
            }
        }
    }
}

/** System widget radius on API 31+; a 24 dp rounded drawable before that (Glance cornerRadius is a no-op there). */
private fun GlanceModifier.widgetShape(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius).background(Tok.Surface)
    } else {
        background(ImageProvider(R.drawable.widget_background))
    }

class DualClockReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DualClockWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // super.onUpdate already holds this broadcast's goAsync() slot, so hand off to BandRefresh
        // instead of calling goAsync() a second time (it would return null).
        requestRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelRefresh(context) // last widget removed
    }
}

// ------------------------------------------------------------------------------------------------

private fun cityColor(c: City) = if (c == City.PERTH) Tok.Perth else Tok.Galway

/**
 * Minute digits: a TextClock bound to the city's IANA zone, embedded through AndroidRemoteViews.
 *
 * Why not recompose every minute? A Glance widget is not a live UI. Each recomposition is
 * serialised to RemoteViews and shipped to the launcher over IPC, which needs our process
 * alive and an alarm every minute to trigger it. That drains the battery and still drifts.
 * TextClock runs inside the launcher's process, redraws itself on the system's minute tick,
 * and resolves Europe/Dublin's IST/GMT switch on its own. Our process can stay dead.
 */
@Composable
fun ZoneClock(city: City, sizeSp: Float) {
    val context = LocalContext.current
    val layout = if (city == City.PERTH) R.layout.clock_perth else R.layout.clock_galway
    val rv = RemoteViews(context.packageName, layout).apply {
        setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    AndroidRemoteViews(rv)
}

@Composable
private fun Dot(color: Color) {
    Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(color)) {}
}

@Composable
private fun CityLabel(m: CityModel, small: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(cityColor(m.city))
        Spacer(GlanceModifier.width(6.dp))
        Text(
            if (small) m.smallLabel else m.label,
            style = TextStyle(color = ColorProvider(Tok.Muted), fontSize = 11.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusDot(open: Boolean) = Dot(if (open) Tok.DotOpen else Tok.DotClosed)

@Composable
private fun SmallLayout(m: WidgetModel) {
    Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
        CityLabel(m.primary, small = true)
        ZoneClock(m.primary.city, 28f)
        Spacer(GlanceModifier.defaultWeight())
        CityLabel(m.secondary, small = true)
        ZoneClock(m.secondary.city, 28f)
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(m.isOpen)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                m.statusShort,
                style = TextStyle(color = ColorProvider(Tok.Text), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WideLayout(m: WidgetModel) {
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column { CityLabel(m.primary); ZoneClock(m.primary.city, 26f) }
        Spacer(GlanceModifier.width(16.dp))
        Column { CityLabel(m.secondary); ZoneClock(m.secondary.city, 26f) }
        Spacer(GlanceModifier.defaultWeight())
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(m.isOpen)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    m.statusShort,
                    style = TextStyle(color = ColorProvider(Tok.Text), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            if (m.isOpen) {
                Text(m.statusDetail, style = TextStyle(color = ColorProvider(Tok.Muted), fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun MediumLayout(m: WidgetModel, ticks: Boolean) {
    val innerWidth = LocalSize.current.width - 32.dp
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(GlanceModifier.fillMaxWidth()) {
            for (c in listOf(m.primary, m.secondary)) {
                Column(GlanceModifier.defaultWeight()) {
                    CityLabel(c)
                    ZoneClock(c.city, 34f)
                    Text(c.dateLine, style = TextStyle(color = ColorProvider(Tok.Muted), fontSize = 11.sp), maxLines = 1)
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        m.band?.let { b ->
            if (ticks) TickRow(b.primaryTicks, cityColor(m.primary.city))
            // Band: track, window segment and "now" marker stacked in one Box. Fractions come from core.
            Box(GlanceModifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
                Box(GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp).background(Tok.Track)) {}
                Row {
                    Spacer(GlanceModifier.width(innerWidth * b.windowStart))
                    Box(
                        GlanceModifier.width(innerWidth * (b.windowEnd - b.windowStart)).height(8.dp)
                            .cornerRadius(4.dp).background(Tok.Window),
                    ) {}
                }
                Row {
                    Spacer(GlanceModifier.width((innerWidth * b.now - 1.dp).coerceAtLeast(0.dp)))
                    Box(GlanceModifier.width(2.dp).height(12.dp).background(Tok.Marker)) {}
                }
            }
            if (ticks) TickRow(b.secondaryTicks, cityColor(m.secondary.city))
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(m.isOpen)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                m.statusTitle,
                style = TextStyle(color = ColorProvider(Tok.Text), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(m.statusDetail, style = TextStyle(color = ColorProvider(Tok.Muted), fontSize = 11.sp), maxLines = 1)
        }
    }
}

@Composable
private fun TickRow(labels: List<String>, color: Color) {
    Row(GlanceModifier.fillMaxWidth()) {
        labels.forEach {
            Text(
                it,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(color), fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
        }
    }
}
