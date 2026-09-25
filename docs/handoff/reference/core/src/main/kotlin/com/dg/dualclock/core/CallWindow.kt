package com.dg.dualclock.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Pure time-zone logic for the Perth–Galway dual-clock widget.
 *
 * No Android imports: this file compiles and unit-tests on the plain JVM.
 * Every function takes `now` as a parameter, so tests pin the instant.
 * Nothing here reads ZoneId.systemDefault(). The device zone is irrelevant to this widget.
 */

object Zones {
    val PERTH: ZoneId = ZoneId.of("Australia/Perth")
    val GALWAY: ZoneId = ZoneId.of("Europe/Dublin")
}

enum class City(val zone: ZoneId, val label: String, val displayName: String) {
    PERTH(Zones.PERTH, "PERTH", "Perth"),
    GALWAY(Zones.GALWAY, "GALWAY", "Galway");

    val other: City get() = if (this == PERTH) GALWAY else PERTH
}

/** Local hours during which a call is acceptable in BOTH cities. closeHour is exclusive. */
data class CallHours(val openHour: Int = 8, val closeHour: Int = 21) {
    init {
        require(openHour in 0..23) { "openHour must be 0..23" }
        require(closeHour in 1..24) { "closeHour must be 1..24" }
        require(openHour < closeHour) { "openHour must be before closeHour" }
    }
}

/** Offset difference in minutes: axis clock minus the other city's clock at [at]. Perth axis: 420 (IST) or 480 (GMT). */
fun gapMinutes(at: Instant, axis: City): Int =
    (axis.zone.rules.getOffset(at).totalSeconds - axis.other.zone.rules.getOffset(at).totalSeconds) / 60

/**
 * Callable minutes-of-day on [axis]'s clock at the offsets in force at [at], or null if the cities never overlap.
 * Used for drawing the band. For scheduling, use [windowOn], which handles clock-change days.
 */
fun callWindow(at: Instant, hours: CallHours = CallHours(), axis: City = City.PERTH): IntRange? {
    val gap = gapMinutes(at, axis)
    val open = hours.openHour * 60
    val close = hours.closeHour * 60
    val start = maxOf(open, open + gap)
    val end = minOf(close, close + gap)
    return if (start < end) start until end else null
}

/** A concrete call window: [start] inclusive, [end] exclusive. */
data class Window(val start: Instant, val end: Instant) {
    operator fun contains(t: Instant): Boolean = !t.isBefore(start) && t.isBefore(end)
}

/**
 * The window on a given Perth calendar date, evaluated at the offsets in force at the window itself
 * (not at "now"). This is what makes 25 Oct 2026 open at 16:00 Perth rather than 15:00.
 */
fun windowOn(perthDate: LocalDate, hours: CallHours = CallHours()): Window? {
    val dayStart = perthDate.atStartOfDay(Zones.PERTH)
    var probe = dayStart.plusHours(12).toInstant()
    var range: IntRange? = null
    repeat(2) { // settle: re-evaluate at the window's own start
        range = callWindow(probe, hours, City.PERTH) ?: return null
        probe = dayStart.plusMinutes(range!!.first.toLong()).toInstant()
    }
    val r = range!!
    return Window(
        start = dayStart.plusMinutes(r.first.toLong()).toInstant(),
        end = dayStart.plusMinutes((r.last + 1).toLong()).toInstant(),
    )
}

private fun perthDate(now: Instant): LocalDate = now.atZone(Zones.PERTH).toLocalDate()

/** The next instant after [now] at which a window opens or closes. */
fun nextWindowEdge(now: Instant, hours: CallHours = CallHours()): Instant? {
    val today = perthDate(now)
    return (0L..2L).asSequence()
        .mapNotNull { windowOn(today.plusDays(it), hours) }
        .flatMap { sequenceOf(it.start, it.end) }
        .filter { it.isAfter(now) }
        .minOrNull()
}

