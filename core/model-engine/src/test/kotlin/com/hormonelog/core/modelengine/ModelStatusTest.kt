package com.hormonelog.core.modelengine

import com.hormonelog.core.evidence.EvidenceStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelStatusTest {
    @Test fun numbersNeedDosesAValidatedStageAndAnEnabledModel() {
        assertNull(blockingReason(EvidenceStage.MODEL_VALIDATED, enabled = true, hasDoses = true))
        assertEquals(ModelReason.NO_DATA, blockingReason(EvidenceStage.MODEL_VALIDATED, enabled = true, hasDoses = false))
        assertEquals(ModelReason.MODEL_DISABLED, blockingReason(EvidenceStage.MODEL_VALIDATED, enabled = false, hasDoses = true))
    }

    @Test fun noUnvalidatedStageShowsANumberEvenWhenTheModelIsEnabled() {
        for (stage in listOf(EvidenceStage.METADATA_ONLY, EvidenceStage.PK_SUMMARY, EvidenceStage.TIME_SERIES_VERIFIED)) {
            assertEquals(ModelReason.EVIDENCE_INSUFFICIENT, blockingReason(stage, enabled = true, hasDoses = true))
        }
    }

    @Test fun theEvidenceReasonIsReportedBeforeTheBuildSwitch() {
        assertEquals(ModelReason.EVIDENCE_INSUFFICIENT, blockingReason(EvidenceStage.METADATA_ONLY, enabled = false, hasDoses = true))
    }
}
