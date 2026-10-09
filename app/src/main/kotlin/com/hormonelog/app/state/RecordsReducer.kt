package com.hormonelog.app.state

import com.hormonelog.app.analysis.Analysis
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.TUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.StockItem
import com.hormonelog.core.domain.allowedRoutes
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.modelengine.CalibrationResult
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** The drugs a route can carry, in the order the design lists them. */
fun drugsFor(route: Route): List<Drug> {
    val order = listOf(
        Drug.ESTRADIOL_VALERATE, Drug.ESTRADIOL_CYPIONATE, Drug.ESTRADIOL_TABLET, Drug.CYPROTERONE,
        Drug.SPIRONOLACTONE, Drug.ESTRADIOL_PATCH, Drug.ESTRADIOL_GEL,
    )
    return order.filter { route in it.allowedRoutes }
}

/**
 * Pure transitions for everything the user records: doses, plans, labs, deletions, undo and
 * imports. Nothing here reads a clock — callers pass [now] and the [zone] — so the same
 * records and the same instant always give the same result.
 */
object RecordsReducer {

    /** Two same-drug records inside this window are treated as a possible double entry. */
    const val DUPLICATE_WINDOW_SECONDS = 6L * 3600L

    /** A draw this far ahead of now is a typo, not a plan. */
    private const val FUTURE_TOLERANCE_SECONDS = 60L

    // ── feedback ──────────────────────────────────────────────
    fun toast(s: AppState, message: String, undo: UndoSnapshot? = null): AppState = s.copy(toast = message, undo = undo)

    fun dismissToast(s: AppState): AppState = s.copy(toast = null, undo = null)

    /** The records as they stand now, to be restored by [undo]. */
    fun undoPoint(s: AppState) = UndoSnapshot(s.doses, s.labs, s.regimens, s.clinics, s.memos, s.journal, s.stock, s.nextVisitMillis, s.carried)

    private fun snapshot(s: AppState) = undoPoint(s)

    /** Put the records back exactly as they were before the last reversible action. */
    fun undo(s: AppState): AppState {
        val u = s.undo ?: return s
        return s.copy(
            doses = u.doses, labs = u.labs, regimens = u.regimens, clinics = u.clinics, memos = u.memos,
            journal = u.journal, stock = u.stock, nextVisitMillis = u.nextVisitMillis, carried = u.carried,
            undo = null, toast = "되돌렸어요",
        )
    }

    // ── deletion ──────────────────────────────────────────────
    fun deleteDose(s: AppState, id: UUID): AppState =
        s.copy(doses = s.doses.filterNot { it.id == id }, sheet = Sheet.None, toast = "기록을 삭제했어요", undo = snapshot(s))

    fun deleteLab(s: AppState, id: UUID): AppState =
        s.copy(labs = s.labs.filterNot { it.id == id }, sheet = Sheet.None, toast = "기록을 삭제했어요", undo = snapshot(s))

    /** Remove the plan only; the events it already generated stay. */
    fun deleteRegimen(s: AppState, id: UUID): AppState =
        s.copy(regimens = s.regimens.filterNot { it.id == id }, confirm = null, toast = "일정을 지웠어요 · 지난 기록은 그대로예요", undo = snapshot(s))

    /**
     * Everything the user entered goes — the records this build could not read and kept aside too, because
     * "all" means all. Settings stay. One tap on the toast brings it all back.
     */
    fun clearAll(s: AppState): AppState = s.copy(
        doses = emptyList(), labs = emptyList(), regimens = emptyList(), clinics = emptyList(), memos = emptyList(),
        journal = emptyList(), stock = emptyList(), nextVisitMillis = null, carried = emptyMap(), confirm = null,
        toast = "모든 기록을 삭제했어요", undo = snapshot(s),
    )