/** Next UTC-offset change in either zone. Perth has none; Galway changes last Sunday of March and October at 01:00 UTC. */
fun nextClockChange(now: Instant): Instant? =
    listOfNotNull(
        Zones.PERTH.rules.nextTransition(now)?.instant,
        Zones.GALWAY.rules.nextTransition(now)?.instant,
    ).minOrNull()

private fun nextMidnight(now: Instant, zone: ZoneId): Instant =
    now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

/** How often the "now" marker on the band is nudged. RTC (non-wakeup) alarm, so it costs nothing while the screen is off. */
val MARKER_REFRESH: Duration = Duration.ofMinutes(15)

/**
 * When the widget must next be recomposed: the earliest of a window edge, a clock change,
 * midnight in either city (date labels), or the marker cadence.
 */
fun nextRefresh(now: Instant, hours: CallHours = CallHours()): Instant =
    listOfNotNull(
        nextWindowEdge(now, hours),
        nextClockChange(now),
        nextMidnight(now, Zones.PERTH),
        nextMidnight(now, Zones.GALWAY),
        now.plus(MARKER_REFRESH),
    ).min()

enum class Reason { TOO_EARLY, TOO_LATE }

sealed interface CallStatus {
    data class Open(val closesAt: Instant) : CallStatus
    data class Closed(val opensAt: Instant, val reason: Reason, val city: City) : CallStatus
    data object NoOverlap : CallStatus
}

fun callStatus(now: Instant, hours: CallHours = CallHours(), primary: City = City.PERTH): CallStatus {
    val today = perthDate(now)
    val windows = (0L..2L).mapNotNull { windowOn(today.plusDays(it), hours) }
    if (windows.isEmpty()) return CallStatus.NoOverlap
    windows.firstOrNull { now in it }?.let { return CallStatus.Open(it.end) }
    val opensAt = windows.map { it.start }.filter { it.isAfter(now) }.min()
    // Explain with the secondary city first (the one the user is usually calling), then the primary.
    for (city in listOf(primary.other, primary)) {
        val t = now.atZone(city.zone)
        val m = t.hour * 60 + t.minute
        if (m < hours.openHour * 60) return CallStatus.Closed(opensAt, Reason.TOO_EARLY, city)
        if (m >= hours.closeHour * 60) return CallStatus.Closed(opensAt, Reason.TOO_LATE, city)
    }
    // Both cities inside their hours but not both at once cannot happen; fall back defensively.
    return CallStatus.Closed(opensAt, Reason.TOO_EARLY, primary.other)
}

// ---------------------------------------------------------------------------------------------
// Presentation model: every string and fraction the widget layouts need, so layouts stay dumb.
// ---------------------------------------------------------------------------------------------

data class CityModel(
    val city: City,
    val label: String,       // "GALWAY"
    val smallLabel: String,  // SMALL size only: "GALWAY · FRI" when its date differs from the primary's, else same as label
    val dateLine: String,    // "Fri 25 Sep · IST · a day behind"
    val timeText: String,    // "11:04". Fallback/accessibility only; on-device digits come from TextClock
)

data class BandModel(
    val windowStart: Float,  // 0..1 along the primary city's 24 h axis
    val windowEnd: Float,
    val now: Float,
    val primaryTicks: List<String>,   // "00","03",…,"21"
    val secondaryTicks: List<String>, // e.g. "17","20","23","02",…
)

data class WidgetModel(
    val primary: CityModel,
    val secondary: CityModel,
    val band: BandModel?,       // null when there is no overlap
    val isOpen: Boolean,
    val statusTitle: String,    // "Good time to call" | "Too late in Galway" | "No shared hours"
    val statusDetail: String,   // "Until 21:00" | "Opens 15:00" | "Adjust calling hours"
    val statusShort: String,    // small size: "Call now" | "Opens 15:00" | "No overlap"
    val contentDescription: String,
)

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

