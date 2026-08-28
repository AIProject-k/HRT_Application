package com.hormonelog.core.domain

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RecordContractsTest {
    private fun dose(status: DoseStatus, unit: DoseUnit = DoseUnit.MG, amount: Double = 4.0) = DoseEvent(
        id = UUID.randomUUID(),
        occurredAt = Instant.parse("2026-08-17T12:10:00Z"),
        sourceZoneId = "Asia/Seoul",
        drug = Drug.ESTRADIOL_VALERATE,
        route = Route.IM_INJECTION,
        amountEntered = amount,
        enteredUnit = unit,
        normalizedMilligrams = DoseEvent.normalizeMilligrams(amount, unit),
        status = status,
    )

    @Test
    fun labWithoutCollectionTimeIsCalibrationIneligible() {
        val lab = LabResult(
            id = UUID.randomUUID(),
            collectedAt = null,
            sourceZoneId = null,
            assay = Assay.UNKNOWN,
        )

        val decision = LabEligibility.evaluate(lab)

        assertFalse(decision.eligible)
        assertEquals(LabIneligibilityReason.MISSING_COLLECTION_TIME, decision.reason)
    }

    @Test
    fun historicalReconstructionExcludesSkippedDose() {
        assertEquals(
            emptyList<DoseEvent>(),
            HistoricalReconstruction.administrationsFrom(listOf(dose(DoseStatus.SKIPPED))),
        )
    }

    @Test
    fun patchUnitCannotNormaliseToMilligramsButKeepsEnteredAmount() {
        val patch = dose(status = DoseStatus.ADMINISTERED, unit = DoseUnit.PATCH, amount = 2.0)

        assertNull(patch.normalizedMilligrams)
        assertEquals(2.0, patch.amountEntered, 0.0)
        assertEquals(DoseUnit.PATCH, patch.enteredUnit)
    }

    @Test
    fun estradiolAnalytePreservesReportedValueAndConvertsPmol() {
        val value = LabAnalyteValue(
            analyte = Analyte.ESTRADIOL,
            reportedValue = 734.26,
            reportedUnit = "pmol/L",
            canonicalValue = LabAnalyteValue.canonical(Analyte.ESTRADIOL, 734.26, "pmol/L"),
        )

        assertEquals(734.26, value.reportedValue, 0.0)
        assertEquals("pmol/L", value.reportedUnit)
        assertEquals(200.0, value.canonicalValue!!, 0.5)
    }

    @Test
    fun unknownAnalyteUnitLeavesCanonicalNull() {
        assertNull(LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, 21.0, "??"))
    }
}
