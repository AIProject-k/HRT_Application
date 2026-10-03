package com.hormonelog.app.feature.dashboard

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.Clinic
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.PrescriptionBasis
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.Telehealth
import com.hormonelog.core.domain.allowedRoutes
import com.hormonelog.core.domain.allowedUnits
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class DashboardTab { HOME, TIMELINE, FLOW, ME }

enum class DashboardSheet { NONE, DOSE, LAB }

enum class HormoneSeries { E2, TT }

enum class ChartRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

enum class TimelineFilter { ALL, DOSE, LAB }

enum class DoseTimeChoice { NOW, MINUS_30M, MINUS_2H, YESTERDAY, CUSTOM }

enum class LabTimeChoice { NOW, THIS_MORNING, YESTERDAY, CUSTOM, UNKNOWN }

enum class LabField { NONE, E2, TT }

/** Accepted dose range. Outside it the entry is refused, never quietly rewritten. */
const val DOSE_MIN = 0.5
const val DOSE_MAX = 500.0

/** Draft backing the 투약 기록 sheet. */
data class DoseDraft(
    /** Non-null when the sheet is editing an existing record rather than adding one. */
    val editingId: UUID? = null,
    val drug: Drug = Drug.ESTRADIOL_VALERATE,
    val route: Route = Route.IM_INJECTION,
    val amount: Double = 5.0,
    val unit: DoseUnit = DoseUnit.MG,
    val time: DoseTimeChoice = DoseTimeChoice.NOW,
    /** Chosen instant when [time] is [DoseTimeChoice.CUSTOM]; UTC epoch millis. */
    val customEpochMillis: Long? = null,
    /** Whether the dose was actually taken; a skipped one stays visible but off the curve. */
    val status: DoseStatus = DoseStatus.ADMINISTERED,
    val note: String = "",
    // ── repeat / regimen ──
    val repeat: Boolean = false,
    val repeatEveryDays: Int = 14,
    val repeatStartMillis: Long? = null,
    val repeatOngoing: Boolean = true,
    val repeatEndMillis: Long? = null,
) {
    /**
     * Why this amount cannot be saved, or null. Out-of-range input is reported rather
     * than clamped: silently turning 1000 into 500 would store a dose the user never
     * took while showing them a success message.
     */
    val amountError: String?
        get() = when {
            amount < DOSE_MIN -> "${plainNumber(DOSE_MIN)} 이상으로 입력해 주세요"
            amount > DOSE_MAX -> "${plainNumber(DOSE_MAX)} 이하로 입력해 주세요"
            else -> null
        }

    val canSave: Boolean get() = amountError == null

    companion object {
        fun of(e: DoseEvent) = DoseDraft(
            editingId = e.id,
            drug = e.drug,
            route = e.route,
            amount = e.amountEntered,
            unit = e.enteredUnit,
            time = DoseTimeChoice.CUSTOM,
            customEpochMillis = e.occurredAt.toEpochMilli(),
            status = e.status,
            note = e.note.orEmpty(),
        )
    }
}

/** Draft backing the 검사 결과 기록 sheet. */
data class LabDraft(
    /** Non-null when the sheet is editing an existing record rather than adding one. */
    val editingId: UUID? = null,
    val e2: String = "",
    val tt: String = "",
    val e2Unit: String = LabAnalyteValue.E2_CANONICAL_UNIT,
    val time: LabTimeChoice = LabTimeChoice.NOW,
    /** Chosen instant when [time] is [LabTimeChoice.CUSTOM]; UTC epoch millis. */
    val customEpochMillis: Long? = null,
    /**
     * Defaults to [Assay.UNKNOWN]: the app must not stamp a method the user never
     * reported onto a record it presents as a real measurement.
     */
    val method: Assay = Assay.UNKNOWN,
    val note: String = "",
    val focus: LabField = LabField.NONE,
) {
    val e2Error: String? get() = labValueError(e2)
    val ttError: String? get() = labValueError(tt)
    val canSave: Boolean
        get() = (e2.isNotBlank() || tt.isNotBlank()) && e2Error == null && ttError == null

    companion object {
        fun of(l: LabResult): LabDraft {
            val e2 = l.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }
            val tt = l.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE }
            return LabDraft(
                editingId = l.id,
                e2 = e2?.reportedValue?.let(::plainNumber).orEmpty(),
                tt = tt?.reportedValue?.let(::plainNumber).orEmpty(),
                e2Unit = e2?.reportedUnit ?: LabAnalyteValue.E2_CANONICAL_UNIT,
                time = if (l.collectedAt == null) LabTimeChoice.UNKNOWN else LabTimeChoice.CUSTOM,
                customEpochMillis = l.collectedAt?.toEpochMilli(),
                method = l.assay,
                note = l.note.orEmpty(),
            )
        }
    }
}