// Fixed English abbreviations. Locale-based "MMM" differs across JDK/Android CLDR versions
// (Locale.UK yields "Sept" on newer data), which would break the design and the tests.
private val WEEKDAYS = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private fun weekday(z: ZonedDateTime) = WEEKDAYS[z.dayOfWeek.value - 1]
private fun dayLabel(z: ZonedDateTime) = "${weekday(z)} ${z.dayOfMonth} ${MONTHS[z.monthValue - 1]}"

/** Deterministic abbreviations. Do not use the "zzz" pattern: its output depends on the device's locale data. */
fun zoneAbbreviation(city: City, at: Instant): String {
    val offMin = city.zone.rules.getOffset(at).totalSeconds / 60
    return when {
        city == City.PERTH && offMin == 480 -> "AWST"
        city == City.GALWAY && offMin == 60 -> "IST"
        city == City.GALWAY && offMin == 0 -> "GMT"
        else -> {
            val a = kotlin.math.abs(offMin)
            "UTC" + (if (offMin >= 0) "+" else "−") + "%02d:%02d".format(a / 60, a % 60)
        }
    }
}

private fun hm(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

fun buildModel(now: Instant, hours: CallHours = CallHours(), primary: City = City.PERTH): WidgetModel {
    val secondary = primary.other
    val pz: ZonedDateTime = now.atZone(primary.zone)
    val sz: ZonedDateTime = now.atZone(secondary.zone)
    val dayCmp = sz.toLocalDate().compareTo(pz.toLocalDate())
    val dayNote = when {
        dayCmp < 0 -> " · a day behind"
        dayCmp > 0 -> " · a day ahead"
        else -> ""
    }

    val primaryModel = CityModel(
        city = primary,
        label = primary.label,
        smallLabel = primary.label,
        dateLine = dayLabel(pz) + " · " + zoneAbbreviation(primary, now),
        timeText = HM.format(pz),
    )
    val secondaryModel = CityModel(
        city = secondary,
        label = secondary.label,
        smallLabel = if (dayCmp != 0) secondary.label + " · " + weekday(sz).uppercase(Locale.ROOT) else secondary.label,
        dateLine = dayLabel(sz) + " · " + zoneAbbreviation(secondary, now) + dayNote,
        timeText = HM.format(sz),
    )

    val axisWindow = callWindow(now, hours, primary)
    val gap = gapMinutes(now, primary)
    val band = axisWindow?.let { w ->
        BandModel(
            windowStart = w.first / 1440f,
            windowEnd = (w.last + 1) / 1440f,
            now = (pz.hour * 60 + pz.minute) / 1440f,
            primaryTicks = (0 until 8).map { "%02d".format(it * 3) },
            secondaryTicks = (0 until 8).map { i ->
                val s = hm(i * 180 - gap)
                if (s.endsWith(":00")) s.substring(0, 2) else s
            },
        )
    }

    val status = callStatus(now, hours, primary)
    val inPrimary = { t: Instant -> HM.format(t.atZone(primary.zone)) }
    val (title, detail, short) = when (status) {
        is CallStatus.Open -> Triple("Good time to call", "Until " + inPrimary(status.closesAt), "Call now")
        is CallStatus.Closed -> Triple(
            (if (status.reason == Reason.TOO_EARLY) "Too early in " else "Too late in ") + status.city.displayName,
            "Opens " + inPrimary(status.opensAt),
            "Opens " + inPrimary(status.opensAt),
        )
        CallStatus.NoOverlap -> Triple("No shared hours", "Adjust calling hours", "No overlap")
    }

    return WidgetModel(
        primary = primaryModel,
        secondary = secondaryModel,
        band = band,
        isOpen = status is CallStatus.Open,
        statusTitle = title,
        statusDetail = detail,
        statusShort = short,
        contentDescription = "${primary.displayName} ${primaryModel.timeText}, " +
            "${secondary.displayName} ${secondaryModel.timeText}. $title. $detail.",
    )
}
