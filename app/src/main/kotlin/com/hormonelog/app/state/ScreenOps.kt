package com.hormonelog.app.state

import com.hormonelog.core.domain.Clinic
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.JournalEntry
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.StockItem
import com.hormonelog.core.domain.VisitMemo
import com.hormonelog.core.domain.isAntiandrogen
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** The flow chart's own state: which hormone, how far back, where the finger is. */
object FlowOps {
    fun series(s: AppState, series: HormoneSeries) = s.copy(flow = s.flow.copy(series = series, scrub = null))
    fun range(s: AppState, range: ChartRange) = s.copy(flow = s.flow.copy(range = range, scrub = null))
    fun customFrom(s: AppState, millis: Long) = s.copy(flow = s.flow.copy(range = ChartRange.CUSTOM, customFromMillis = millis, scrub = null))
    fun scrub(s: AppState, fraction: Float?) = s.copy(flow = s.flow.copy(scrub = fraction?.coerceIn(0f, 1f)))
    fun guide(s: AppState, on: Boolean) = s.copy(flow = s.flow.copy(guide = on))
}

/** The timeline's filters and what each row's menu does. */
object TimelineOps {
    fun query(s: AppState, text: String) = s.copy(timeline = s.timeline.copy(query = text.take(60)))
    fun type(s: AppState, type: TimelineType) = s.copy(timeline = s.timeline.copy(type = type))
    fun drug(s: AppState, drug: com.hormonelog.core.domain.Drug?) = s.copy(timeline = s.timeline.copy(drug = drug))
    fun period(s: AppState, period: TimelinePeriod) = s.copy(timeline = s.timeline.copy(period = period))
    fun reset(s: AppState) = s.copy(timeline = s.timeline.copy(query = "", type = TimelineType.ALL, drug = null, period = TimelinePeriod.ALL))
    fun toggleMonth(s: AppState, key: String): AppState {
        val open = s.timeline.openMonths
        return s.copy(timeline = s.timeline.copy(openMonths = if (key in open) open - key else open + key))
    }

    fun menu(s: AppState, target: RowTarget) = s.copy(sheet = Sheet.RowMenu(target))

    /** The row's own editor. */
    fun edit(s: AppState, target: RowTarget): AppState = when (target) {
        is RowTarget.Dose -> RecordsReducer.editDose(s, target.id)
        is RowTarget.Lab -> RecordsReducer.editLab(s, target.id)
        is RowTarget.Journal -> JournalOps.edit(s, target.id)
        is RowTarget.Memo -> MemoOps.editMemo(s, target.id)
    }

    /** Record the same thing again, right now. */
    fun duplicate(s: AppState, target: RowTarget, now: Instant, zone: ZoneId): AppState = when (target) {
        is RowTarget.Dose -> {
            val d = s.doses.firstOrNull { it.id == target.id }
            if (d == null) s else {
                val copy = d.copy(id = UUID.randomUUID(), occurredAt = now, sourceZoneId = zone.id, status = DoseStatus.ADMINISTERED, revision = 1, source = RecordSource.MANUAL)
                s.copy(
                    doses = (s.doses + copy).sortedBy { it.occurredAt },
                    stock = RecordsReducer.useStock(s.stock, copy),
                    sheet = Sheet.None, undo = RecordsReducer.undoPoint(s), toast = "지금 시각으로 복제했어요",
                )
            }
        }
        is RowTarget.Lab -> {
            val l = s.labs.firstOrNull { it.id == target.id }
            if (l == null) s else {
                val copy = l.copy(id = UUID.randomUUID(), collectedAt = now, sourceZoneId = zone.id, isBaseline = false, source = RecordSource.MANUAL)
                s.copy(labs = (s.labs + copy).sortedBy { it.collectedAt ?: Instant.MIN }, sheet = Sheet.None, undo = RecordsReducer.undoPoint(s), toast = "지금 시각으로 복제했어요")
            }
        }
        is RowTarget.Journal -> {
            val j = s.journal.firstOrNull { it.id == target.id }
            if (j == null) s else s.copy(
                journal = (s.journal + j.copy(id = UUID.randomUUID(), at = now, sourceZoneId = zone.id)).sortedBy { it.at },
                sheet = Sheet.None, undo = RecordsReducer.undoPoint(s), toast = "지금 시각으로 복제했어요",
            )
        }
        is RowTarget.Memo -> {
            val m = s.memos.firstOrNull { it.id == target.id }
            if (m == null) s else s.copy(
                memos = (s.memos + m.copy(id = UUID.randomUUID(), date = now.atZone(zone).toLocalDate())).sortedByDescending { it.date },
                sheet = Sheet.None, undo = RecordsReducer.undoPoint(s), toast = "오늘 날짜로 복제했어요",
            )
        }
    }

