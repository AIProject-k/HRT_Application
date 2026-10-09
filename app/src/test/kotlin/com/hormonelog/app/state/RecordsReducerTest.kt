package com.hormonelog.app.state

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.dayMillis
import com.hormonelog.app.dose
import com.hormonelog.app.lab
import com.hormonelog.app.settings
import com.hormonelog.app.weekly
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.StockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RecordsReducerTest {
    /** Monday 5 October 2026, 16:30 in Seoul. */
    private val now = at(2026, 10, 5, 16, 30)
    private val empty = AppState(settings = settings)

    private fun withDraft(s: AppState = empty, draft: DoseDraft) = s.copy(doseDraft = draft, sheet = Sheet.Dose)

    @Test
    fun savingADoseAddsItAndKeepsAnUndoPoint() {
        val s = RecordsReducer.saveDose(withDraft(draft = DoseDraft(amountText = "5")), now, SEOUL)
        assertEquals(1, s.doses.size)
        assertEquals(now, s.doses.single().occurredAt)
        assertEquals(Sheet.None, s.sheet)
        assertNotNull(s.undo)
        assertEquals(0, RecordsReducer.undo(s).doses.size)
    }

    @Test
    fun aDoseWithoutAnAmountIsNotSaved() {
        val s = RecordsReducer.saveDose(withDraft(draft = DoseDraft(amountText = "")), now, SEOUL)
        assertEquals(0, s.doses.size)
        assertEquals(Sheet.Dose, s.sheet)
    }

    @Test
    fun aTimeAheadOfNowIsRefusedNotQuietlyMoved() {
        val draft = DoseDraft(amountText = "5", time = DoseTime.PICK, pickedMillis = now.plusSeconds(3600).toEpochMilli())
        assertEquals(0, RecordsReducer.saveDose(withDraft(draft = draft), now, SEOUL).doses.size)
    }

    @Test
    fun aSameDrugDoseWithinSixHoursAsksBeforeAddingAnother() {
        val earlier = dose(now.minusSeconds(2 * 3600))
        val s = RecordsReducer.saveDose(withDraft(empty.copy(doses = listOf(earlier)), DoseDraft(amountText = "5")), now, SEOUL)
        assertEquals(1, s.doses.size)
        assertEquals(earlier, (s.confirm as Confirm.DuplicateDose).existing)
        // "그래도 기록": the same save, forced.
        val forced = RecordsReducer.confirmDuplicate(s, now, SEOUL)
        assertEquals(2, forced.doses.size)
        assertNull(forced.confirm)
    }

    @Test
    fun aDifferentDrugAtTheSameTimeIsNotADuplicate() {
        val cpa = dose(now.minusSeconds(3600), drug = Drug.CYPROTERONE, route = Route.ORAL, amount = 12.5)
        val s = RecordsReducer.saveDose(withDraft(empty.copy(doses = listOf(cpa)), DoseDraft(amountText = "5")), now, SEOUL)
        assertEquals(2, s.doses.size)
        assertNull(s.confirm)
    }

    @Test
    fun recordingADoseUsesOneUnitOfMatchingStock() {
        val ampoules = StockItem(UUID.randomUUID(), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, DoseUnit.MG, count = 3)
        val tablets = StockItem(UUID.randomUUID(), Drug.CYPROTERONE, Route.ORAL, 12.5, DoseUnit.MG, count = 18)
        val s = RecordsReducer.saveDose(withDraft(empty.copy(stock = listOf(ampoules, tablets)), DoseDraft(amountText = "5")), now, SEOUL)
        assertEquals(listOf(2, 18), s.stock.map { it.count })
    }

    @Test
    fun aMissedDoseIsRecordedButDoesNotUseStock() {
        val ampoules = StockItem(UUID.randomUUID(), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, DoseUnit.MG, count = 3)
        val s = RecordsReducer.saveDose(withDraft(empty.copy(stock = listOf(ampoules)), DoseDraft(amountText = "5", status = DoseStatus.SKIPPED)), now, SEOUL)
        assertEquals(DoseStatus.SKIPPED, s.doses.single().status)
        assertEquals(3, s.stock.single().count)
    }

    @Test
    fun quickLogRepeatsTheLastEstrogenAsTakenNow() {
        val last = dose(at(2026, 10, 3, 9), amount = 4.0)
        val s = RecordsReducer.quickLog(empty.copy(doses = listOf(last)), now, SEOUL)
        assertEquals(2, s.doses.size)
        assertEquals(now, s.doses.last().occurredAt)
        assertEquals(4.0, s.doses.last().amountEntered, 0.0)
    }

    @Test
    fun quickLogWithNoHistoryDoesNothing() {
        assertEquals(empty, RecordsReducer.quickLog(empty, now, SEOUL))
    }

    @Test
    fun quickLogSkipsAnAntiAndrogenWhenChoosingWhatToRepeat() {
        val ev = dose(at(2026, 10, 3, 9))
        val cpa = dose(at(2026, 10, 4, 8, 30), drug = Drug.CYPROTERONE, route = Route.ORAL, amount = 12.5)
        val s = RecordsReducer.quickLog(empty.copy(doses = listOf(ev, cpa)), now, SEOUL)
        assertEquals(Drug.ESTRADIOL_VALERATE, s.doses.last().drug)
    }

    @Test
    fun clearAllEmptiesTheRecordsAndOneTapBringsThemBack() {
        val s0 = empty.copy(doses = listOf(dose(now.minusSeconds(86_400))), labs = listOf(lab(now, e2 = 100.0)))
        val cleared = RecordsReducer.clearAll(s0)
        assertEquals(0, cleared.recordCount)
        assertEquals(s0.doses, RecordsReducer.undo(cleared).doses)
        assertEquals(s0.labs, RecordsReducer.undo(cleared).labs)
    }

    @Test
    fun clearAllTakesTheRecordsSetAsideAsUnreadableToo_andUndoBringsThemBack() {
        val kept = mapOf("doses" to listOf("""{"id":"x","drug":"FUTURE"}"""))
        val s0 = empty.copy(doses = listOf(dose(now.minusSeconds(86_400))), carried = kept)
        val cleared = RecordsReducer.clearAll(s0)
        assertTrue("nothing set aside is left to be written back", cleared.carried.isEmpty())
        assertEquals(kept, RecordsReducer.undo(cleared).carried)
    }

    @Test
    fun aNewPlanOverARunningOneAsksWhichToKeep() {
        val running = weekly(at(2026, 9, 5))
        val draft = DoseDraft(repeat = true, amountText = "6", startMillis = dayMillis(2026, 10, 5))
        val s = RecordsReducer.saveRegimen(withDraft(empty.copy(regimens = listOf(running)), draft), now, SEOUL)
        val ask = s.confirm as Confirm.ReplaceRegimen
        assertEquals(running, ask.existing)
        val done = RecordsReducer.confirmReplace(s)
        assertEquals(2, done.regimens.size)
        // The old plan stops just before the new one starts; nothing it already produced is touched.
        val old = done.regimens.first { it.id == running.id }
        assertFalse(old.active)
        assertEquals(done.regimens.first { it.id != running.id }.startAt.minusSeconds(1), old.endAt)
    }

    @Test
    fun aPlanThatStartedInThePastOffersItsPastDosesAndRecordsThemOnlyOnYes() {
        val draft = DoseDraft(repeat = true, amountText = "5", interval = IntervalChoice.WEEKLY, startMillis = dayMillis(2026, 9, 7))
        val asked = RecordsReducer.saveRegimen(withDraft(draft = draft), now, SEOUL)
        val pending = (asked.confirm as Confirm.Backfill).pending
        // Mondays 9/7, 9/14, 9/21, 9/28 and this morning at 09:00.
        assertEquals(5, pending.doses.size)
        assertEquals(0, asked.doses.size)

        val yes = RecordsReducer.confirmBackfill(asked)
        assertEquals(5, yes.doses.size)
        assertTrue(yes.undo != null)

        val no = RecordsReducer.declineBackfill(asked)
        assertEquals(0, no.doses.size)
        assertEquals(1, no.regimens.size)
    }

    @Test
    fun aSavedLabShowsItsResultAndKeepsTheUnitsJustUsed() {
        val s0 = empty.copy(
            doses = listOf(dose(at(2026, 10, 3, 9))),
            labDraft = LabDraft(e2 = "250", draw = DrawChoice.NOW),
            sheet = Sheet.Lab,
        )
        val s = RecordsReducer.saveLab(s0, now, SEOUL)
        assertEquals(1, s.labs.size)
        assertEquals(Screen.LAB_RESULT, s.screen)
        assertEquals(250.0, s.labResult!!.e2!!.measuredPg, 0.001)
        assertEquals(Sheet.None, s.sheet)
    }

    @Test
    fun aLabWithNoValueAboveZeroIsNotSaved() {
        val s = RecordsReducer.saveLab(empty.copy(labDraft = LabDraft(e2 = "0", tt = "")), now, SEOUL)
        assertEquals(0, s.labs.size)
    }

    @Test
    fun aLabWithAnUnknownDrawTimeIsKeptButBelongsToNoMoment() {
        val s = RecordsReducer.saveLab(empty.copy(labDraft = LabDraft(e2 = "410", draw = DrawChoice.UNKNOWN)), now, SEOUL)
        assertNull(s.labs.single().collectedAt)
        assertNull(s.labResult!!.collectedAt)
    }

    @Test
    fun importingSkipsWhatIsAlreadyHereAndCountsIt() {
        val here = dose(at(2026, 10, 3, 9))
        val s0 = empty.copy(doses = listOf(here))
        val incoming = listOf(here.copy(id = UUID.randomUUID()), dose(at(2026, 10, 4, 9)))
        assertEquals(1, RecordsReducer.countNew(s0, incoming, emptyList()))
        val (merged, duplicates) = RecordsReducer.mergeImported(s0, incoming, emptyList())
        assertEquals(2, merged.doses.size)
        assertEquals(1, duplicates)
    }

    @Test
    fun deletingADoseCanBeUndone() {
        val d = dose(now.minusSeconds(86_400))
        val s = RecordsReducer.deleteDose(empty.copy(doses = listOf(d)), d.id)
        assertEquals(0, s.doses.size)
        assertEquals(listOf(d), RecordsReducer.undo(s).doses)
    }

    @Test
    fun deletingAPlanKeepsWhatItAlreadyProduced() {
        val plan = weekly(at(2026, 9, 5))
        val past = dose(at(2026, 9, 5, 9))
        val s = RecordsReducer.deleteRegimen(empty.copy(regimens = listOf(plan), doses = listOf(past)), plan.id)
        assertEquals(0, s.regimens.size)
        assertEquals(1, s.doses.size)
    }

    @Test
    fun anAbandonedEditIsDroppedButAHalfTypedNewRecordIsKept() {
        val typed = empty.copy(doseDraft = DoseDraft(amountText = "7"), sheet = Sheet.Dose)
        assertEquals("7", RecordsReducer.closeSheet(typed).doseDraft.amountText)
        val editing = typed.copy(doseDraft = DoseDraft(editingId = UUID.randomUUID(), amountText = "7"))
        assertEquals("", RecordsReducer.closeSheet(editing).doseDraft.amountText)
    }
}
