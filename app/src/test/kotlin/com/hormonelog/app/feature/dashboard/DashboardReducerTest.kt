package com.hormonelog.app.feature.dashboard

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DashboardReducerTest {
    private val now = Instant.parse("2026-08-27T12:10:00Z")
    private val base = DashboardState()

    @Test
    fun switchTabChangesTabAndClearsScrub() {
        val scrubbing = base.copy(scrubFraction = 0.5f)
        val next = DashboardReducer.switchTab(scrubbing, DashboardTab.FLOW)
        assertEquals(DashboardTab.FLOW, next.tab)
        assertNull(next.scrubFraction)
    }

    @Test
    fun openAndCloseSheet() {
        val opened = DashboardReducer.openSheet(base, DashboardSheet.DOSE)
        assertEquals(DashboardSheet.DOSE, opened.sheet)
        val closed = DashboardReducer.closeSheet(opened.copy(labDraft = opened.labDraft.copy(focus = LabField.E2)))
        assertEquals(DashboardSheet.NONE, closed.sheet)
        assertEquals(LabField.NONE, closed.labDraft.focus)
    }

    @Test
    fun saveDoseAppendsEventPreservesUnitAndFlashesToast() {
        val next = DashboardReducer.saveDose(base, now)
        assertEquals(1, next.doses.size)
        val event = next.doses.single()
        assertEquals(DoseUnit.MG, event.enteredUnit)
        assertEquals(5.0, event.amountEntered, 0.0)
        assertEquals(5.0, event.normalizedMilligrams!!, 0.0)
        assertEquals(DoseStatus.ADMINISTERED, event.status)
        assertEquals(DashboardSheet.NONE, next.sheet)
        assertNotNull(next.toast)
        assertEquals(setOf(event.id.toString()), next.newIds)
    }

    @Test
    fun saveDoseWithCustomDateUsesTheChosenInstant() {
        val chosen = Instant.parse("2026-08-10T21:30:00Z")
        val custom = DashboardReducer.editDose(base) {
            it.copy(time = DoseTimeChoice.CUSTOM, customEpochMillis = chosen.toEpochMilli())
        }
        val next = DashboardReducer.saveDose(custom, now)
        assertEquals(chosen, next.doses.single().occurredAt)
    }

    @Test
    fun saveDoseWithPatchUnitLeavesNormalizedMilligramsNull() {
        val patched = DashboardReducer.editDose(base) { it.copy(unit = DoseUnit.PATCH, amount = 1.0) }
        val next = DashboardReducer.saveDose(patched, now)
        assertNull(next.doses.single().normalizedMilligrams)
        assertEquals(DoseUnit.PATCH, next.doses.single().enteredUnit)
    }

    @Test
    fun stepDoseTiersAndClamps() {
        // <10 -> 0.5 steps
        assertEquals(5.5, DashboardReducer.stepDose(base, up = true).doseDraft.amount, 0.0)
        // 10..50 -> 1.0 steps, so 25 mg is reachable by stepping up from 10
        val atTen = DashboardReducer.editDose(base) { it.copy(amount = 10.0) }
        assertEquals(11.0, DashboardReducer.stepDose(atTen, up = true).doseDraft.amount, 0.0)
        // >50 -> 5.0 steps
        val at60 = DashboardReducer.editDose(base) { it.copy(amount = 60.0) }
        assertEquals(65.0, DashboardReducer.stepDose(at60, up = true).doseDraft.amount, 0.0)
        // floor clamp
        val atFloor = DashboardReducer.editDose(base) { it.copy(amount = 0.5) }
        assertEquals(0.5, DashboardReducer.stepDose(atFloor, up = false).doseDraft.amount, 0.0)
    }

    @Test
    fun setDoseAmountParsesClampsAndIgnoresGarbage() {
        assertEquals(25.0, DashboardReducer.setDoseAmount(base, "25").doseDraft.amount, 0.0)
        assertEquals(3.5, DashboardReducer.setDoseAmount(base, "3.5").doseDraft.amount, 0.0)
        assertSame(base, DashboardReducer.setDoseAmount(base, "")) // unparseable -> unchanged instance
        assertSame(base, DashboardReducer.setDoseAmount(base, "abc"))
        assertEquals(500.0, DashboardReducer.setDoseAmount(base, "9999").doseDraft.amount, 0.0)
    }

    @Test
    fun saveLabWithE2OnlyStoresOneAnalyteAndResetsDraft() {
        val filled = DashboardReducer.editLab(base) { it.copy(e2 = "174") }
        val next = DashboardReducer.saveLab(filled, now)
        assertEquals(1, next.labs.size)
        val lab = next.labs.single()
        assertEquals(1, lab.analytes.size)
        assertEquals(Analyte.ESTRADIOL, lab.analytes.single().analyte)
        assertEquals(174.0, lab.analytes.single().canonicalValue!!, 0.0)
        assertEquals("", next.labDraft.e2)
        assertNotNull(next.toast)
    }

    @Test
    fun saveLabWithNoValuesIsANoOp() {
        assertSame(base, DashboardReducer.saveLab(base, now))
    }

    @Test
    fun pressKeyAppendsToFocusedFieldWithSixCharCapAndBackspace() {
        var s = DashboardReducer.focusLabField(base, LabField.E2)
        listOf("1", "2", "3", "4", "5", "6", "7").forEach { s = DashboardReducer.pressKey(s, it) }
        assertEquals("123456", s.labDraft.e2)
        s = DashboardReducer.pressKey(s, "⌫")
        assertEquals("12345", s.labDraft.e2)
    }

    @Test
    fun pressKeyIgnoredWhenNoFieldFocused() {
        assertSame(base, DashboardReducer.pressKey(base, "5"))
    }

    @Test
    fun loadSampleRegimenSeedsTwoRegimensAndBackfillsEvents() {
        val next = DashboardReducer.loadSampleRegimen(base, now)
        assertEquals(2, next.regimens.size)
        // EV biweekly over 60d (~5) + CPA daily over 60d (~61)
        assertTrue(next.doses.size in 60..70)
        assertNotNull(next.toast)
        // second call is a no-op-ish (already seeded)
        val again = DashboardReducer.loadSampleRegimen(next, now)
        assertEquals(2, again.regimens.size)
    }

    @Test
    fun saveRegimenFromDraftExpandsElapsedPortion() {
        val draft = DashboardReducer.editDose(base) {
            it.copy(repeat = true, repeatEveryDays = 14, repeatStartMillis = now.minusSeconds(60L * 86400).toEpochMilli())
        }
        val next = DashboardReducer.saveRegimen(draft, now)
        assertEquals(1, next.regimens.size)
        assertEquals(5, next.doses.size)
        assertEquals(DashboardSheet.NONE, next.sheet)
    }

    @Test
    fun clinicAddEditDeleteFlow() {
        var s = DashboardReducer.openClinics(base)
        assertTrue(s.clinicsOpen)
        s = DashboardReducer.newClinic(s)
        assertNotNull(s.clinicDraft)
        // blank name -> no-op
        assertSame(s, DashboardReducer.saveClinic(s))
        s = DashboardReducer.editClinicDraft(s) { it.copy(name = "OO의원", region = "서울") }
        s = DashboardReducer.saveClinic(s)
        assertEquals(1, s.clinics.size)
        assertNull(s.clinicDraft)
        assertEquals("OO의원", s.clinics.single().name)

        val id = s.clinics.single().id
        s = DashboardReducer.editClinic(s, id)
        s = DashboardReducer.editClinicDraft(s) { it.copy(memo = "예약 필수") }
        s = DashboardReducer.saveClinic(s)
        assertEquals(1, s.clinics.size) // edited in place, not duplicated
        assertEquals("예약 필수", s.clinics.single().memo)

        s = DashboardReducer.deleteClinic(s, id)
        assertTrue(s.clinics.isEmpty())
        s = DashboardReducer.closeClinics(s)
        assertFalse(s.clinicsOpen)
    }

    @Test
    fun deleteDoseAndLabRemoveOnlyTheTargetRow() {
        var s = DashboardReducer.saveDose(base, now)
        s = DashboardReducer.saveDose(s, now.plusSeconds(60))
        s = DashboardReducer.saveLab(DashboardReducer.editLab(s) { it.copy(e2 = "200") }, now)
        val doseId = s.doses.first().id
        val labId = s.labs.single().id

        s = DashboardReducer.deleteDose(s, doseId)
        assertEquals(1, s.doses.size)
        assertFalse(s.doses.any { it.id == doseId })
        assertEquals(1, s.labs.size)
        assertNotNull(s.toast)

        s = DashboardReducer.deleteLab(s, labId)
        assertTrue(s.labs.isEmpty())
        assertEquals(1, s.doses.size)
    }

    @Test
    fun deleteRegimenKeepsTheEventsItGenerated() {
        val seeded = DashboardReducer.loadSampleRegimen(base, now)
        val doseCount = seeded.doses.size
        val regimenId = seeded.regimens.first().id

        val after = DashboardReducer.deleteRegimen(seeded, regimenId)
        assertEquals(1, after.regimens.size)
        assertEquals(doseCount, after.doses.size) // records untouched
    }

    @Test
    fun clearHelpersWipeTheRightCollections() {
        val seeded = DashboardReducer.saveLab(
            DashboardReducer.editLab(DashboardReducer.loadSampleRegimen(base, now)) { it.copy(e2 = "180") },
            now,
        )
        assertTrue(seeded.doses.isNotEmpty() && seeded.labs.isNotEmpty() && seeded.regimens.isNotEmpty())

        val noDoses = DashboardReducer.clearDoses(seeded)
        assertTrue(noDoses.doses.isEmpty())
        assertTrue(noDoses.labs.isNotEmpty() && noDoses.regimens.isNotEmpty())

        val noLabs = DashboardReducer.clearLabs(seeded)
        assertTrue(noLabs.labs.isEmpty())
        assertTrue(noLabs.doses.isNotEmpty())

        val cleared = DashboardReducer.clearAllRecords(seeded)
        assertTrue(cleared.doses.isEmpty() && cleared.labs.isEmpty() && cleared.regimens.isEmpty())
    }

    @Test
    fun calibrationStatusWithoutEvidenceStaysPreparing() {
        val zero = CalibrationStatus.of(includedLabs = 0, exposureScale = 1.0, canEstimate = false)
        assertEquals("예상 곡선 준비 중", zero.title)
        assertEquals(0, zero.progressPct)
        assertFalse(zero.steps.any { it.done })
        assertTrue(zero.steps.none { it.active })
    }

    @Test
    fun calibrationStatusWithLabsReportsExposureAdjustment() {
        val up = CalibrationStatus.of(includedLabs = 2, exposureScale = 1.3, canEstimate = true)
        assertEquals("개인화됨", up.title)
        assertTrue(up.subtitle.contains("상향"))
        assertTrue(up.steps[1].done)

        val one = CalibrationStatus.of(includedLabs = 1, exposureScale = 0.7, canEstimate = true)
        assertEquals("보정 중", one.title)
        assertTrue(one.subtitle.contains("하향"))
    }
}
