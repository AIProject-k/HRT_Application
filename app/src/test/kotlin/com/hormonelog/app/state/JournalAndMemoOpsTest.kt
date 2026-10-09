package com.hormonelog.app.state

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.dose
import com.hormonelog.app.lab
import com.hormonelog.app.settings
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.ExtraLab
import com.hormonelog.core.domain.JournalEntry
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.Symptom
import com.hormonelog.core.domain.VisitMemo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class JournalOpsTest {
    private val now = at(2026, 10, 5, 16, 30)
    private val empty = AppState(settings = settings)

    private fun open(tab: RecordTab) = JournalOps.open(empty, tab)

    @Test
    fun openingTheRecordsScreenStacksItOnTopOfWhereTheUserWas() {
        val s = open(RecordTab.BODY)
        assertEquals(Screen.RECORDS, s.screen)
        assertEquals(listOf(Screen.HOME), s.stack)
        assertEquals(RecordTab.BODY, s.journalDraft.tab)
    }

    @Test
    fun aConditionEntryKeepsTheScoreAndTheSymptomsAndGoesBack() {
        var s = open(RecordTab.CONDITION)
        s = JournalOps.draft(s) { it.copy(score = 4, note = "  slept well ") }
        s = JournalOps.toggleSymptom(s, Symptom.FATIGUE)
        s = JournalOps.toggleSymptom(s, Symptom.HEADACHE)
        s = JournalOps.toggleSymptom(s, Symptom.FATIGUE)
        val saved = JournalOps.save(s, now, SEOUL)

        val e = saved.journal.single()
        assertEquals(JournalEntry.Kind.CONDITION, e.kind)
        assertEquals(4, e.condition)
        assertEquals(setOf(Symptom.HEADACHE), e.symptoms)
        assertEquals("slept well", e.note)
        assertEquals(now, e.at)
        assertEquals(Screen.HOME, saved.screen)
        assertNotNull(saved.undo)
        assertEquals(0, RecordsReducer.undo(saved).journal.size)
    }

    @Test
    fun aBodyEntryNeedsAWeightOrAWholePressure() {
        var s = open(RecordTab.BODY)
        assertFalse(s.journalDraft.canSave)
        s = JournalOps.draft(s) { it.copy(systolic = "120") }
        assertFalse("half a pressure", s.journalDraft.canSave)
        s = JournalOps.draft(s) { it.copy(diastolic = "80") }
        assertTrue(s.journalDraft.canSave)
        s = JournalOps.draft(s) { it.copy(diastolic = "130") }
        assertFalse("the lower number cannot be higher", s.journalDraft.canSave)
        assertNotNull(s.journalDraft.pressureError)
        s = JournalOps.draft(s) { it.copy(systolic = "", diastolic = "", weight = "58.4") }
        assertTrue(s.journalDraft.canSave)
    }

    @Test
    fun aWeightAloneIsKeptWithoutAPressure() {
        val s = JournalOps.draft(open(RecordTab.BODY)) { it.copy(weight = "58.4") }
        val e = JournalOps.save(s, now, SEOUL).journal.single()
        assertEquals(58.4, e.weightKg!!, 0.0)
        assertNull(e.systolic)
        assertNull(e.diastolic)
        assertEquals(JournalEntry.Kind.BODY, e.kind)
    }

    @Test
    fun anAbsurdWeightIsRefused() {
        val s = JournalOps.draft(open(RecordTab.BODY)) { it.copy(weight = "5.8.4") }
        assertNotNull(s.journalDraft.weightError)
        assertFalse(s.journalDraft.canSave)
        assertEquals(s, JournalOps.save(s, now, SEOUL))
    }

    @Test
    fun extraLabsNeedOneValueAndKeepOnlyTheFilledOnes() {
        var s = open(RecordTab.LABS)
        assertFalse(s.journalDraft.canSave)
        s = JournalOps.draft(s) { it.copy(labs = mapOf(ExtraLab.LH to "4.2", ExtraLab.FSH to "", ExtraLab.POTASSIUM to "4.1")) }
        assertTrue(s.journalDraft.canSave)
        val e = JournalOps.save(s, now, SEOUL).journal.single()
        assertEquals(JournalEntry.Kind.EXTRA_LABS, e.kind)
        assertEquals(mapOf(ExtraLab.LH to 4.2, ExtraLab.POTASSIUM to 4.1), e.extraLabs)
        val bad = JournalOps.draft(s) { it.copy(labs = it.labs + (ExtraLab.SHBG to "abc")) }
        assertFalse(bad.journalDraft.canSave)
    }

    @Test
    fun editingAnEntryKeepsItsIdAndItsMomentAndReplacesItInPlace() {
        val saved = JournalOps.save(JournalOps.draft(open(RecordTab.CONDITION)) { it.copy(score = 2) }, now, SEOUL)
        val original = saved.journal.single()
        var s = JournalOps.edit(saved, original.id)
        assertEquals(RecordTab.CONDITION, s.journalDraft.tab)
        assertEquals(2, s.journalDraft.score)
        s = JournalOps.draft(s) { it.copy(score = 5) }
        val again = JournalOps.save(s, now.plusSeconds(86_400), SEOUL)
        assertEquals(1, again.journal.size)
        assertEquals(original.id, again.journal.single().id)
        assertEquals(original.at, again.journal.single().at)
        assertEquals(5, again.journal.single().condition)
    }

    @Test
    fun deletingAnEntryCanBeUndone() {
        val saved = JournalOps.save(open(RecordTab.CONDITION), now, SEOUL)
        val id = saved.journal.single().id
        val gone = JournalOps.delete(saved, id)
        assertEquals(0, gone.journal.size)
        assertEquals(1, RecordsReducer.undo(gone).journal.size)
    }

    // ── stock ─────────────────────────────────────────────────

    private val ampoule = Combo(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, DoseUnit.MG, null, uses = 3, last = now)

    @Test
    fun aProductIsCountedFromZeroAndNeverTwice() {
        var s = JournalOps.addStock(empty.copy(sheet = Sheet.StockAdd), ampoule)
        assertEquals(1, s.stock.size)
        assertEquals(0, s.stock.single().count)
        assertEquals(Sheet.None, s.sheet)
        s = JournalOps.addStock(s, ampoule)
        assertEquals(1, s.stock.size)
        s = JournalOps.addStock(s, ampoule.copy(amount = 4.0))
        assertEquals(2, s.stock.size)
    }

    @Test
    fun theCountMovesByOneAndStaysBetweenZeroAndNineThousand() {
        var s = JournalOps.addStock(empty, ampoule)
        val id = s.stock.single().id
        s = JournalOps.changeStock(s, id, -1)
        assertEquals(0, s.stock.single().count)
        s = JournalOps.changeStock(s, id, +1)
        s = JournalOps.changeStock(s, id, +1)
        assertEquals(2, s.stock.single().count)
        assertEquals(9999, JournalOps.changeStock(s, id, +50_000).stock.single().count)
    }

    @Test
    fun removingAProductCanBeUndone() {
        val s = JournalOps.addStock(empty, ampoule)
        val removed = JournalOps.removeStock(s, s.stock.single().id)
        assertEquals(0, removed.stock.size)
        assertEquals(1, RecordsReducer.undo(removed).stock.size)
    }

    @Test
    fun aDoseRecordedByDuplicatingARowAlsoUsesStock() {
        val d = dose(at(2026, 10, 3, 9))
        var s = JournalOps.addStock(empty.copy(doses = listOf(d)), ampoule)
        s = JournalOps.changeStock(JournalOps.changeStock(s, s.stock.single().id, +1), s.stock.single().id, +1)
        val copied = TimelineOps.duplicate(s, RowTarget.Dose(d.id), now, SEOUL)
        assertEquals(2, copied.doses.size)
        assertEquals(1, copied.stock.single().count)
    }
}

class MemoOpsTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val empty = AppState(settings = settings, screen = Screen.MEMOS)

    @Test
    fun aMemoNeedsATitleAndOpensOnToday() {
        var s = MemoOps.newMemo(empty, today)
        assertEquals(Sheet.MemoEditor, s.sheet)
        assertEquals(today, s.memoDraft!!.date)
        assertEquals(s, MemoOps.saveMemo(s))
        s = MemoOps.memoDraft(s) { it.copy(title = "  Dose change  ", body = " EV 4 -> 5 ", prescription = "", link = "") }
        val saved = MemoOps.saveMemo(s)
        val m = saved.memos.single()
        assertEquals("Dose change", m.title)
        assertEquals("EV 4 -> 5", m.body)
        assertNull("an empty text is not a prescription", m.prescription)
        assertNull(m.link)
        assertEquals(Sheet.None, saved.sheet)
        assertNull(saved.memoDraft)
        assertNotNull(saved.undo)
    }

    @Test
    fun onlyAWebAddressCanBeKeptAsALink() {
        val s = MemoOps.memoDraft(MemoOps.newMemo(empty, today)) { it.copy(title = "x", link = "ftp://example.com") }
        assertNotNull(s.memoDraft!!.linkError)
        assertEquals(s, MemoOps.saveMemo(s))
        for (ok in listOf("https://example.com/a", "http://example.com", "  https://example.com  ")) {
            val good = MemoOps.memoDraft(s) { it.copy(link = ok) }
            assertNull(ok, good.memoDraft!!.linkError)
            assertEquals(ok.trim(), MemoOps.saveMemo(good).memos.single().link)
        }
        // A string that merely contains a scheme later on is not a web address.
        assertNotNull(MemoOps.memoDraft(s) { it.copy(link = "javascript:alert(1)//https://x") }.memoDraft!!.linkError)
    }

    @Test
    fun editingAMemoKeepsItsIdAndTheNewestComesFirst() {
        val old = VisitMemo(UUID.randomUUID(), LocalDate.of(2026, 6, 19), "First", "x")
        val newer = VisitMemo(UUID.randomUUID(), LocalDate.of(2026, 9, 18), "Second", "y")
        var s = empty.copy(memos = listOf(newer, old))
        s = MemoOps.editMemo(s, old.id)
        assertEquals("First", s.memoDraft!!.title)
        s = MemoOps.memoDraft(s) { it.copy(date = LocalDate.of(2026, 10, 1), title = "First, edited") }
        val saved = MemoOps.saveMemo(s)
        assertEquals(2, saved.memos.size)
        assertEquals(listOf("First, edited", "Second"), saved.memos.map { it.title })
        assertEquals(old.id, saved.memos.first().id)
    }

    @Test
    fun deletingAMemoAsksFirstAndCanBeUndone() {
        val memo = VisitMemo(UUID.randomUUID(), today, "A", "")
        val asked = MemoOps.askDeleteMemo(empty.copy(memos = listOf(memo)), memo.id)
        assertEquals(Confirm.DeleteMemo(memo.id), asked.confirm)
        val gone = MemoOps.deleteMemo(asked, memo.id)
        assertEquals(0, gone.memos.size)
        assertNull(gone.confirm)
        assertEquals(1, RecordsReducer.undo(gone).memos.size)
    }

    @Test
    fun theNextVisitCanBeSetAndCleared() {
        val millis = at(2026, 10, 15, 10, 30).toEpochMilli()
        val set = MemoOps.setVisit(empty.copy(sheet = Sheet.Appointment), millis)
        assertEquals(millis, set.nextVisitMillis)
        assertEquals(Sheet.None, set.sheet)
        val cleared = MemoOps.setVisit(set, null)
        assertNull(cleared.nextVisitMillis)
        assertEquals(millis, RecordsReducer.undo(cleared).nextVisitMillis)
    }

    @Test
    fun aClinicNeedsANameAndIsKeptInAlphabeticalOrder() {
        var s = MemoOps.newClinic(empty)
        assertEquals(Sheet.ClinicEditor, s.sheet)
        assertEquals(s, MemoOps.saveClinic(s))
        s = MemoOps.saveClinic(MemoOps.clinicDraft(s) { it.copy(name = "Zeta", region = " Seoul ") })
        s = MemoOps.saveClinic(MemoOps.clinicDraft(MemoOps.newClinic(s)) { it.copy(name = "Alpha", sourceUrl = "https://example.com/c") })
        assertEquals(listOf("Alpha", "Zeta"), s.clinics.map { it.name })
        assertEquals("Seoul", s.clinics.last().region)
        assertEquals(Sheet.None, s.sheet)
    }

    @Test
    fun aClinicLinkMustBeAWebAddressToo() {
        val s = MemoOps.clinicDraft(MemoOps.newClinic(empty)) { it.copy(name = "A", sourceUrl = "file:///etc/passwd") }
        assertNotNull(s.clinicDraft!!.linkError)
        assertEquals(s, MemoOps.saveClinic(s))
    }

    @Test
    fun deletingAClinicCanBeUndone() {
        var s = MemoOps.saveClinic(MemoOps.clinicDraft(MemoOps.newClinic(empty)) { it.copy(name = "Alpha") })
        val id = s.clinics.single().id
        s = MemoOps.deleteClinic(MemoOps.askDeleteClinic(s, id), id)
        assertEquals(0, s.clinics.size)
        assertEquals(1, RecordsReducer.undo(s).clinics.size)
    }

    @Test
    fun aMemoRowMenuDuplicatesToToday() {
        val memo = VisitMemo(UUID.randomUUID(), LocalDate.of(2026, 9, 18), "A", "b")
        val s = TimelineOps.duplicate(empty.copy(memos = listOf(memo)), RowTarget.Memo(memo.id), at(2026, 10, 5, 16), SEOUL)
        assertEquals(2, s.memos.size)
        assertEquals(LocalDate.of(2026, 10, 5), s.memos.first().date)
        assertTrue(s.memos.map { it.id }.toSet().size == 2)
    }
}

