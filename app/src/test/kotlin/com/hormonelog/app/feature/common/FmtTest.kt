package com.hormonelog.app.feature.common

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FmtTest {
    /** Monday 5 October 2026, 16:30 in Seoul. */
    private val now = at(2026, 10, 5, 16, 30)
    private val twelve = Fmt(SEOUL, clock24 = false, now = now)
    private val twentyFour = Fmt(SEOUL, clock24 = true, now = now)

    @Test
    fun aDateShowsItsYearOnlyOutsideTheCurrentOne() {
        assertEquals("10월 4일", twelve.date(at(2026, 10, 4)))
        assertEquals("2025년 12월 3일", twelve.date(at(2025, 12, 3)))
        assertEquals("10월 4일 (일)", twelve.dateDay(at(2026, 10, 4)))
    }

    @Test
    fun timeFollowsTheClockTheUserChose() {
        assertEquals("오후 2:10", twelve.time(at(2026, 10, 5, 14, 10)))
        assertEquals("14:10", twentyFour.time(at(2026, 10, 5, 14, 10)))
        assertEquals("오전 12:05", twelve.time(at(2026, 10, 5, 0, 5)))
        assertEquals("00:05", twentyFour.time(at(2026, 10, 5, 0, 5)))
        assertEquals("오후 12:00", twelve.time(at(2026, 10, 5, 12, 0)))
        assertEquals("오전 9:00", twelve.minutes(9 * 60))
        assertEquals("21:00", twentyFour.minutes(21 * 60))
    }

    @Test
    fun aDateAndATimeTogether() {
        assertEquals("10월 10일 (토) 오전 9:00", twelve.dateTime(at(2026, 10, 10, 9)))
        assertEquals("10/4", twelve.short(at(2026, 10, 4)))
    }

    @Test
    fun agoSpeaksInTheUnitThatReadsNaturally() {
        assertEquals("방금", twelve.ago(now.minusSeconds(20)))
        assertEquals("45분 전", twelve.ago(now.minusSeconds(45 * 60)))
        assertEquals("3시간 전", twelve.ago(now.minusSeconds(3 * 3600)))
        assertEquals("12일 전", twelve.ago(now.minusSeconds(12 * 86_400L)))
        assertEquals("2시간 뒤", twelve.ago(now.plusSeconds(90 * 60)))
        assertEquals("3일 뒤", twelve.ago(now.plusSeconds(3 * 86_400L)))
    }

    @Test
    fun hoursSinceSwitchesToDaysAfterTwo() {
        assertEquals("29시간", twelve.hoursSince(29.0))
        assertEquals("2일", twelve.hoursSince(48.0))
        assertEquals("3.2일", twelve.hoursSince(77.0))
    }

    @Test
    fun elapsedNamesTheLargestUnitsFirstAndNeverEndsEmpty() {
        assertEquals("16시간 40분", twelve.elapsed(now.minusSeconds((16 * 60 + 40) * 60L)))
        assertEquals("2일 3시간", twelve.elapsed(now.minusSeconds((2 * 24 + 3) * 3600L)))
        assertEquals("0분", twelve.elapsed(now))
    }

    @Test
    fun daysBetweenCountsCalendarDaysNotTwentyFourHourBlocks() {
        assertEquals(1, twelve.daysBetween(at(2026, 10, 4, 23, 50), at(2026, 10, 5, 0, 10)))
        assertEquals(0, twelve.daysBetween(at(2026, 10, 5, 0, 10), at(2026, 10, 5, 23, 50)))
        assertEquals(-3, twelve.daysBetween(at(2026, 10, 8), at(2026, 10, 5)))
    }

    @Test
    fun aMomentJustAfterMidnightInSeoulIsStillTheNewDay() {
        // 00:30 on the 5th in Seoul is 15:30 UTC on the 4th; the date must be the local one.
        val i = Instant.parse("2026-10-04T15:30:00Z")
        assertEquals("10월 5일 (월)", twelve.dateDay(i))
        assertEquals("오전 12:30", twelve.time(i))
    }
}
