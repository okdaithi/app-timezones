package com.dg.dualclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dg.dualclock.core.City
import com.dg.dualclock.core.CityModel
import com.dg.dualclock.core.WidgetModel

private fun cityColor(c: City) = if (c == City.PERTH) AppTok.Perth else AppTok.Galway

/**
 * In-app replica of the MEDIUM_TALL widget, drawn with plain Compose. It reads the same
 * core.buildModel output as the Glance widget. Unlike the widget, it may show a live
 * countdown ([detail]) because the app is on screen and recomposes every second.
 */
@Composable
fun WidgetPreview(m: WidgetModel, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AppTok.WidgetSurface, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = m.contentDescription },
    ) {
        Row(Modifier.fillMaxWidth()) {
            for (c in listOf(m.primary, m.secondary)) {
                CityColumn(c, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        m.band?.let { b ->
            TickRow(b.primaryTicks, cityColor(m.primary.city))
            BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
                val w = maxWidth
                Box(Modifier.fillMaxWidth().height(8.dp).background(AppTok.Track, RoundedCornerShape(4.dp)))
                Box(
                    Modifier
                        .offset(x = w * b.windowStart)
                        .width(w * (b.windowEnd - b.windowStart))
                        .height(8.dp)
                        .background(AppTok.Window, RoundedCornerShape(4.dp)),
                )
                Box(Modifier.offset(x = w * b.now - 1.dp).width(2.dp).height(12.dp).background(Color.White))
            }
            TickRow(b.secondaryTicks, cityColor(m.secondary.city))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (m.isOpen) AppTok.DotOpen else AppTok.DotClosed, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(m.statusTitle, color = AppTok.WidgetText, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = UiFont)
            Spacer(Modifier.weight(1f))
            Text(detail, color = AppTok.WidgetMuted, fontSize = 11.sp, fontFamily = UiFont)
        }
    }
}

@Composable
private fun CityColumn(c: CityModel, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(cityColor(c.city), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(c.label, color = AppTok.WidgetMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = UiFont)
        }
        Text(c.timeText, color = AppTok.WidgetText, fontSize = 34.sp, fontFamily = DigitsFont, lineHeight = 40.sp)
        Text(c.dateLine, color = AppTok.WidgetMuted, fontSize = 11.sp, fontFamily = UiFont, maxLines = 1)
    }
}

@Composable
private fun TickRow(labels: List<String>, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
        labels.forEach {
            Text(it, color = color, fontSize = 10.sp, fontFamily = DigitsFont, modifier = Modifier.weight(1f))
        }
    }
}