class TimelineOpsTest {
    private val now = at(2026, 10, 5, 16, 30)
    private val d = dose(at(2026, 10, 3, 9))
    private val l = lab(at(2026, 9, 22, 14), e2 = 168.0)
    private val s = AppState(settings = settings, doses = listOf(d), labs = listOf(l))

    @Test
    fun filtersChangeOneThingAtATimeAndResetClearsThemAll() {
        var t = TimelineOps.type(s, TimelineType.LAB)
        t = TimelineOps.drug(t, Drug.CYPROTERONE)
        t = TimelineOps.period(t, TimelinePeriod.QUARTER)
        t = TimelineOps.query(t, "abc")
        assertFalse(t.timeline.isDefault)
        assertTrue(TimelineOps.reset(t).timeline.isDefault)
    }

    @Test
    fun aSearchIsCappedAtSixtyCharacters() {
        assertEquals(60, TimelineOps.query(s, "a".repeat(200)).timeline.query.length)
    }

    @Test
    fun openMonthsToggleOnAndOff() {
        val open = TimelineOps.toggleMonth(s, "2026-09")
        assertEquals(setOf("2026-09"), open.timeline.openMonths)
        assertTrue(TimelineOps.toggleMonth(open, "2026-09").timeline.openMonths.isEmpty())
    }