private fun labValueError(text: String): String? {
    if (text.isBlank()) return null
    val value = text.toDoubleOrNull()
    return when {
        value == null || !value.isFinite() -> "올바른 숫자를 입력해 주세요"
        value < 0 -> "0 이상의 값을 입력해 주세요"
        else -> null
    }
}

/** "412.0" -> "412" so an edited value reads back the way it was typed. */
private fun plainNumber(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

/** Draft backing the 병원 메모 add/edit form. */
data class ClinicDraft(
    val editingId: UUID? = null,
    val name: String = "",
    val region: String = "",
    val basis: PrescriptionBasis = PrescriptionBasis.UNKNOWN,
    val telehealth: Telehealth = Telehealth.UNKNOWN,
    val priceNote: String = "",
    val memo: String = "",
    val sourceUrl: String = "",
) {
    val canSave: Boolean get() = name.isNotBlank()

    companion object {
        fun of(c: Clinic) = ClinicDraft(c.id, c.name, c.region, c.prescriptionBasis, c.telehealth, c.priceNote, c.memo, c.sourceUrl)
    }
}

/**
 * Past dose records a bulk action wants to write, held back until the user has seen
 * what and how many. Saving a schedule and back-filling the months before it are two
 * different acts, and only the first is what "저장" asked for.
 */
data class PendingBackfill(
    val doses: List<DoseEvent>,
    val regimens: List<Regimen>,
    val title: String,
    val body: String,
)

/**
 * The record lists as they were immediately before a destructive or additive change,
 * so one tap can put them back. Only the three record collections are captured — UI
 * position is not something the user asked to undo.
 */
data class UndoSnapshot(
    val doses: List<DoseEvent>,
    val labs: List<LabResult>,
    val regimens: List<Regimen>,
)

/**
 * Whole-app UI state. Held in the Activity and mutated only through [DashboardReducer].
 * Starts empty — there is no seeded demo data (the design's empty states are the
 * genuine first-run screens).
 */
data class DashboardState(
    val tab: DashboardTab = DashboardTab.HOME,
    val sheet: DashboardSheet = DashboardSheet.NONE,
    val series: HormoneSeries = HormoneSeries.E2,
    val range: ChartRange = ChartRange.MONTH,
    val timelineFilter: TimelineFilter = TimelineFilter.ALL,
    /** 0f..1f position of the chart scrub cursor, or null when not scrubbing. */
    val scrubFraction: Float? = null,
    val toast: String? = null,
    /**
     * Persistent banner for a storage problem the user has to know about — unlike
     * [toast] it does not time out, because losing this message could mean losing
     * the chance to recover the records.
     */
    val storageWarning: String? = null,
    /** Set alongside [toast] when the action it reports can be reversed. */
    val undo: UndoSnapshot? = null,
    /**
     * An already-recorded dose close enough to the one being saved that it is probably
     * a double entry. Saving waits for the user to confirm.
     */
    val duplicateDose: DoseEvent? = null,
    /** Bulk past-dose insert waiting for confirmation. */
    val pendingBackfill: PendingBackfill? = null,
    val newIds: Set<String> = emptySet(),
    val doses: List<DoseEvent> = emptyList(),
    val labs: List<LabResult> = emptyList(),
    val regimens: List<Regimen> = emptyList(),
    val clinics: List<Clinic> = emptyList(),
    val doseDraft: DoseDraft = DoseDraft(),
    val labDraft: LabDraft = LabDraft(),
    /** 병원 메모 list overlay open. */
    val clinicsOpen: Boolean = false,
    /** Non-null = the add/edit form is showing (over the list). */
    val clinicDraft: ClinicDraft? = null,
)

/** Pure transitions. [now] is injected so time resolution stays deterministic. */
object DashboardReducer {

    fun switchTab(s: DashboardState, tab: DashboardTab): DashboardState =
        s.copy(tab = tab, scrubFraction = null)

    fun openSheet(s: DashboardState, sheet: DashboardSheet): DashboardState =
        s.copy(sheet = sheet)

    /**
     * Drafts survive so a half-typed new record is not lost, but an abandoned *edit*
     * is dropped — otherwise the next "기록하기" would silently overwrite that record.
     */
    fun closeSheet(s: DashboardState): DashboardState = s.copy(
        sheet = DashboardSheet.NONE,
        duplicateDose = null,
        doseDraft = if (s.doseDraft.editingId != null) DoseDraft() else s.doseDraft,
        labDraft = if (s.labDraft.editingId != null) LabDraft() else s.labDraft.copy(focus = LabField.NONE),
    )

    fun setSeries(s: DashboardState, series: HormoneSeries): DashboardState =
        s.copy(series = series, scrubFraction = null)

    fun setRange(s: DashboardState, range: ChartRange): DashboardState =
        s.copy(range = range, scrubFraction = null)

    fun setTimelineFilter(s: DashboardState, filter: TimelineFilter): DashboardState =
        s.copy(timelineFilter = filter)

    fun setScrub(s: DashboardState, fraction: Float?): DashboardState =
        s.copy(scrubFraction = fraction?.coerceIn(0f, 1f))

    fun dismissToast(s: DashboardState): DashboardState = s.copy(toast = null, undo = null)

    fun dismissStorageWarning(s: DashboardState): DashboardState = s.copy(storageWarning = null)

    // ── 병원 메모 ─────────────────────────────────────────────
    fun openClinics(s: DashboardState): DashboardState = s.copy(clinicsOpen = true)

    fun closeClinics(s: DashboardState): DashboardState =
        s.copy(clinicsOpen = false, clinicDraft = null)

    fun newClinic(s: DashboardState): DashboardState = s.copy(clinicDraft = ClinicDraft())

    fun editClinic(s: DashboardState, id: java.util.UUID): DashboardState =
        s.clinics.firstOrNull { it.id == id }?.let { s.copy(clinicDraft = ClinicDraft.of(it)) } ?: s

    fun editClinicDraft(s: DashboardState, block: (ClinicDraft) -> ClinicDraft): DashboardState =
        s.clinicDraft?.let { s.copy(clinicDraft = block(it)) } ?: s

    fun cancelClinicDraft(s: DashboardState): DashboardState = s.copy(clinicDraft = null)

    fun saveClinic(s: DashboardState): DashboardState {
        val d = s.clinicDraft ?: return s
        if (!d.canSave) return s
        val clinic = Clinic(
            id = d.editingId ?: java.util.UUID.randomUUID(),
            name = d.name.trim(),
            region = d.region.trim(),
            prescriptionBasis = d.basis,
            telehealth = d.telehealth,
            priceNote = d.priceNote.trim(),
            memo = d.memo.trim(),
            sourceUrl = d.sourceUrl.trim(),
        )
        val list = if (d.editingId != null) {
            s.clinics.map { if (it.id == clinic.id) clinic else it }
        } else {
            s.clinics + clinic
        }
        return s.copy(clinics = list.sortedBy { it.name }, clinicDraft = null)
    }

    fun deleteClinic(s: DashboardState, id: java.util.UUID): DashboardState =
        s.copy(clinics = s.clinics.filterNot { it.id == id }, clinicDraft = null)

    // ── undo ──────────────────────────────────────────────────
    /** The record lists as they stand now, to be restored by [undoLast]. */
    private fun snapshot(s: DashboardState) = UndoSnapshot(s.doses, s.labs, s.regimens)

    /** Put the records back exactly as they were before the last reversible action. */
    fun undoLast(s: DashboardState): DashboardState {
        val u = s.undo ?: return s
        return s.copy(
            doses = u.doses,
            labs = u.labs,
            regimens = u.regimens,
            undo = null,
            toast = "되돌렸어요",
            newIds = emptySet(),
        )
    }

    // ── record deletion ───────────────────────────────────────
    fun deleteDose(s: DashboardState, id: UUID): DashboardState =
        s.copy(doses = s.doses.filterNot { it.id == id }, toast = "투약 기록 1건 삭제됨", undo = snapshot(s))

    fun deleteLab(s: DashboardState, id: UUID): DashboardState =
        s.copy(labs = s.labs.filterNot { it.id == id }, toast = "검사 결과 1건 삭제됨", undo = snapshot(s))

    /** Remove the repeating schedule only; the events it already generated stay. */
    fun deleteRegimen(s: DashboardState, id: UUID): DashboardState =
        s.copy(regimens = s.regimens.filterNot { it.id == id }, toast = "반복 일정 삭제됨 · 기록은 그대로예요", undo = snapshot(s))

    fun clearDoses(s: DashboardState): DashboardState =
        s.copy(doses = emptyList(), toast = "투약 기록 전체 삭제됨", undo = snapshot(s))

    fun clearLabs(s: DashboardState): DashboardState =
        s.copy(labs = emptyList(), toast = "검사 결과 전체 삭제됨", undo = snapshot(s))

    fun clearAllRecords(s: DashboardState): DashboardState =
        s.copy(
            doses = emptyList(), labs = emptyList(), regimens = emptyList(),
            toast = "투약·검사·반복 일정 전체 삭제됨", undo = snapshot(s),
        )

    // ── dose sheet ────────────────────────────────────────────
    fun editDose(s: DashboardState, block: (DoseDraft) -> DoseDraft): DashboardState =
        s.copy(doseDraft = block(s.doseDraft))

    /** Load an existing dose into the sheet so saving replaces it instead of adding. */
    fun beginEditDose(s: DashboardState, id: UUID): DashboardState =
        s.doses.firstOrNull { it.id == id }
            ?.let { s.copy(doseDraft = DoseDraft.of(it), sheet = DashboardSheet.DOSE) }
            ?: s

    /**
     * Pick the drug, keeping route and unit legal for it. Snapping here is what stops
     * a combination like a patch given by injection from ever reaching a record.
     */
    fun setDoseDrug(s: DashboardState, drug: Drug): DashboardState {
        val route = s.doseDraft.route.takeIf { it in drug.allowedRoutes } ?: drug.allowedRoutes.first()
        return setDoseRoute(s.copy(doseDraft = s.doseDraft.copy(drug = drug)), route)
    }

    fun setDoseRoute(s: DashboardState, route: Route): DashboardState {
        val unit = s.doseDraft.unit.takeIf { it in route.allowedUnits } ?: route.allowedUnits.first()
        return s.copy(doseDraft = s.doseDraft.copy(route = route, unit = unit))
    }

    fun setDoseStatus(s: DashboardState, status: DoseStatus): DashboardState =
        editDose(s) { it.copy(status = status) }

    fun cancelDuplicate(s: DashboardState): DashboardState = s.copy(duplicateDose = null)

    fun stepDose(s: DashboardState, up: Boolean): DashboardState {
        val a = s.doseDraft.amount
        val step = when {
            a < 10.0 -> 0.5
            a < 50.0 -> 1.0
            else -> 5.0
        }
        val next = (if (up) a + step else a - step).coerceIn(DOSE_MIN, DOSE_MAX)
        return editDose(s) { it.copy(amount = round2(next)) }
    }

    /**
     * Set the amount from typed text. Unparseable input is ignored, but a parseable
     * out-of-range value is kept as typed so [DoseDraft.amountError] can explain it.
     */
    fun setDoseAmount(s: DashboardState, text: String): DashboardState {
        val v = text.toDoubleOrNull() ?: return s
        return editDose(s) { it.copy(amount = round2(v)) }
    }

    /**
     * Save the dose draft, replacing the record it was loaded from when editing.
     * Unless [force], a same-drug record within [DUPLICATE_WINDOW_SECONDS] parks the
     * save and asks first — a re-tap is far more likely than two real doses that close.
     */
    fun saveDose(s: DashboardState, now: Instant, force: Boolean = false): DashboardState {
        val d = s.doseDraft
        if (!d.canSave) return s
        val zone = ZoneId.systemDefault()
        val at = resolveDoseTime(d.time, now, d.customEpochMillis)
        if (!force) {
            val clash = s.doses.firstOrNull {
                it.id != d.editingId && it.drug == d.drug &&
                    Math.abs(it.occurredAt.epochSecond - at.epochSecond) < DUPLICATE_WINDOW_SECONDS
            }
            if (clash != null) return s.copy(duplicateDose = clash)
        }
        val previous = d.editingId?.let { eid -> s.doses.firstOrNull { it.id == eid } }
        val id = previous?.id ?: UUID.randomUUID()
        val event = DoseEvent(
            id = id,
            occurredAt = at,
            sourceZoneId = zone.id,
            drug = d.drug,
            route = d.route,
            amountEntered = d.amount,
            enteredUnit = d.unit,
            normalizedMilligrams = DoseEvent.normalizeMilligrams(d.amount, d.unit),
            status = d.status,
            note = d.note.ifBlank { null },
            revision = (previous?.revision ?: 0) + 1,
        )
        val doses = if (previous != null) s.doses.map { if (it.id == id) event else it } else s.doses + event
        return s.copy(
            doses = doses.sortedBy { it.occurredAt },
            sheet = DashboardSheet.NONE,
            doseDraft = d.copy(editingId = null),
            duplicateDose = null,
            undo = snapshot(s),
            toast = when {
                previous != null -> "투약 기록 수정됨"
                d.status == DoseStatus.SKIPPED -> "놓친 투약으로 기록됨 · 예상 곡선에는 넣지 않아요"
                else -> "투약 기록됨 · 타임라인과 예상 흐름에 반영했어요"
            },
            newIds = setOf(id.toString()),
        )
    }

    /** Save the dose draft as a repeating [Regimen]; the elapsed portion becomes real events. */
    fun saveRegimen(s: DashboardState, now: Instant): DashboardState {
        val d = s.doseDraft
        val start = d.repeatStartMillis?.let(Instant::ofEpochMilli) ?: now.minus(30, ChronoUnit.DAYS)
        val end = if (d.repeatOngoing) null else d.repeatEndMillis?.let(Instant::ofEpochMilli)
        val regimen = Regimen(
            id = UUID.randomUUID(),
            drug = d.drug,
            route = d.route,
            amountEntered = d.amount,
            enteredUnit = d.unit,
            everyDays = d.repeatEveryDays.coerceAtLeast(1),
            startAt = start,
            endAt = end,
            active = d.repeatOngoing || (end != null && end.isAfter(now)),
        )
        val generated = pastDosesFor(s, listOf(regimen), now)
        val saved = s.copy(
            regimens = s.regimens + regimen,
            sheet = DashboardSheet.NONE,
            undo = snapshot(s),
            toast = "반복 일정 저장됨",
        )
        if (generated.isEmpty()) return saved
        return saved.copy(
            pendingBackfill = PendingBackfill(
                doses = generated,
                regimens = emptyList(),
                title = "지난 투약도 기록할까요?",
                body = "${describeRange(generated)} 사이의 ${generated.size}건이에요. " +
                    "실제로 투약한 것만 기록해 주세요. 나중에 개별 삭제할 수 있어요.",
            ),
        )
    }

    /** Occurrences a schedule would have produced before [now], minus days already recorded. */
    private fun pastDosesFor(s: DashboardState, regimens: List<Regimen>, now: Instant): List<DoseEvent> =
        regimens
            .flatMap { Regimen.expand(it, now) }
            .filterNot { g -> s.doses.any { sameDayDrug(it, g) } }
            .sortedBy { it.occurredAt }

    private fun describeRange(doses: List<DoseEvent>): String {
        val zone = ZoneId.systemDefault()
        fun d(i: Instant) = i.atZone(zone).let { "${it.monthValue}월 ${it.dayOfMonth}일" }
        return "${d(doses.first().occurredAt)} ~ ${d(doses.last().occurredAt)}"
    }

    fun confirmBackfill(s: DashboardState): DashboardState {
        val p = s.pendingBackfill ?: return s
        return s.copy(
            doses = (s.doses + p.doses).sortedBy { it.occurredAt },
            regimens = s.regimens + p.regimens,
            pendingBackfill = null,
            undo = snapshot(s),
            toast = "지난 투약 ${p.doses.size}건을 기록에 추가했어요",
            newIds = p.doses.map { it.id.toString() }.toSet(),
        )
    }

    fun cancelBackfill(s: DashboardState): DashboardState =
        s.copy(pendingBackfill = null, toast = "지난 투약은 기록하지 않았어요")

    /**
     * Merge CSV-imported records. Identity is the exact instant plus what was taken,
     * not the calendar day: a drug taken twice in one day is two real records, and
     * a day-level rule would silently drop the second one on every restore. Labs are
     * matched the same way instead of being appended blindly.
     */
    fun mergeImported(
        s: DashboardState,
        doses: List<DoseEvent>,
        labs: List<LabResult>,
        skipped: Int,
    ): DashboardState {
        val newDoses = doses.filterNot { g -> s.doses.any { sameDoseEvent(it, g) } }
        val newLabs = labs.filterNot { g -> s.labs.any { sameLabResult(it, g) } }
        val duplicates = (doses.size - newDoses.size) + (labs.size - newLabs.size)
        val addedNote = buildString {
            append("CSV 불러옴 · 투약 ${newDoses.size}건 · 검사 ${newLabs.size}건")
            if (duplicates > 0) append(" · 이미 있는 기록 $duplicates")
            if (skipped > 0) append(" · 건너뜀 $skipped")
        }
        return s.copy(
            doses = (s.doses + newDoses).sortedBy { it.occurredAt },
            labs = (s.labs + newLabs).sortedBy { it.collectedAt ?: Instant.MIN },
            undo = snapshot(s),
            toast = addedNote,
            newIds = (newDoses.map { it.id.toString() } + newLabs.map { it.id.toString() }).toSet(),
        )
    }

    private fun sameDoseEvent(a: DoseEvent, b: DoseEvent): Boolean =
        a.drug == b.drug && a.route == b.route &&
            a.occurredAt == b.occurredAt &&
            a.amountEntered == b.amountEntered && a.enteredUnit == b.enteredUnit

    private fun sameLabResult(a: LabResult, b: LabResult): Boolean =
        a.collectedAt == b.collectedAt &&
            a.analytes.map { it.analyte to it.reportedValue }.toSet() ==
            b.analytes.map { it.analyte to it.reportedValue }.toSet()

    /**
     * Example data: EV IM 10mg/2주 + CPA 경구 25mg/일 for the last 60 days.
     *
     * It is written as ordinary records, indistinguishable from real ones afterwards,
     * so it is offered only into an app with nothing in it and only after the user has
     * confirmed. Mixing invented doses into real history would quietly corrupt every
     * curve drawn from then on.
     */
    fun loadSampleRegimen(s: DashboardState, now: Instant): DashboardState {
        if (s.doses.isNotEmpty() || s.labs.isNotEmpty() || s.regimens.isNotEmpty()) {
            return s.copy(toast = "기록이 있을 때는 예시를 넣지 않아요 · 예시와 실제 기록이 섞이면 곡선을 믿을 수 없어요")
        }
        val start = now.minus(60, ChronoUnit.DAYS)
        val sample = listOf(
            Regimen(UUID.randomUUID(), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 10.0, DoseUnit.MG, 14, start, null),
            Regimen(UUID.randomUUID(), Drug.CYPROTERONE, Route.ORAL, 25.0, DoseUnit.MG, 1, start, null),
        )
        val generated = pastDosesFor(s, sample, now)
        if (generated.isEmpty()) return s
        return s.copy(
            pendingBackfill = PendingBackfill(
                doses = generated,
                regimens = sample,
                title = "예시 데이터를 넣을까요?",
                body = "EV 2주 + CPA 매일, ${describeRange(generated)} 사이의 ${generated.size}건이 " +
                    "실제 기록과 똑같이 저장돼요. 둘러본 뒤에는 내 정보 → 전체 초기화로 지워 주세요.",
            ),
        )
    }

    /** Day-level match, used only when expanding a repeating schedule. */
    private fun sameDayDrug(a: DoseEvent, b: DoseEvent): Boolean =
        a.drug == b.drug &&
            a.occurredAt.epochSecond / 86_400L == b.occurredAt.epochSecond / 86_400L

    // ── lab sheet ─────────────────────────────────────────────
    fun editLab(s: DashboardState, block: (LabDraft) -> LabDraft): DashboardState =
        s.copy(labDraft = block(s.labDraft))

    /** Load an existing lab result into the sheet so saving replaces it. */
    fun beginEditLab(s: DashboardState, id: UUID): DashboardState =
        s.labs.firstOrNull { it.id == id }
            ?.let { s.copy(labDraft = LabDraft.of(it), sheet = DashboardSheet.LAB) }
            ?: s

    fun focusLabField(s: DashboardState, field: LabField): DashboardState =
        editLab(s) { it.copy(focus = field) }

    fun pressKey(s: DashboardState, key: String): DashboardState {
        val field = s.labDraft.focus
        if (field == LabField.NONE) return s
        val current = if (field == LabField.E2) s.labDraft.e2 else s.labDraft.tt
        val next = when {
            key == "⌫" -> current.dropLast(1)
            key == "." && current.contains('.') -> current
            current.length >= 6 -> current
            key == "." && current.isEmpty() -> "0."
            else -> current + key
        }
        return editLab(s) { if (field == LabField.E2) it.copy(e2 = next) else it.copy(tt = next) }
    }

    fun saveLab(s: DashboardState, now: Instant): DashboardState {
        val d = s.labDraft
        if (!d.canSave) return s
        val zone = ZoneId.systemDefault()
        val analytes = buildList {
            d.e2.toDoubleOrNull()?.let {
                add(LabAnalyteValue(Analyte.ESTRADIOL, it, d.e2Unit, LabAnalyteValue.canonical(Analyte.ESTRADIOL, it, d.e2Unit)))
            }
            d.tt.toDoubleOrNull()?.let {
                add(
                    LabAnalyteValue(
                        Analyte.TOTAL_TESTOSTERONE, it, LabAnalyteValue.TT_CANONICAL_UNIT,
                        LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, it, LabAnalyteValue.TT_CANONICAL_UNIT),
                    ),
                )
            }
        }
        if (analytes.isEmpty()) return s
        val previous = d.editingId?.let { eid -> s.labs.firstOrNull { it.id == eid } }
        val id = previous?.id ?: UUID.randomUUID()
        val collectedAt = resolveLabTime(d.time, now, zone, d.customEpochMillis)
        val lab = LabResult(
            id = id,
            collectedAt = collectedAt,
            sourceZoneId = zone.id,
            assay = d.method,
            analytes = analytes,
            note = d.note.ifBlank { null },
        )
        val labs = if (previous != null) s.labs.map { if (it.id == id) lab else it } else s.labs + lab
        return s.copy(
            labs = labs.sortedBy { it.collectedAt ?: Instant.MIN },
            sheet = DashboardSheet.NONE,
            // Keep the unit and method the user just confirmed; drop everything else.
            labDraft = LabDraft(e2Unit = d.e2Unit, method = d.method),
            undo = snapshot(s),
            toast = when {
                previous != null -> "검사 결과 수정됨"
                collectedAt == null -> "검사값 저장됨 · 채혈 시각을 모르면 곡선 보정에는 쓰지 않아요"
                else -> "실제 검사값으로 저장됨 · 그래프에 노란 마름모로 표시돼요"
            },
            newIds = setOf(id.toString()),
        )
    }

    // ── helpers ───────────────────────────────────────────────
    /** Two same-drug records inside this window are treated as a possible double entry. */
    const val DUPLICATE_WINDOW_SECONDS = 6L * 3600L
    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}