    /** One unit of the matching product is used up when a real dose is recorded. */
    fun useStock(stock: List<StockItem>, dose: DoseEvent): List<StockItem> =
        stock.map { if (it.matches(dose) && it.count > 0) it.copy(count = it.count - 1) else it }
    // ── dose sheet ────────────────────────────────────────────
    /**
     * Opens the sheet for a new record. It starts from what the person took last, so the
     * usual case is one tap — but with no history nothing is filled in, because a dose the
     * user never typed must not look like a suggestion.
     */
    fun openDose(s: AppState, repeat: Boolean = false): AppState {
        val last = s.doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen }.maxByOrNull { it.occurredAt }
        val base = DoseDraft(repeat = repeat)
        val draft = if (last != null) fillFrom(base.copy(drug = last.drug, route = last.route), s.doses) else base
        return s.copy(doseDraft = draft, sheet = Sheet.Dose)
    }

    /** Edit an existing dose in place. */
    fun editDose(s: AppState, id: UUID): AppState {
        val e = s.doses.firstOrNull { it.id == id } ?: return s
        val draft = DoseDraft(
            editingId = e.id,
            drug = e.drug,
            route = e.route,
            amountText = if (e.route == Route.PATCH) "" else plainNumber(e.amountEntered),
            patchStrength = e.patchMicrogramsPerDay ?: 50.0,
            patchCycle = e.patchCycle ?: com.hormonelog.core.domain.PatchCycle.TWICE_WEEKLY,
            time = DoseTime.PICK,
            pickedMillis = e.occurredAt.toEpochMilli(),
            site = e.site,
            status = e.status,
            note = e.note.orEmpty(),
        )
        return s.copy(doseDraft = draft, sheet = Sheet.Dose)
    }

    fun closeSheet(s: AppState): AppState = s.copy(
        sheet = Sheet.None,
        // Drafts survive so a half-typed new record is not lost, but an abandoned *edit* is
        // dropped — otherwise the next "기록하기" would silently overwrite that record.
        doseDraft = if (s.doseDraft.editingId != null) DoseDraft() else s.doseDraft,
        labDraft = if (s.labDraft.editingId != null) freshLabDraft(s) else s.labDraft,
    )

    fun editDraft(s: AppState, block: (DoseDraft) -> DoseDraft): AppState = s.copy(doseDraft = block(s.doseDraft))

    /** Fill the amount (and patch details) from the last record of this drug by this route. */
    private fun fillFrom(d: DoseDraft, doses: List<DoseEvent>): DoseDraft {
        val last = lastOf(doses, d.drug, d.route)
            ?: return d.copy(amountText = "", amountFilled = false)
        return d.copy(
            amountText = if (d.route == Route.PATCH) "" else plainNumber(last.amountEntered),
            amountFilled = d.route != Route.PATCH,
            patchStrength = last.patchMicrogramsPerDay ?: d.patchStrength,
            patchCycle = last.patchCycle ?: d.patchCycle,
        )
    }

    fun setRoute(s: AppState, route: Route): AppState {
        val d = s.doseDraft
        val drug = if (d.drug in drugsFor(route)) d.drug else drugsFor(route).first()
        return s.copy(doseDraft = fillFrom(d.copy(route = route, drug = drug, site = d.site.takeIf { route.isInjectionRoute() }), s.doses))
    }

    fun setDrug(s: AppState, drug: Drug): AppState = s.copy(doseDraft = fillFrom(s.doseDraft.copy(drug = drug), s.doses))

    fun setAmountText(s: AppState, text: String): AppState {
        // Only digits and one dot can be a number; anything else is ignored rather than stored.
        val cleaned = text.filter { it.isDigit() || it == '.' }.take(7)
        return s.copy(doseDraft = s.doseDraft.copy(amountText = cleaned, amountFilled = false))
    }

    fun pickCombo(s: AppState, combo: Combo): AppState = s.copy(
        doseDraft = s.doseDraft.copy(
            drug = combo.drug,
            route = combo.route,
            amountText = if (combo.route == Route.PATCH) "" else plainNumber(combo.amount),
            amountFilled = false,
            patchStrength = if (combo.route == Route.PATCH) combo.amount else s.doseDraft.patchStrength,
            patchCycle = combo.patchCycle ?: s.doseDraft.patchCycle,
            repeat = false,
        ),
    )

    private fun Route.isInjectionRoute() = this == Route.IM_INJECTION || this == Route.SC_INJECTION

    /**
     * Saves the dose draft, replacing the record it was loaded from when editing. Unless
     * [force], a same-drug record within [DUPLICATE_WINDOW_SECONDS] parks the save and asks
     * first — a re-tap is far more likely than two real doses that close.
     */
    fun saveDose(s: AppState, now: Instant, zone: ZoneId, force: Boolean = false): AppState {
        val d = s.doseDraft
        if (d.repeat || !d.canSave) return s
        val at = resolveDoseTime(d, now)
        if (at.isAfter(now.plusSeconds(FUTURE_TOLERANCE_SECONDS))) return s
        if (!force) {
            val clash = s.doses.firstOrNull {
                it.id != d.editingId && it.drug == d.drug && abs(it.occurredAt.epochSecond - at.epochSecond) < DUPLICATE_WINDOW_SECONDS
            }
            if (clash != null) return s.copy(confirm = Confirm.DuplicateDose(clash))
        }
        val previous = d.editingId?.let { eid -> s.doses.firstOrNull { it.id == eid } }
        val id = previous?.id ?: UUID.randomUUID()
        val patch = d.isPatch
        val unit = if (patch) DoseUnit.UG_PER_DAY else DoseUnit.MG
        val amount = d.amount ?: return s
        val event = DoseEvent(
            id = id,
            occurredAt = at,
            sourceZoneId = zone.id,
            drug = d.drug,
            route = d.route,
            amountEntered = amount,
            enteredUnit = unit,
            normalizedMilligrams = DoseEvent.normalizeMilligrams(amount, unit),
            status = d.status,
            note = d.note.trim().ifBlank { null },
            revision = (previous?.revision ?: 0) + 1,
            source = previous?.source ?: RecordSource.MANUAL,
            site = d.site.takeIf { d.isInjection },
            patchCycle = d.patchCycle.takeIf { patch },
        )
        val doses = if (previous != null) s.doses.map { if (it.id == id) event else it } else s.doses + event
        return s.copy(
            doses = doses.sortedBy { it.occurredAt },
            stock = if (previous == null && d.status.wasTaken) useStock(s.stock, event) else s.stock,
            sheet = Sheet.None,
            confirm = null,
            // A one-off past entry must not become the default time of the next new record.
            doseDraft = fillFrom(DoseDraft(drug = d.drug, route = d.route), doses),
            undo = snapshot(s),
            toast = when {
                previous != null -> "투약 기록을 고쳤어요"
                d.status == DoseStatus.SKIPPED -> "놓친 투약으로 기록했어요 · 예상 곡선에는 넣지 않아요"
                else -> "${com.hormonelog.app.feature.common.doseSummary(d.drug, d.route, amount, unit)}를 기록했어요"
            },
        )
    }

    /** One tap: record the last-used estrogen combination as taken right now. */
    fun quickLog(s: AppState, now: Instant, zone: ZoneId): AppState {
        val last = s.doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen }.maxByOrNull { it.occurredAt } ?: return s
        val event = last.copy(
            id = UUID.randomUUID(), occurredAt = now, sourceZoneId = zone.id, status = DoseStatus.ADMINISTERED,
            note = null, revision = 1, source = RecordSource.MANUAL,
        )
        val clash = s.doses.firstOrNull { it.drug == event.drug && abs(it.occurredAt.epochSecond - now.epochSecond) < DUPLICATE_WINDOW_SECONDS }
        if (clash != null) return s.copy(confirm = Confirm.DuplicateDose(clash), doseDraft = fillFrom(DoseDraft(drug = last.drug, route = last.route), s.doses))
        return s.copy(
            doses = (s.doses + event).sortedBy { it.occurredAt },
            stock = useStock(s.stock, event),
            undo = snapshot(s),
            toast = "${com.hormonelog.app.feature.common.doseSummary(event.drug, event.route, event.amountEntered, event.enteredUnit)}를 기록했어요",
        )
    }

    /** The quick-log button's "again, but anyway" after the duplicate question. */
    fun confirmDuplicate(s: AppState, now: Instant, zone: ZoneId): AppState {
        val dup = (s.confirm as? Confirm.DuplicateDose) ?: return s
        val fromSheet = s.sheet == Sheet.Dose
        return if (fromSheet) {
            saveDose(s.copy(confirm = null), now, zone, force = true)
        } else {
            val base = s.copy(confirm = null)
            val last = base.doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen }.maxByOrNull { it.occurredAt } ?: dup.existing
            val event = last.copy(
                id = UUID.randomUUID(), occurredAt = now, sourceZoneId = zone.id, status = DoseStatus.ADMINISTERED,
                note = null, revision = 1, source = RecordSource.MANUAL,
            )
            base.copy(
                doses = (base.doses + event).sortedBy { it.occurredAt },
                stock = useStock(base.stock, event),
                undo = snapshot(base),
                toast = "${com.hormonelog.app.feature.common.doseSummary(event.drug, event.route, event.amountEntered, event.enteredUnit)}를 기록했어요",
            )
        }
    }

    // ── repeating plans ───────────────────────────────────────
    /** The plan the draft describes, anchored to its start day and time. */
    fun regimenFromDraft(d: DoseDraft, now: Instant, zone: ZoneId): Regimen? {
        if (!d.repeat || !d.canSave) return null
        val startDate = planStartDate(d, now, zone)
        val atTime = startDate.atTime(d.timeMinutes / 60, d.timeMinutes % 60).atZone(zone).toInstant()
        // A plan that starts today begins at its next occurrence, not at an hour that has already passed.
        val start = if (startDate == now.atZone(zone).toLocalDate()) maxOf(atTime, now) else atTime
        val startDay = start.atZone(zone).dayOfWeek
        val patch = d.isPatch
        val interval = if (patch) {
            if (d.patchCycle == com.hormonelog.core.domain.PatchCycle.WEEKLY) IntervalChoice.WEEKLY else IntervalChoice.TWICE_WEEKLY
        } else {
            d.interval
        }
        val (everyDays, weekdays) = when (interval) {
            IntervalChoice.DAILY -> 1 to 0
            IntervalChoice.TWICE_WEEKLY -> 3 to Regimen.maskOf(twiceWeeklyDays(startDay))
            IntervalChoice.WEEKLY -> 7 to Regimen.maskOf(setOf(startDay))
            IntervalChoice.BIWEEKLY -> 14 to 0
            IntervalChoice.CUSTOM -> (d.customDays.trim().toIntOrNull() ?: return null) to 0
        }
        val amount = d.amount ?: return null
        val unit = if (patch) DoseUnit.UG_PER_DAY else DoseUnit.MG
        return Regimen(
            id = UUID.randomUUID(),
            drug = d.drug,
            route = d.route,
            amountEntered = amount,
            enteredUnit = unit,
            everyDays = everyDays,
            startAt = start,
            endAt = null,
            weekdays = weekdays,
            timeMinutes = d.timeMinutes,
            patchCycle = d.patchCycle.takeIf { patch },
        )
    }

    /** Save the draft as a repeating plan; the elapsed part becomes events only after the user agrees. */
    fun saveRegimen(s: AppState, now: Instant, zone: ZoneId): AppState {
        val r = regimenFromDraft(s.doseDraft, now, zone) ?: return s
        val backfill = backfillFor(s, r, now, zone)
        val running = s.regimens.firstOrNull {
            it.isRunningAt(now) && it.drug == r.drug && it.route == r.route
        }
        if (running != null) {
            return s.copy(confirm = Confirm.ReplaceRegimen(running, r, backfill))
        }
        return applyNewRegimen(s.copy(sheet = Sheet.None), r, null, backfill)
    }

    /** Ends the running plan the day before the new one starts, then adds the new one. */
    fun confirmReplace(s: AppState): AppState {
        val c = (s.confirm as? Confirm.ReplaceRegimen) ?: return s
        return applyNewRegimen(if (s.screen == Screen.SCHEDULE_EDIT) ScheduleOps.leave(s.copy(sheet = Sheet.None, confirm = null)) else s.copy(sheet = Sheet.None, confirm = null), c.replacement, c.existing, c.backfill)
    }

    /** Adds [r], ending [replacing] the moment before it starts; asks about the past doses it would have produced. */
    fun applyNewRegimen(s: AppState, r: Regimen, replacing: Regimen?, backfill: PendingBackfill?): AppState {
        val end = r.startAt.minusSeconds(1)
        val regimens = s.regimens.map { if (replacing != null && it.id == replacing.id) it.copy(endAt = end, active = false) else it } + r
        val saved = s.copy(
            regimens = regimens,
            undo = snapshot(s),
            toast = if (replacing != null) "일정을 바꿨어요 · 지난 기록은 남아요" else "반복 일정을 만들었어요",
        )
        return if (backfill == null) saved else saved.copy(confirm = Confirm.Backfill(backfill), undo = null, toast = null)
    }

    /** What the plan would have produced before [now], minus days already recorded. */
    fun backfillFor(s: AppState, r: Regimen, now: Instant, zone: ZoneId): PendingBackfill? {
        val generated = Regimen.expand(r, now, zone)
            .filterNot { g -> s.doses.any { sameDayDrug(it, g, zone) } }
            .sortedBy { it.occurredAt }
        if (generated.isEmpty()) return null
        val first = generated.first().occurredAt.atZone(zone)
        val last = generated.last().occurredAt.atZone(zone)
        fun md(z: java.time.ZonedDateTime) = "${z.monthValue}월 ${z.dayOfMonth}일"
        return PendingBackfill(
            doses = generated,
            regimens = emptyList(),
            title = "지난 투약도 기록할까요?",
            body = "${md(first)} ~ ${md(last)} 사이의 ${generated.size}건이에요. 실제로 투약한 것만 기록해 주세요. 나중에 하나씩 고치거나 지울 수 있어요.",
        )
    }

    private fun sameDayDrug(a: DoseEvent, b: DoseEvent, zone: ZoneId): Boolean =
        a.drug == b.drug && a.route == b.route && a.occurredAt.atZone(zone).toLocalDate() == b.occurredAt.atZone(zone).toLocalDate()

    fun confirmBackfill(s: AppState): AppState {
        val p = (s.confirm as? Confirm.Backfill)?.pending ?: return s
        return s.copy(
            doses = (s.doses + p.doses).sortedBy { it.occurredAt },
            confirm = null,
            undo = snapshot(s),
            toast = "지난 투약 ${p.doses.size}건을 기록에 추가했어요",
        )
    }

    fun declineBackfill(s: AppState): AppState = s.copy(confirm = null, toast = "지난 투약은 기록하지 않았어요")

    /** Stop a plan today; what it already produced stays. */
    fun endRegimenToday(s: AppState, id: UUID, now: Instant): AppState = s.copy(
        regimens = s.regimens.map { if (it.id == id) it.copy(endAt = now, active = false) else it },
        undo = snapshot(s),
        toast = "오늘부로 종료했어요 · 지난 기록은 남아요",
    )

    // ── lab sheet ─────────────────────────────────────────────
    fun freshLabDraft(s: AppState) = LabDraft(e2Unit = s.settings.e2Unit, ttUnit = s.settings.tUnit)

    fun openLab(s: AppState): AppState = s.copy(labDraft = freshLabDraft(s).copy(assay = s.labDraft.assay), sheet = Sheet.Lab)

    /** The pre-HRT lab: the one already saved, or a new one marked as such whose draw time is not assumed. */
    fun openBaselineLab(s: AppState): AppState {
        val existing = s.labs.filter { it.isBaseline }.maxByOrNull { it.collectedAt ?: Instant.MIN }
        if (existing != null) return editLab(s, existing.id)
        return openLab(s).let { it.copy(labDraft = it.labDraft.copy(isBaseline = true, draw = DrawChoice.UNKNOWN)) }
    }

    fun editLab(s: AppState, id: UUID): AppState {
        val l = s.labs.firstOrNull { it.id == id } ?: return s
        return s.copy(labDraft = LabDraft.of(l, s.settings.e2Unit, s.settings.tUnit), sheet = Sheet.Lab)
    }

    fun editLabDraft(s: AppState, block: (LabDraft) -> LabDraft): AppState = s.copy(labDraft = block(s.labDraft))

    /** A lab value as typed: digits and one dot only, so nothing else can reach the record. */
    fun cleanNumber(text: String): String = text.filter { it.isDigit() || it == '.' }.take(8)

    /** Saves the lab draft; the result screen then compares it with what the model expected. */
    fun saveLab(s: AppState, now: Instant, zone: ZoneId): AppState {
        val d = s.labDraft
        if (!d.canSave) return s
        val drawAt = resolveDrawTime(d, now, zone)
        if (drawAt != null && drawAt.isAfter(now.plusSeconds(FUTURE_TOLERANCE_SECONDS))) return s
        val analytes = buildList {
            d.e2Value?.takeIf { it > 0.0 }?.let {
                add(LabAnalyteValue(Analyte.ESTRADIOL, it, d.e2Unit.label, LabAnalyteValue.canonical(Analyte.ESTRADIOL, it, d.e2Unit.label)))
            }
            d.ttValue?.takeIf { it > 0.0 }?.let {
                add(LabAnalyteValue(Analyte.TOTAL_TESTOSTERONE, it, d.ttUnit.label, LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, it, d.ttUnit.label)))
            }
        }
        if (analytes.isEmpty()) return s
        val previous = d.editingId?.let { eid -> s.labs.firstOrNull { it.id == eid } }
        val lab = LabResult(
            id = previous?.id ?: UUID.randomUUID(),
            collectedAt = drawAt,
            sourceZoneId = zone.id,
            assay = d.assay,
            analytes = analytes,
            note = d.note.trim().ifBlank { null },
            isBaseline = d.isBaseline,
            source = previous?.source ?: RecordSource.MANUAL,
        )
        val labs = (if (previous != null) s.labs.map { if (it.id == lab.id) lab else it } else s.labs + lab)
            .sortedBy { it.collectedAt ?: Instant.MIN }
        val next = s.copy(
            labs = labs,
            sheet = Sheet.None,
            // Keep the units and method the user just confirmed; drop everything else.
            labDraft = LabDraft(e2Unit = d.e2Unit, ttUnit = d.ttUnit, assay = d.assay),
            undo = snapshot(s),
            toast = null,
        )
        val summary = LabAnalyzer.summarize(next, lab, now, zone)
        return next.copy(labResult = summary, screen = Screen.LAB_RESULT, stack = if (s.screen == Screen.LAB_RESULT) s.stack else s.stack + s.screen)
    }

    // ── import ────────────────────────────────────────────────
    /**
     * Merge imported records. Identity is the exact instant plus what was taken, not the
     * calendar day: a drug taken twice in one day is two real records, and a day-level rule
     * would silently drop the second one on every restore.
     */
    fun mergeImported(s: AppState, doses: List<DoseEvent>, labs: List<LabResult>): Pair<AppState, Int> {
        val newDoses = doses.filterNot { g -> s.doses.any { sameDose(it, g) } }
        val newLabs = labs.filterNot { g -> s.labs.any { sameLab(it, g) } }
        val duplicates = (doses.size - newDoses.size) + (labs.size - newLabs.size)
        return s.copy(
            doses = (s.doses + newDoses).sortedBy { it.occurredAt },
            labs = (s.labs + newLabs).sortedBy { it.collectedAt ?: Instant.MIN },
            undo = snapshot(s),
            toast = "투약 ${newDoses.size}건 · 검사 ${newLabs.size}건을 불러왔어요",
        ) to duplicates
    }

    fun countNew(s: AppState, doses: List<DoseEvent>, labs: List<LabResult>): Int =
        doses.count { g -> s.doses.none { sameDose(it, g) } } + labs.count { g -> s.labs.none { sameLab(it, g) } }

    private fun sameDose(a: DoseEvent, b: DoseEvent): Boolean =
        a.drug == b.drug && a.route == b.route && a.occurredAt == b.occurredAt &&
            a.amountEntered == b.amountEntered && a.enteredUnit == b.enteredUnit

    private fun sameLab(a: LabResult, b: LabResult): Boolean =
        a.collectedAt == b.collectedAt &&
            a.analytes.map { it.analyte to it.reportedValue }.toSet() == b.analytes.map { it.analyte to it.reportedValue }.toSet()
}