    @Test
    fun duplicatingADoseRecordsTheSameThingNowAsAManualEntry() {
        val copied = TimelineOps.duplicate(s, RowTarget.Dose(d.id), now, SEOUL)
        assertEquals(2, copied.doses.size)
        val fresh = copied.doses.last()
        assertEquals(now, fresh.occurredAt)
        assertEquals(d.amountEntered, fresh.amountEntered, 0.0)
        assertTrue(fresh.id != d.id)
        assertEquals(com.hormonelog.core.domain.RecordSource.MANUAL, fresh.source)
        assertNotNull(copied.undo)
    }

    @Test
    fun aDuplicatedLabIsNeverTheBaselineOne() {
        val baseline = l.copy(isBaseline = true)
        val copied = TimelineOps.duplicate(s.copy(labs = listOf(baseline)), RowTarget.Lab(baseline.id), now, SEOUL)
        assertEquals(2, copied.labs.size)
        assertFalse(copied.labs.last().isBaseline)
        assertEquals(now, copied.labs.last().collectedAt)
    }

    @Test
    fun editingARowOpensThatRecordInItsOwnEditor() {
        assertEquals(Sheet.Dose, TimelineOps.edit(s, RowTarget.Dose(d.id)).sheet)
        assertEquals(d.id, TimelineOps.edit(s, RowTarget.Dose(d.id)).doseDraft.editingId)
        assertEquals(Sheet.Lab, TimelineOps.edit(s, RowTarget.Lab(l.id)).sheet)
        // A row that is gone (deleted in the meantime) changes nothing.
        assertEquals(s, TimelineOps.edit(s, RowTarget.Dose(UUID.randomUUID())))
    }

    @Test
    fun deletingARowRemovesOnlyThatRecord() {
        val gone = TimelineOps.delete(s, RowTarget.Dose(d.id))
        assertEquals(0, gone.doses.size)
        assertEquals(1, gone.labs.size)
        assertEquals(Sheet.None, gone.sheet)
    }
}
