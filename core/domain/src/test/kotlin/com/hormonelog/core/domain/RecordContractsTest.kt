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

    @Test
    fun totalTestosteroneConvertsFromEveryUnitALabMayUse() {
        assertEquals(41.0, LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, 41.0, "ng/dL")!!, 0.0)
        assertEquals(30.0, LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, 0.3, "ng/mL")!!, 1e-9)
        assertEquals(28.842, LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, 1.0, "nmol/L")!!, 1e-9)
    }

    @Test
    fun patchStrengthIsReadAsMicrogramsPerDay() {
        assertEquals(50.0, dose(DoseStatus.ADMINISTERED, DoseUnit.UG_PER_DAY, 50.0).patchMicrogramsPerDay!!, 0.0)
        // Older builds labelled the strength "mg/일": 50 there can only mean 50 µg.
        assertEquals(50.0, dose(DoseStatus.ADMINISTERED, DoseUnit.MG_PER_DAY, 50.0).patchMicrogramsPerDay!!, 0.0)
        assertEquals(50.0, dose(DoseStatus.ADMINISTERED, DoseUnit.MG_PER_DAY, 0.05).patchMicrogramsPerDay!!, 1e-9)
        // A bare count of patches says nothing about strength.
        assertNull(dose(DoseStatus.ADMINISTERED, DoseUnit.PATCH, 1.0).patchMicrogramsPerDay)
    }

    @Test
    fun everyDrugOffersOnlyRoutesItCanActuallyBeGivenBy() {
        assertEquals(listOf(Route.PATCH), Drug.ESTRADIOL_PATCH.allowedRoutes)
        assertEquals(listOf(Route.GEL), Drug.ESTRADIOL_GEL.allowedRoutes)
        assertEquals(listOf(Route.IM_INJECTION, Route.SC_INJECTION), Drug.ESTRADIOL_CYPIONATE.allowedRoutes)
        assertEquals(listOf(DoseUnit.UG_PER_DAY), Route.PATCH.allowedUnits)
    }
}