    fun delete(s: AppState, target: RowTarget): AppState = when (target) {
        is RowTarget.Dose -> RecordsReducer.deleteDose(s, target.id)
        is RowTarget.Lab -> RecordsReducer.deleteLab(s, target.id)
        is RowTarget.Journal -> JournalOps.delete(s, target.id)
        is RowTarget.Memo -> MemoOps.deleteMemo(s, target.id)
    }
}

/** 다른 기록: condition, weight and blood pressure, extra labs, and the stock of what is taken. */
object JournalOps {
    fun open(s: AppState, tab: RecordTab): AppState = Nav.push(s, Screen.RECORDS).copy(journalDraft = JournalDraft(tab = tab))

    fun edit(s: AppState, id: UUID): AppState {
        val j = s.journal.firstOrNull { it.id == id } ?: return s
        val tab = when (j.kind) {
            JournalEntry.Kind.CONDITION -> RecordTab.CONDITION
            JournalEntry.Kind.BODY -> RecordTab.BODY
            JournalEntry.Kind.EXTRA_LABS -> RecordTab.LABS
        }
        val draft = JournalDraft(
            editingId = id, tab = tab, score = j.condition ?: 3, symptoms = j.symptoms, note = j.note.orEmpty(),
            weight = j.weightKg?.let(::plainNumber).orEmpty(), systolic = j.systolic?.toString().orEmpty(), diastolic = j.diastolic?.toString().orEmpty(),
            labs = j.extraLabs.mapValues { plainNumber(it.value) },
        )
        return Nav.push(s, Screen.RECORDS).copy(journalDraft = draft)
    }

    fun draft(s: AppState, block: (JournalDraft) -> JournalDraft) = s.copy(journalDraft = block(s.journalDraft))

    fun setTab(s: AppState, tab: RecordTab) = draft(s) { it.copy(tab = tab) }

    fun toggleSymptom(s: AppState, symptom: com.hormonelog.core.domain.Symptom) = draft(s) {
        it.copy(symptoms = if (symptom in it.symptoms) it.symptoms - symptom else it.symptoms + symptom)
    }

    /** Saves what the open tab holds, then goes back to where the user came from. */
    fun save(s: AppState, now: Instant, zone: ZoneId): AppState {
        val d = s.journalDraft
        if (!d.canSave) return s
        val previous = d.editingId?.let { id -> s.journal.firstOrNull { it.id == id } }
        val base = JournalEntry(id = previous?.id ?: UUID.randomUUID(), at = previous?.at ?: now, sourceZoneId = zone.id)
        val entry = when (d.tab) {
            RecordTab.CONDITION -> base.copy(condition = d.score, symptoms = d.symptoms, note = d.note.trim().ifBlank { null })
            RecordTab.BODY -> base.copy(
                weightKg = d.weightValue(),
                systolic = d.systolic.trim().toIntOrNull()?.takeIf { d.diastolic.isNotBlank() },
                diastolic = d.diastolic.trim().toIntOrNull()?.takeIf { d.systolic.isNotBlank() },
            )
            RecordTab.LABS -> base.copy(extraLabs = d.labValues())
            RecordTab.STOCK -> return s
        }
        val journal = (if (previous != null) s.journal.map { if (it.id == entry.id) entry else it } else s.journal + entry).sortedBy { it.at }
        val saved = s.copy(
            journal = journal,
            journalDraft = JournalDraft(tab = d.tab),
            undo = RecordsReducer.undoPoint(s),
            toast = if (previous != null) "기록을 고쳤어요" else "기록했어요 · 타임라인에 보여요",
        )
        return Nav.back(saved).copy(toast = saved.toast, undo = saved.undo)
    }

