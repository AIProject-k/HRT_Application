package com.hormonelog.app.feature.flow

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.feature.common.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChartAxisLabelTest {
    private val now = at(2026, 11, 2, 21)
    private val fmt = Fmt(SEOUL, true, now)

    @Test fun aDayLongWindowLabelsTheTimeOfDayAndLongerWindowsTheDate() {
        val thirtyHours = 30 * 3_600_000.0
        assertEquals(fmt.time(now), axisLabel(fmt, now, thirtyHours))
        assertNotEquals(axisLabel(fmt, now, thirtyHours), axisLabel(fmt, now.minusSeconds(6 * 3600), thirtyHours))
        assertEquals("11/2", axisLabel(fmt, now, 7 * 86_400_000.0))
    }
}