/** What the result screen needs after a lab is saved: expectation, difference, cycle position, calibration. */
object LabAnalyzer {

    fun summarize(s: AppState, lab: LabResult, now: Instant, zone: ZoneId): LabResultSummary {
        val at = lab.collectedAt
        val status = s.settings.gonadalStatus
        // "예상" is what the model said just before this lab: calibrated by the other labs, not by this one.
        val prior = Analysis.calibrate(s.doses, s.labs.filter { it.id != lab.id }, status)
        val withThis = Analysis.calibrate(s.doses, s.labs, status)
        val last = at?.let { Analysis.lastEstrogenBefore(s.doses, it) }
        val hours = if (at != null && last != null) (at.toEpochMilli() - last.occurredAt.toEpochMilli()) / 3_600_000.0 else null
        val beforeFirst = at != null && last == null &&
            s.doses.any { it.status.wasTaken && !it.drug.isAntiandrogen && it.occurredAt.isAfter(at) }

        val e2 = lab.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }?.let { v ->
            val pg = Analysis.canonical(v)
            val expected = if (at != null && pg != null) Analysis.e2At(s.doses, s.regimens, prior, at, now, zone) else null
            val diff = if (pg != null && expected != null && expected.median > 0) (((pg - expected.median) / expected.median) * 100).roundToInt() else null
            E2Outcome(
                reported = v.reportedValue,
                unit = E2Unit.entries.firstOrNull { it.label.equals(v.reportedUnit, true) } ?: E2Unit.PG_ML,
                measuredPg = pg ?: v.reportedValue,
                expected = expected,
                diffPercent = diff,
                withinRange = if (pg != null && expected != null) pg >= expected.lower && pg <= expected.upper else null,
                spot = if (at != null) Analysis.cyclePosition(s.doses, s.regimens, prior, at, zone)?.spot else null,
                calibration = withThis.labs[lab.id]?.e2,
            )
        }
        val tt = lab.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE }?.let { v ->
            val ng = Analysis.canonical(v)
            val expected = if (at != null && ng != null) Analysis.ttAt(s.doses, s.labs.filter { it.id != lab.id }, status, prior, at) else null
            val diff = if (ng != null && expected != null && expected.median > 0) (((ng - expected.median) / expected.median) * 100).roundToInt() else null
            TtOutcome(
                reported = v.reportedValue,
                unit = TUnit.entries.firstOrNull { it.label.equals(v.reportedUnit, true) } ?: TUnit.NG_DL,
                measuredNgDl = ng ?: v.reportedValue,
                expected = expected,
                diffPercent = diff,
                withinRange = if (ng != null && expected != null) ng >= expected.lower && ng <= expected.upper else null,
                calibration = withThis.labs[lab.id]?.tt,
            )
        }
        // "N번째 검사": its place among the labs that shaped the same route group's curve.
        val used = e2?.calibration?.takeIf { it.used }
        val ordinal = used?.group?.let { group ->
            s.labs.filter { l -> withThis.labs[l.id]?.e2?.let { it.used && it.group == group } == true }
                .sortedBy { it.collectedAt }
                .indexOfFirst { it.id == lab.id }.takeIf { it >= 0 }?.plus(1)
        }
        return LabResultSummary(lab.id, at, e2, tt, hours, beforeFirst, ordinal)
    }
}