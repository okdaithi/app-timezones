package com.dg.dualclock.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** In-app explanation panel and countdowns. Same pinned instants as CallWindowTest. */
class ExplainTest {

    private fun t(s: String) = Instant.parse(s)

    @Test fun irishSummerInsideWindow() {
        val e = explain(t("2026-09-25T10:04:00Z"))
        assertEquals("UTC+08:00", e.perthOffset)
        assertEquals("AWST, no daylight saving", e.perthNote)
        assertEquals("UTC+01:00", e.galwayOffset)
        assertEquals("IST, Irish summer time", e.galwayNote)
        assertEquals("Galway is 7 h behind Perth", e.gap)
        assertEquals("15:00–21:00 Perth = 08:00–14:00 Galway", e.window)
        assertEquals("Sun 25 Oct: Galway clocks go back 1 h", e.nextChange)
        assertEquals("Gap becomes 8 h; window 16:00–21:00 Perth", e.nextChangeEffect)
        assertEquals("Closes in 2h 56m", e.countdown)
    }

    @Test fun countdownIgnoresSeconds() {
        assertEquals("Closes in 2h 56m", explain(t("2026-09-25T10:04:59Z")).countdown)
    }

    @Test fun outsideWindowCountsDownToOpening() {
        assertEquals("Opens in 8h 20m", explain(t("2026-09-25T22:40:00Z")).countdown)
    }

    @Test fun irishWinter() {
        val e = explain(t("2026-11-02T10:00:00Z"))
        assertEquals("GMT, Irish winter time", e.galwayNote)
        assertEquals("UTC+00:00", e.galwayOffset)
        assertEquals("Galway is 8 h behind Perth", e.gap)
        assertEquals("16:00–21:00 Perth = 08:00–13:00 Galway", e.window)
        assertEquals("Sun 28 Mar: Galway clocks go forward 1 h", e.nextChange)
        assertEquals("Gap becomes 7 h; window 15:00–21:00 Perth", e.nextChangeEffect)
    }

    @Test fun galwayPrimary() {
        val e = explain(t("2026-09-25T10:04:00Z"), primary = City.GALWAY)
        assertEquals("Perth is 7 h ahead of Galway", e.gap)
        assertEquals("08:00–14:00 Galway = 15:00–21:00 Perth", e.window)
        assertEquals("Gap becomes 8 h; window 08:00–13:00 Galway", e.nextChangeEffect)
    }

    @Test fun noOverlap() {
        val e = explain(t("2026-09-25T10:04:00Z"), CallHours(9, 15))
        assertEquals("None at these hours", e.window)
        assertEquals("Widen the hours", e.countdown)
    }
}