    fun delete(s: AppState, id: UUID): AppState =
        s.copy(journal = s.journal.filterNot { it.id == id }, sheet = Sheet.None, toast = "기록을 삭제했어요", undo = RecordsReducer.undoPoint(s))

    // ── stock ─────────────────────────────────────────────────
    fun addStock(s: AppState, combo: Combo): AppState {
        if (s.stock.any { it.drug == combo.drug && it.route == combo.route && it.amountEntered == combo.amount && it.enteredUnit == combo.unit }) return s.copy(sheet = Sheet.None)
        val item = StockItem(UUID.randomUUID(), combo.drug, combo.route, combo.amount, combo.unit, count = 0)
        return s.copy(stock = s.stock + item, sheet = Sheet.None)
    }

    fun changeStock(s: AppState, id: UUID, delta: Int): AppState =
        s.copy(stock = s.stock.map { if (it.id == id) it.copy(count = (it.count + delta).coerceIn(0, 9999)) else it })

    fun removeStock(s: AppState, id: UUID): AppState = s.copy(stock = s.stock.filterNot { it.id == id }, undo = RecordsReducer.undoPoint(s), toast = "재고 항목을 지웠어요")
}

/** 반복 일정 screens: list, edit, end, conflict. */
object ScheduleOps {

    /** A new plan starts from what the person takes now, or from a plain weekly injection with no amount. */
    fun create(s: AppState, now: Instant, zone: ZoneId): AppState {
        val last = s.doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen }.maxByOrNull { it.occurredAt }
        val base = ScheduleDraft(weekdays = setOf(now.atZone(zone).dayOfWeek))
        val draft = if (last == null) base else base.copy(
            drug = last.drug,
            route = last.route,
            amountText = if (last.route == Route.PATCH) "" else plainNumber(last.amountEntered),
            patchStrength = last.patchMicrogramsPerDay ?: 50.0,
            patchCycle = last.patchCycle ?: PatchCycle.TWICE_WEEKLY,
        )
        return Nav.push(s, Screen.SCHEDULE_EDIT).copy(scheduleDraft = draft)
    }

    fun edit(s: AppState, id: UUID, zone: ZoneId): AppState {
        val r = s.regimens.firstOrNull { it.id == id } ?: return s
        val interval = when {
            r.isWeekdayPlan && r.weekdaySet.size == 2 -> IntervalChoice.TWICE_WEEKLY
            r.isWeekdayPlan -> IntervalChoice.WEEKLY
            r.everyDays == 1 -> IntervalChoice.DAILY
            r.everyDays == 14 -> IntervalChoice.BIWEEKLY
            r.everyDays == 7 -> IntervalChoice.WEEKLY
            else -> IntervalChoice.CUSTOM
        }
        val draft = ScheduleDraft(
            editingId = r.id, drug = r.drug, route = r.route,
            amountText = if (r.route == Route.PATCH) "" else plainNumber(r.amountEntered),
            patchStrength = r.patchMicrogramsPerDay ?: 50.0,
            patchCycle = r.patchCycle ?: PatchCycle.TWICE_WEEKLY,
            interval = interval,
            customDays = if (interval == IntervalChoice.CUSTOM) r.everyDays.toString() else "",
            weekdays = r.weekdaySet.ifEmpty { if (interval == IntervalChoice.WEEKLY) setOf(r.startAt.atZone(zone).dayOfWeek) else emptySet() },
            timeMinutes = r.timeMinutes ?: r.startAt.atZone(zone).let { it.hour * 60 + it.minute },
            startMillis = r.startAt.toEpochMilli(),
            endMillis = r.endAt?.toEpochMilli(),
        )
        return Nav.push(s, Screen.SCHEDULE_EDIT).copy(scheduleDraft = draft)
    }

    fun draft(s: AppState, block: (ScheduleDraft) -> ScheduleDraft): AppState = s.scheduleDraft?.let { s.copy(scheduleDraft = block(it)) } ?: s

    /** A route that cannot carry the chosen drug brings the first drug that can. */
    fun setRoute(s: AppState, route: Route): AppState = draft(s) { d ->
        d.copy(route = route, drug = if (d.drug in drugsFor(route)) d.drug else drugsFor(route).first())
    }

    fun setDrug(s: AppState, drug: Drug): AppState = draft(s) { it.copy(drug = drug) }

    /** Only digits and one dot can be a number; anything else is ignored rather than stored. */
    fun setAmount(s: AppState, text: String): AppState = draft(s) { it.copy(amountText = text.filter { ch -> ch.isDigit() || ch == '.' }.take(7)) }

    /** Picking an interval picks the weekdays that go with it, as the design does. */
    fun setInterval(s: AppState, interval: IntervalChoice, now: Instant, zone: ZoneId): AppState = draft(s) { d ->
        val today = now.atZone(zone).dayOfWeek
        d.copy(
            interval = interval,
            weekdays = when (interval) {
                IntervalChoice.WEEKLY -> d.weekdays.firstOrNull()?.let { setOf(it) } ?: setOf(today)
                IntervalChoice.TWICE_WEEKLY -> if (d.weekdays.size == 2) d.weekdays else twiceWeeklyDays(today)
                else -> emptySet()
            },
        )
    }

    /** A patch's change cycle is its interval: once a week, or on two days. */
    fun setPatchCycle(s: AppState, cycle: PatchCycle, now: Instant, zone: ZoneId): AppState =
        setInterval(draft(s) { it.copy(patchCycle = cycle) }, if (cycle == PatchCycle.WEEKLY) IntervalChoice.WEEKLY else IntervalChoice.TWICE_WEEKLY, now, zone)

    /** One day for "매주", two for "주 2회" (a third pick replaces the oldest). */
    fun toggleDay(s: AppState, day: DayOfWeek): AppState = draft(s) { d ->
        val on = day in d.weekdays
        val next = when {
            d.effectiveInterval == IntervalChoice.WEEKLY -> setOf(day)
            on -> d.weekdays - day
            d.weekdays.size >= 2 -> setOf(d.weekdays.last(), day)
            else -> d.weekdays + day
        }
        d.copy(weekdays = next)
    }

    fun regimenOf(d: ScheduleDraft, now: Instant, zone: ZoneId, existing: Regimen? = null): Regimen? {
        val today = now.atZone(zone).toLocalDate()
        val startDate = d.startMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
        val atTime = startDate.atTime(d.timeMinutes / 60, d.timeMinutes % 60).atZone(zone).toInstant()
        // A plan that starts today begins at its next occurrence, not at an hour that has already passed.
        val startAt = existing?.startAt ?: if (startDate == today) maxOf(atTime, now) else atTime
        val interval = d.effectiveInterval
        val startDay = startAt.atZone(zone).dayOfWeek
        val (everyDays, weekdays) = when (interval) {
            IntervalChoice.DAILY -> 1 to 0
            IntervalChoice.TWICE_WEEKLY -> 3 to Regimen.maskOf(d.weekdays.ifEmpty { twiceWeeklyDays(startDay) })
            IntervalChoice.WEEKLY -> 7 to Regimen.maskOf(d.weekdays.ifEmpty { setOf(startDay) })
            IntervalChoice.BIWEEKLY -> 14 to 0
            IntervalChoice.CUSTOM -> (d.customDays.trim().toIntOrNull() ?: return null) to 0
        }
        val amount = d.amount ?: return null
        val end = d.endMillis?.let {
            // The chosen day is the last day: the plan runs through the end of it.
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)
        }
        return Regimen(
            id = existing?.id ?: UUID.randomUUID(),
            drug = existing?.drug ?: d.drug,
            route = existing?.route ?: d.route,
            amountEntered = existing?.amountEntered ?: amount,
            enteredUnit = existing?.enteredUnit ?: if (d.isPatch) DoseUnit.UG_PER_DAY else DoseUnit.MG,
            everyDays = everyDays,
            startAt = startAt,
            endAt = end,
            active = end == null || end.isAfter(now),
            weekdays = weekdays,
            timeMinutes = d.timeMinutes,
            patchCycle = (existing?.patchCycle ?: d.patchCycle).takeIf { (existing?.route ?: d.route) == Route.PATCH },
        )
    }

    /** The first few dates the plan falls on, for the preview under the form. */
    fun preview(d: ScheduleDraft, now: Instant, zone: ZoneId, count: Int = 5): List<Instant> {
        val r = regimenOf(d, now, zone) ?: return emptyList()
        return Regimen.expected(r, r.startAt.minusSeconds(1), now.plusSeconds(120L * 86_400L), emptyList(), zone).take(count)
    }

    fun save(s: AppState, now: Instant, zone: ZoneId): AppState {
        val d = s.scheduleDraft ?: return s
        val todayMillis = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        if (!d.canSave(todayMillis)) return s
        val existing = d.editingId?.let { id -> s.regimens.firstOrNull { it.id == id } }
        val r = regimenOf(d, now, zone, existing) ?: return s
        if (existing != null) {
            val saved = s.copy(
                regimens = s.regimens.map { if (it.id == r.id) r else it },
                undo = RecordsReducer.undoPoint(s),
                toast = "일정을 저장했어요",
            )
            return leave(saved)
        }
        val backfill = RecordsReducer.backfillFor(s, r, now, zone)
        val running = s.regimens.firstOrNull { it.isRunningAt(now) && it.drug == r.drug && it.route == r.route }
        if (running != null) return s.copy(confirm = Confirm.ReplaceRegimen(running, r, backfill))
        return RecordsReducer.applyNewRegimen(leave(s), r, null, backfill)
    }

    /** Back to the schedule list, with the editor closed. */
    fun leave(s: AppState): AppState = Nav.back(s.copy(confirm = null)).copy(scheduleDraft = null)

    fun endToday(s: AppState, id: UUID, now: Instant): AppState = RecordsReducer.endRegimenToday(s, id, now)

    fun askDelete(s: AppState, id: UUID): AppState = s.copy(confirm = Confirm.DeleteRegimen(id))

    /** After deleting from the editor, the editor goes too. */
    fun afterDelete(s: AppState): AppState = if (s.screen == Screen.SCHEDULE_EDIT) leave(s) else s
}

