package com.hormonelog.app.feature.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DateTimePickerTest {
    @Test
    fun calendarStartsOnTheLocalDayAfterMidnightInSeoul() {
        val seed = Instant.parse("2026-10-02T15:15:00Z").toEpochMilli()
        val expected = Instant.parse("2026-10-03T00:00:00Z").toEpochMilli()
        assertEquals(expected, datePickerSeedMillis(seed, ZoneId.of("Asia/Seoul")))
    }

    @Test
    fun calendarStartsOnTheLocalDayBeforeMidnightInLosAngeles() {
        val seed = Instant.parse("2026-10-03T06:15:00Z").toEpochMilli()
        val expected = Instant.parse("2026-10-02T00:00:00Z").toEpochMilli()
        assertEquals(expected, datePickerSeedMillis(seed, ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun selectedCalendarDayAndTimeRoundTripInBothZones() {
        val day = Instant.parse("2026-08-01T00:00:00Z").toEpochMilli()
        for (zone in listOf(ZoneId.of("Asia/Seoul"), ZoneId.of("America/Los_Angeles"))) {
            val expected = java.time.LocalDateTime.of(2026, 8, 1, 23, 45)
                .atZone(zone).toInstant().toEpochMilli()
            assertEquals(expected, datePickerResultMillis(day, 23, 45, zone))
        }
    }
}
