package com.hormonelog.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpaEvidenceTest {
    private val sources = MpaEvidence.bundle.sources
    private fun source(id: String) = sources.single { it.id == id }

    @Test fun pfizerSingleAndRepeatedArmsAreNeverPooled() {
        val twoTablets = source("pfizer-single-2x10mg")
        val eightTablets = source("pfizer-single-8x2.5mg")
        assertEquals(DoseDesign.SINGLE, twoTablets.design)
        assertEquals(20.0, twoTablets.doseMg!!, 0.0)
        assertEquals(DoseDesign.SINGLE, eightTablets.design)
        assertEquals(20.0, eightTablets.doseMg!!, 0.0)
        val repeated = source("pfizer-repeated-10mg-day7")
        assertEquals(DoseDesign.REPEATED, repeated.design)
        assertEquals(10.0, repeated.doseMg!!, 0.0)
        assertEquals(7, repeated.dayNumber)
        assertTrue(sources.none { it.design == DoseDesign.SINGLE && it.doseMg == 10.0 })
    }

    @Test fun pfizerSummaryValuesMatchThePrintedTable() {
        val twoTablets = source("pfizer-single-2x10mg").summary!!
        assertEquals(MeanSd(1.01, 0.599), twoTablets.cmax)
        assertEquals(MeanSd(2.65, 1.41), twoTablets.tmaxHours)
        assertEquals(MeanSd(6.95, 3.39), twoTablets.aucNgHPerMl)
        assertEquals(MeanSd(12.1, 3.49), twoTablets.tHalfHours)
        val eightTablets = source("pfizer-single-8x2.5mg").summary!!
        assertEquals(MeanSd(0.805, 0.413), eightTablets.cmax)
        assertEquals(MeanSd(2.22, 1.39), eightTablets.tmaxHours)
        assertEquals(MeanSd(5.62, 2.79), eightTablets.aucNgHPerMl)
        assertEquals(MeanSd(11.6, 2.81), eightTablets.tHalfHours)
        val repeated = source("pfizer-repeated-10mg-day7").summary!!
        assertEquals(MeanSd(0.71, 0.35), repeated.cmax)
        assertEquals(MeanSd(2.83, 1.83), repeated.tmaxHours)
        assertEquals(MeanSd(6.01, 3.16), repeated.aucNgHPerMl)
        assertEquals(MeanSd(16.6, 15.0), repeated.tHalfHours)
    }

    @Test fun everyNumericSourceNamesItsCohortFedStateAssayAndLocator() {
        for (s in sources.filter { it.summary != null }) {
            assertTrue(s.id, s.subjects != null)
            assertTrue(s.id, s.fedState != FedState.NOT_STATED)
            assertTrue(s.id, s.assay != MpaAssay.NOT_STATED)
            assertTrue(s.id, s.locator.isNotBlank())
            assertTrue(s.id, s.reviewedOn.isNotBlank())
        }
    }

    @Test fun noSourceReachesAStageThatAllowsACurveAndTheBundleStaysAtPkSummary() {
        assertEquals(EvidenceStage.PK_SUMMARY, MpaEvidence.bundle.stage)
        assertTrue(sources.all { it.stage == EvidenceStage.METADATA_ONLY || it.stage == EvidenceStage.PK_SUMMARY })
    }

    @Test fun victorAndJohanssonNumbersAreNotStoredBecauseTheyWereNeverChecked() {
        val victor = source("victor-johansson-1976-pmid975821")
        assertNull(victor.summary)
        assertNull(victor.doseMg)
        assertEquals(EvidenceStage.METADATA_ONLY, victor.stage)
    }

    @Test fun theFoodEffectIsARelativeChangeWithoutAFedArmMean() {
        val food = source("pfizer-food-10mg")
        assertNull(food.summary)
        assertTrue(food.note.contains("50 to 70%"))
        assertTrue(food.note.contains("18 to 33%"))
        assertEquals(EvidenceStage.PK_SUMMARY, food.stage)
    }
}