/** 진료 메모, the next visit, and 병원 정보. */
object MemoOps {
    fun newMemo(s: AppState, today: LocalDate): AppState = s.copy(memoDraft = MemoDraft(date = today), sheet = Sheet.MemoEditor)

    fun editMemo(s: AppState, id: UUID): AppState {
        val m = s.memos.firstOrNull { it.id == id } ?: return s
        return s.copy(
            memoDraft = MemoDraft(m.id, m.date, m.title, m.body, m.prescription.orEmpty(), m.link.orEmpty()),
            sheet = Sheet.MemoEditor,
        )
    }

    fun memoDraft(s: AppState, block: (MemoDraft) -> MemoDraft) = s.memoDraft?.let { s.copy(memoDraft = block(it)) } ?: s

    fun saveMemo(s: AppState): AppState {
        val d = s.memoDraft ?: return s
        if (!d.canSave || d.linkError != null) return s
        val memo = VisitMemo(
            id = d.editingId ?: UUID.randomUUID(), date = d.date, title = d.title.trim(), body = d.body.trim(),
            prescription = d.prescription.trim().ifBlank { null }, link = d.link.trim().ifBlank { null },
        )
        val memos = if (d.editingId != null) s.memos.map { if (it.id == memo.id) memo else it } else s.memos + memo
        return s.copy(
            memos = memos.sortedByDescending { it.date }, memoDraft = null, sheet = Sheet.None,
            undo = RecordsReducer.undoPoint(s), toast = if (d.editingId != null) "메모를 고쳤어요" else "메모를 저장했어요",
        )
    }

