package com.dg.dualclock.core

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.zone.ZoneOffsetTransition
import kotlin.math.abs

/*
 * In-app explanation panel (SPEC A2) and countdowns. Ported from the prototype script in
 * design/canvas/Main.dc.html. Countdowns belong here, not on the widget: they go stale every
 * minute, and the widget only recomposes at edges (SPEC §4.1).
 */

data class Explanation(
    val perthOffset: String,      // "UTC+08:00"
    val perthNote: String,        // "AWST, no daylight saving"
    val galwayOffset: String,     // "UTC+01:00"
    val galwayNote: String,       // "IST, Irish summer time"
    val gap: String,              // "Galway is 7 h behind Perth"
    val window: String,           // "15:00–21:00 Perth = 08:00–14:00 Galway"
    val nextChange: String,       // "Sun 25 Oct: Galway clocks go back 1 h"
    val nextChangeEffect: String, // "Gap becomes 8 h; window 16:00–21:00 Perth"
    val countdown: String,        // "Closes in 2h 56m" | "Opens in 8h 20m" | "Widen the hours"
)

private fun offsetLabel(offMin: Int): String {
    val a = abs(offMin)
    return "UTC" + (if (offMin >= 0) "+" else "−") + "%02d:%02d".format(a / 60, a % 60)
}

private fun offsetMinutes(city: City, at: Instant): Int = city.zone.rules.getOffset(at).totalSeconds / 60

private fun zoneNote(city: City, at: Instant): String = when (city) {
    City.PERTH -> "AWST, no daylight saving"
    City.GALWAY -> if (offsetMinutes(city, at) == 60) "IST, Irish summer time" else "GMT, Irish winter time"
}

/** "7 h", or "7.5 h" for a half-hour gap. */
private fun hoursText(minutes: Int): String {
    val m = abs(minutes)
    return if (m % 60 == 0) "${m / 60} h" else "%.1f h".format(m / 60.0)
}

/** "2h 56m", or "45m" under an hour. */
private fun durationText(d: Duration): String {
    val total = d.toMinutes()
    val h = total / 60
    val m = total % 60
    return if (h > 0) "${h}h %02dm".format(m) else "${m}m"
}

private fun windowText(range: IntRange?, gap: Int, primary: City): String {
    if (range == null) return "None at these hours"
    val lo = range.first
    val hi = range.last + 1
    return "${hm(lo)}–${hm(hi)} ${primary.displayName} = ${hm(lo - gap)}–${hm(hi - gap)} ${primary.other.displayName}"
}

fun explain(now: Instant, hours: CallHours = CallHours(), primary: City = City.PERTH): Explanation {
    val secondary = primary.other
    val gap = gapMinutes(now, primary)
    val gapText = when {
        gap == 0 -> "Same clock time"
        gap > 0 -> "${secondary.displayName} is ${hoursText(gap)} behind ${primary.displayName}"
        else -> "${secondary.displayName} is ${hoursText(gap)} ahead of ${primary.displayName}"
    }

    // Earliest upcoming offset change in either city, taken straight from ZoneRules.
    val change: Pair<City, ZoneOffsetTransition>? = City.entries
        .mapNotNull { c -> c.zone.rules.nextTransition(now)?.let { c to it } }
        .minByOrNull { it.second.instant }
    val (changeText, changeEffect) = change?.let { (city, tr) ->
        val deltaMin = (tr.offsetAfter.totalSeconds - tr.offsetBefore.totalSeconds) / 60
        val direction = if (deltaMin < 0) "back" else "forward"
        val after = tr.instant.plusSeconds(60)
        val newGap = gapMinutes(after, primary)
        val newWindow = callWindow(after, hours, primary)
        val windowPart = newWindow?.let { "${hm(it.first)}–${hm(it.last + 1)} ${primary.displayName}" } ?: "closes"
        Pair(
            "${dayLabel(tr.instant.atZone(city.zone))}: ${city.displayName} clocks go $direction ${hoursText(deltaMin)}",
            "Gap becomes ${hoursText(newGap)}; window $windowPart",
        )
    } ?: Pair("None scheduled", "")

    // Whole minutes, matching the clocks on screen (18:04:30 → 21:00 reads "2h 56m").
    val minute = now.truncatedTo(ChronoUnit.MINUTES)
    val countdown = when (val s = callStatus(now, hours, primary)) {
        is CallStatus.Open -> "Closes in " + durationText(Duration.between(minute, s.closesAt))
        is CallStatus.Closed -> "Opens in " + durationText(Duration.between(minute, s.opensAt))
        CallStatus.NoOverlap -> "Widen the hours"
    }

    return Explanation(
        perthOffset = offsetLabel(offsetMinutes(City.PERTH, now)),
        perthNote = zoneNote(City.PERTH, now),
        galwayOffset = offsetLabel(offsetMinutes(City.GALWAY, now)),
        galwayNote = zoneNote(City.GALWAY, now),
        gap = gapText,
        window = windowText(callWindow(now, hours, primary), gap, primary),
        nextChange = changeText,
        nextChangeEffect = changeEffect,
        countdown = countdown,
    )
}

