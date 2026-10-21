package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class CpaModelTest {
    private val at = Instant.parse("2026-10-01T00:00:00Z")
    private val model = CpaModel(0.88, 6.0, 0.365)
    private fun dose(status: DoseStatus) = DoseEvent(
        UUID.randomUUID(), at, "UTC", Drug.CYPROTERONE, Route.ORAL,
        25.0, DoseUnit.MG, 25.0, status,
    )

    @Test fun onlyTakenStatusesContribute() {
        val later = at.plusSeconds(86400).toEpochMilli()
        for (status in DoseStatus.entries) {
            val level = model.levelAt(listOf(dose(status)), later)
            if (status.wasTaken) assertTrue("$status", level > 0.0)
            else assertEquals("$status", 0.0, level, 0.0)
        }
    }

    @Test fun unsupportedRouteUnitAndInvalidAmountsDoNotBecomeOralExposure() {
        val d = dose(DoseStatus.ADMINISTERED)
        val invalid = listOf(
            d.copy(route = Route.IM_INJECTION),
            d.copy(enteredUnit = DoseUnit.PATCH, normalizedMilligrams = null),
            d.copy(amountEntered = Double.NaN, normalizedMilligrams = Double.NaN),
            d.copy(amountEntered = Double.POSITIVE_INFINITY, normalizedMilligrams = Double.POSITIVE_INFINITY),
            d.copy(amountEntered = -25.0, normalizedMilligrams = -25.0),
        )
        assertEquals(0.0, model.levelAt(invalid, at.plusSeconds(86400).toEpochMilli()), 0.0)
        assertTrue(model.levelAt(listOf(d.copy(normalizedMilligrams = null)), at.plusSeconds(86400).toEpochMilli()) > 0.0)
    }

    @Test fun futureAndOtherDrugsContributeNothingAndTakenDosesAdd() {
        val d = dose(DoseStatus.ADMINISTERED)
        assertEquals(0.0, model.levelAt(listOf(d), at.minusSeconds(1).toEpochMilli()), 0.0)
        val later = at.plusSeconds(86400).toEpochMilli()
        assertEquals(0.0, model.levelAt(listOf(d.copy(drug = Drug.PROGESTERONE_MICRONIZED)), later), 0.0)
        assertEquals(2 * model.levelAt(listOf(d), later), model.levelAt(listOf(d, d), later), 1e-9)
    }
}
