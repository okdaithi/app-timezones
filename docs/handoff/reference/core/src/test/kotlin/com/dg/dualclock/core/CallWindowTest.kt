package com.dg.dualclock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pinned instants. Reference moments used in the design boards:
 *   2026-09-25T10:04Z = Fri 18:04 Perth / Fri 11:04 Galway (IST), inside the window
 *   2026-09-25T22:40Z = Sat 06:40 Perth / Fri 23:40 Galway, outside, Galway a day behind
 */
class CallWindowTest {

    private fun t(s: String) = Instant.parse(s)

    @Test fun windowIrishSummer() {
        assertEquals(900 until 1260, callWindow(t("2026-09-25T10:04:00Z")))
    }

    @Test fun windowIrishWinter() {
        assertEquals(960 until 1260, callWindow(t("2026-11-02T10:00:00Z")))
    }

    @Test fun windowOnGalwayAxis() {
        assertEquals(480 until 840, callWindow(t("2026-09-25T10:04:00Z"), axis = City.GALWAY))
    }

    @Test fun noOverlapWhenHoursTooNarrow() {
        assertNull(callWindow(t("2026-09-25T10:04:00Z"), CallHours(9, 15)))
        assertEquals(CallStatus.NoOverlap, callStatus(t("2026-09-25T10:04:00Z"), CallHours(9, 15)))
    }

    @Test fun clockChanges() {
        assertEquals(t("2026-10-25T01:00:00Z"), nextClockChange(t("2026-09-25T00:00:00Z")))
        assertEquals(t("2027-03-28T01:00:00Z"), nextClockChange(t("2026-10-25T01:00:00Z")))
        assertNull(Zones.PERTH.rules.nextTransition(t("2026-09-25T00:00:00Z")))
    }

    @Test fun nextEdgeInsideWindowIsClose() {
        assertEquals(t("2026-09-25T13:00:00Z"), nextWindowEdge(t("2026-09-25T10:04:00Z")))
    }

    @Test fun nextEdgeOvernightIsNextOpen() {
        assertEquals(t("2026-09-26T07:00:00Z"), nextWindowEdge(t("2026-09-25T22:40:00Z")))
    }

    @Test fun openStatus() {
        val m = buildModel(t("2026-09-25T10:04:00Z"))
        assertTrue(m.isOpen)
        assertEquals("Good time to call", m.statusTitle)
        assertEquals("Until 21:00", m.statusDetail)
        assertEquals("Call now", m.statusShort)
        assertEquals("18:04", m.primary.timeText)
        assertEquals("11:04", m.secondary.timeText)
        assertEquals("Fri 25 Sep · AWST", m.primary.dateLine)
        assertEquals("Fri 25 Sep · IST", m.secondary.dateLine)
        assertEquals("GALWAY", m.secondary.label)
    }

    @Test fun closedStatusGalwayDayBehind() {
        val now = t("2026-09-25T22:40:00Z")
        assertEquals(CallStatus.Closed(t("2026-09-26T07:00:00Z"), Reason.TOO_LATE, City.GALWAY), callStatus(now))
        val m = buildModel(now)
        assertEquals("Too late in Galway", m.statusTitle)
        assertEquals("Opens 15:00", m.statusDetail)
        assertEquals("GALWAY", m.secondary.label)
        assertEquals("GALWAY · FRI", m.secondary.smallLabel)
        assertEquals("Fri 25 Sep · IST · a day behind", m.secondary.dateLine)
    }

    @Test fun clockChangeDayOpensAtNewTime() {
        // Sun 25 Oct 06:00 Perth; Galway still on IST (Sat 23:00). Ireland goes to GMT at 01:00Z,
        // so today's window opens at 16:00 Perth (08:00Z), not 15:00.
        val now = t("2026-10-24T22:00:00Z")
        assertEquals(CallStatus.Closed(t("2026-10-25T08:00:00Z"), Reason.TOO_LATE, City.GALWAY), callStatus(now))
        assertEquals("Opens 16:00", buildModel(now).statusDetail)
    }

    @Test fun galwayPrimarySwapsAxis() {
        val m = buildModel(t("2026-09-25T10:04:00Z"), primary = City.GALWAY)
        assertEquals("Until 14:00", m.statusDetail)
        assertEquals(480 / 1440f, m.band!!.windowStart)
        assertEquals(840 / 1440f, m.band!!.windowEnd)
    }

    @Test fun bandTicks() {
        val b = buildModel(t("2026-09-25T10:04:00Z")).band!!
        assertEquals(listOf("00", "03", "06", "09", "12", "15", "18", "21"), b.primaryTicks)
        assertEquals(listOf("17", "20", "23", "02", "05", "08", "11", "14"), b.secondaryTicks)
    }

    @Test fun refreshAtGalwayMidnight() {
        // 23:50 Galway: date label must flip at 00:00 Galway (23:00Z), before the 15-minute marker tick.
        assertEquals(t("2026-09-25T23:00:00Z"), nextRefresh(t("2026-09-25T22:50:00Z")))
    }

    @Test fun refreshDefaultsToMarkerCadence() {
        assertEquals(t("2026-09-25T10:19:00Z"), nextRefresh(t("2026-09-25T10:04:00Z")))
    }
}