fun resolveDoseTime(choice: DoseTimeChoice, now: Instant, customEpochMillis: Long? = null): Instant = when (choice) {
    DoseTimeChoice.NOW -> now
    DoseTimeChoice.MINUS_30M -> now.minusSeconds(1800)
    DoseTimeChoice.MINUS_2H -> now.minusSeconds(7200)
    DoseTimeChoice.YESTERDAY -> now.minus(1, ChronoUnit.DAYS)
    DoseTimeChoice.CUSTOM -> customEpochMillis?.let(Instant::ofEpochMilli) ?: now
}

/**
 * Null for [LabTimeChoice.UNKNOWN]: the result is still worth keeping, but without a
 * collection time it cannot be placed against a curve, and [LabEligibility] excludes
 * it from calibration rather than guessing.
 */
fun resolveLabTime(
    choice: LabTimeChoice,
    now: Instant,
    zone: ZoneId,
    customEpochMillis: Long? = null,
): Instant? = when (choice) {
    LabTimeChoice.NOW -> now
    LabTimeChoice.THIS_MORNING ->
        now.atZone(zone).toLocalDate().atTime(9, 30).atZone(zone).toInstant()
    LabTimeChoice.YESTERDAY -> now.minus(1, ChronoUnit.DAYS)
    LabTimeChoice.CUSTOM -> customEpochMillis?.let(Instant::ofEpochMilli) ?: now
    LabTimeChoice.UNKNOWN -> null
}
