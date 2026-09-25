package com.dg.dualclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dg.dualclock.core.City
import com.dg.dualclock.core.buildModel
import com.dg.dualclock.core.explain
import com.dg.dualclock.core.nextClockChange
import com.dg.dualclock.data.Settings
import com.dg.dualclock.data.SettingsRepository
import com.dg.dualclock.ui.AppTok
import com.dg.dualclock.ui.DualClockTheme
import com.dg.dualclock.ui.WidgetPreview
import com.dg.dualclock.widget.refreshAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository(applicationContext)
        setContent {
            DualClockTheme { MainScreen(repo) }
        }
    }
}

@Composable
private fun MainScreen(repo: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = Settings())

    // Wall clock for the in-app preview only. The app is on screen, so ticking every second is fine here.
    var wall by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            wall = Instant.now()
            delay(1_000)
        }
    }

    // SPEC A3: debug-only simulated offset. It feeds the preview below and nothing else;
    // the real widget always reads Instant.now() in provideGlance.
    var simSeconds by remember { mutableLongStateOf(0L) }
    val now = if (BuildConfig.DEBUG) wall.plusSeconds(simSeconds) else wall

    val model = buildModel(now, settings.hours, settings.primary)
    val explanation = explain(now, settings.hours, settings.primary)

    fun save(s: Settings) {
        scope.launch {
            repo.save(s)
            refreshAll(context.applicationContext, "settings") // SPEC A1: every placed widget, immediately
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTok.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("HOME-SCREEN WIDGET · JETPACK GLANCE", style = MaterialTheme.typography.labelSmall, color = AppTok.Muted)
        Text("Perth ⇄ Galway", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Green band: both cities between %02d:00 and %02d:00 local time. White line: now. ".format(
                settings.openHour, settings.closeHour % 24,
            ) + "Clocks read the IANA zones directly, so the band moves by itself when Ireland changes its clocks.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTok.Muted,
        )

        WidgetPreview(model, detail = explanation.countdown)

        if (BuildConfig.DEBUG) {
            SimulateTime(
                simSeconds = simSeconds,
                onChange = { simSeconds = it },
                onJump = {
                    // Land one day past the next clock change, at the same time of day.
                    nextClockChange(now)?.let { change ->
                        val days = (change.epochSecond - now.epochSecond) / 86_400 + 1
                        simSeconds += days * 86_400
                    }
                },
            )
        }

        Card {
            ExplainRow("Perth", "${explanation.perthOffset} · ${explanation.perthNote}")
            ExplainRow("Galway", "${explanation.galwayOffset} · ${explanation.galwayNote}")
            ExplainRow("Gap", explanation.gap)
            ExplainRow("Call window", explanation.window)
            ExplainRow("Next change", listOf(explanation.nextChange, explanation.nextChangeEffect).filter { it.isNotEmpty() }.joinToString("\n"))
        }

        Card {
            Text("Calling hours (local, both cities)", style = MaterialTheme.typography.titleMedium)
            HourStepper(
                label = "Open",
                value = settings.openHour,
                canDecrease = settings.openHour > 0,
                canIncrease = settings.openHour + 1 < settings.closeHour,
                onChange = { save(settings.copy(openHour = it)) },
            )
            HourStepper(
                label = "Close",
                value = settings.closeHour,
                canDecrease = settings.closeHour - 1 > settings.openHour,
                canIncrease = settings.closeHour < 24,
                onChange = { save(settings.copy(closeHour = it)) },
            )
            Spacer(Modifier.height(4.dp))
            Text("Primary city (left, and the band's clock)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (city in City.entries) {
                    FilterChip(
                        selected = settings.primary == city,
                        onClick = { save(settings.copy(primary = city)) },
                        label = { Text(city.displayName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppTok.Card, RoundedCornerShape(16.dp))
            .border(1.dp, AppTok.CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun ExplainRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.width(104.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AppTok.Muted)
    }
}

@Composable
private fun HourStepper(
    label: String,
    value: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        OutlinedButton(onClick = { onChange(value - 1) }, enabled = canDecrease) { Text("−") }
        Text("%02d:00".format(value % 24), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedButton(onClick = { onChange(value + 1) }, enabled = canIncrease) { Text("+") }
    }
}

@Composable
private fun SimulateTime(simSeconds: Long, onChange: (Long) -> Unit, onJump: () -> Unit) {
    val dayPart = simSeconds / 86_400
    val hourPart = (simSeconds % 86_400) / 3_600f
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Simulate time (debug build, preview only)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (simSeconds == 0L) "LIVE" else "SIM " + (if (dayPart != 0L) "+${dayPart}d " else "") + "%+.2fh".format(hourPart),
                style = MaterialTheme.typography.labelSmall,
                color = AppTok.Muted,
            )
        }
        Slider(
            value = hourPart,
            onValueChange = { h -> onChange(dayPart * 86_400 + (h * 4).toInt() * 900L) },
            valueRange = -12f..12f,
            steps = 95, // 15-minute steps
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange(0L) }) { Text("Back to live") }
            TextButton(onClick = onJump) { Text("Jump past clock change") }
        }
    }
}