    fun askDeleteMemo(s: AppState, id: UUID) = s.copy(confirm = Confirm.DeleteMemo(id))

    fun deleteMemo(s: AppState, id: UUID): AppState =
        s.copy(memos = s.memos.filterNot { it.id == id }, confirm = null, sheet = Sheet.None, undo = RecordsReducer.undoPoint(s), toast = "메모를 삭제했어요")

    fun setVisit(s: AppState, millis: Long?): AppState =
        s.copy(nextVisitMillis = millis, sheet = Sheet.None, undo = RecordsReducer.undoPoint(s), toast = if (millis == null) "진료 예약을 지웠어요" else "진료 예약을 저장했어요")

    // ── clinics ──
    fun newClinic(s: AppState): AppState = s.copy(clinicDraft = ClinicDraft(), sheet = Sheet.ClinicEditor)

    fun editClinic(s: AppState, id: UUID): AppState {
        val c = s.clinics.firstOrNull { it.id == id } ?: return s
        return s.copy(
            clinicDraft = ClinicDraft(c.id, c.name, c.region, c.prescriptionBasis, c.telehealth, c.priceNote, c.memo, c.sourceUrl),
            sheet = Sheet.ClinicEditor,
        )
    }

    fun clinicDraft(s: AppState, block: (ClinicDraft) -> ClinicDraft) = s.clinicDraft?.let { s.copy(clinicDraft = block(it)) } ?: s

