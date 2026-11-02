package com.hormonelog.core.modelengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class EstimateSeriesTest {
    private val t = Instant.parse("2026-10-17T00:00:00Z")
    private fun p(hour: Long, v: Double) = EstimatePoint(t.plusSeconds(hour * 3600), v, v, v)
    private fun h(hour: Double): Instant = t.plusMillis((hour * 3_600_000).toLong())

    @Test fun withoutGapsTheNearestPointAnswersAndTheLineIsOnePiece() {
        val s = EstimateSeries(Hormone.P4, listOf(p(0, 1.0), p(2, 2.0), p(4, 3.0)))
        assertEquals(2.0, s.pointAt(h(2.9))!!.median, 0.0)
        assertEquals(1, s.segments().size)
    }

    @Test fun aGapCutsTheLineIntoPiecesAndHasNoValueInside() {
        val s = EstimateSeries(Hormone.P4, listOf(p(0, 1.0), p(2, 2.0), p(10, 5.0), p(12, 6.0)), listOf(EstimateGap(h(3.0), h(10.0))))
        assertEquals(listOf(listOf(p(0, 1.0), p(2, 2.0)), listOf(p(10, 5.0), p(12, 6.0))), s.segments())
        assertNull(s.pointAt(h(5.0)))
        assertEquals(5.0, s.pointAt(h(10.0))!!.median, 0.0)
    }

    @Test fun aValueIsNeverReadFromTheOtherSideOfAGap() {
        val s = EstimateSeries(Hormone.P4, listOf(p(0, 1.0), p(4, 5.0)), listOf(EstimateGap(h(3.0), h(3.5))))
        // The 4 h point is nearer to 2:59, but a gap lies between them.
        assertEquals(1.0, s.pointAt(h(2.98))!!.median, 0.0)
        assertNull(s.pointAt(h(3.2)))
        assertEquals(5.0, s.pointAt(h(3.5))!!.median, 0.0)
    }
}