    fun saveClinic(s: AppState): AppState {
        val d = s.clinicDraft ?: return s
        if (!d.canSave || d.linkError != null) return s
        val clinic = Clinic(
            id = d.editingId ?: UUID.randomUUID(), name = d.name.trim(), region = d.region.trim(),
            prescriptionBasis = d.basis, telehealth = d.telehealth, priceNote = d.priceNote.trim(),
            memo = d.memo.trim(), sourceUrl = d.sourceUrl.trim(),
        )
        val list = if (d.editingId != null) s.clinics.map { if (it.id == clinic.id) clinic else it } else s.clinics + clinic
        return s.copy(clinics = list.sortedBy { it.name }, clinicDraft = null, sheet = Sheet.None, undo = RecordsReducer.undoPoint(s), toast = "병원 정보를 저장했어요")
    }

    fun askDeleteClinic(s: AppState, id: UUID) = s.copy(confirm = Confirm.DeleteClinic(id))

    fun deleteClinic(s: AppState, id: UUID): AppState =
        s.copy(clinics = s.clinics.filterNot { it.id == id }, confirm = null, sheet = Sheet.None, clinicDraft = null, undo = RecordsReducer.undoPoint(s), toast = "병원 정보를 삭제했어요")
}

/** The report screen's choices. */
object ReportOps {
    fun period(s: AppState, p: ReportPeriod) = s.copy(report = s.report.copy(period = p))
    fun toggle(s: AppState, part: ReportPart): AppState {
        val inc = s.report.include
        return s.copy(report = s.report.copy(include = if (part in inc) inc - part else inc + part))
    }

    fun busy(s: AppState, busy: Boolean) = s.copy(report = s.report.copy(busy = busy, ready = if (busy) null else s.report.ready))

    fun done(s: AppState, file: ReportFile?): AppState =
        if (file != null) s.copy(report = s.report.copy(busy = false, ready = file)) else RecordsReducer.toast(s.copy(report = s.report.copy(busy = false, ready = null)), "리포트를 만들지 못했어요 · 다시 시도해 주세요")

    fun shared(s: AppState) = s.copy(report = s.report.copy(ready = null))
}
