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

enum class LabTimeChoice { NOW, THIS_MORNING, YESTERDAY }

enum class LabField { NONE, E2, TT }

/** Draft backing the 투약 기록 sheet. */
data class DoseDraft(
    val drug: Drug = Drug.ESTRADIOL_VALERATE,
    val route: Route = Route.IM_INJECTION,
    val amount: Double = 5.0,
    val unit: DoseUnit = DoseUnit.MG,
    val time: DoseTimeChoice = DoseTimeChoice.NOW,
    /** Chosen instant when [time] is [DoseTimeChoice.CUSTOM]; UTC epoch millis. */
    val customEpochMillis: Long? = null,
    val note: String = "",
    // ── repeat / regimen ──
    val repeat: Boolean = false,
    val repeatEveryDays: Int = 14,
    val repeatStartMillis: Long? = null,
    val repeatOngoing: Boolean = true,
    val repeatEndMillis: Long? = null,
)

/** Draft backing the 검사 결과 기록 sheet. */
data class LabDraft(
    val e2: String = "",
    val tt: String = "",
    val e2Unit: String = LabAnalyteValue.E2_CANONICAL_UNIT,
    val time: LabTimeChoice = LabTimeChoice.NOW,
    val method: Assay = Assay.LC_MS_MS,
    val note: String = "",
    val focus: LabField = LabField.NONE,
) {
    val canSave: Boolean get() = e2.isNotBlank() || tt.isNotBlank()
}

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

    fun closeSheet(s: DashboardState): DashboardState =
        s.copy(sheet = DashboardSheet.NONE, labDraft = s.labDraft.copy(focus = LabField.NONE))

    fun setSeries(s: DashboardState, series: HormoneSeries): DashboardState =
        s.copy(series = series, scrubFraction = null)

    fun setRange(s: DashboardState, range: ChartRange): DashboardState =
        s.copy(range = range, scrubFraction = null)

    fun setTimelineFilter(s: DashboardState, filter: TimelineFilter): DashboardState =
        s.copy(timelineFilter = filter)

    fun setScrub(s: DashboardState, fraction: Float?): DashboardState =
        s.copy(scrubFraction = fraction?.coerceIn(0f, 1f))

    fun dismissToast(s: DashboardState): DashboardState = s.copy(toast = null)

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

    // ── record deletion ───────────────────────────────────────
    fun deleteDose(s: DashboardState, id: UUID): DashboardState =
        s.copy(doses = s.doses.filterNot { it.id == id }, toast = "투약 기록 1건 삭제됨")

    fun deleteLab(s: DashboardState, id: UUID): DashboardState =
        s.copy(labs = s.labs.filterNot { it.id == id }, toast = "검사 결과 1건 삭제됨")

    /** Remove the repeating schedule only; the events it already generated stay. */
    fun deleteRegimen(s: DashboardState, id: UUID): DashboardState =
        s.copy(regimens = s.regimens.filterNot { it.id == id }, toast = "반복 일정 삭제됨 · 기록은 그대로예요")

    fun clearDoses(s: DashboardState): DashboardState =
        s.copy(doses = emptyList(), toast = "투약 기록 전체 삭제됨")

    fun clearLabs(s: DashboardState): DashboardState =
        s.copy(labs = emptyList(), toast = "검사 결과 전체 삭제됨")

    fun clearAllRecords(s: DashboardState): DashboardState =
        s.copy(doses = emptyList(), labs = emptyList(), regimens = emptyList(), toast = "투약·검사·반복 일정 전체 삭제됨")

    // ── dose sheet ────────────────────────────────────────────
    fun editDose(s: DashboardState, block: (DoseDraft) -> DoseDraft): DashboardState =
        s.copy(doseDraft = block(s.doseDraft))

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

    /** Set the amount from typed text; ignores unparseable input, clamps the rest. */
    fun setDoseAmount(s: DashboardState, text: String): DashboardState {
        val v = text.toDoubleOrNull() ?: return s
        return editDose(s) { it.copy(amount = round2(v.coerceIn(DOSE_MIN, DOSE_MAX))) }
    }

    fun saveDose(s: DashboardState, now: Instant): DashboardState {
        val d = s.doseDraft
        val zone = ZoneId.systemDefault()
        val id = UUID.randomUUID()
        val event = DoseEvent(
            id = id,
            occurredAt = resolveDoseTime(d.time, now, d.customEpochMillis),
            sourceZoneId = zone.id,
            drug = d.drug,
            route = d.route,
            amountEntered = d.amount,
            enteredUnit = d.unit,
            normalizedMilligrams = DoseEvent.normalizeMilligrams(d.amount, d.unit),
            status = DoseStatus.ADMINISTERED,
            note = d.note.ifBlank { null },
        )
        return s.copy(
            doses = (s.doses + event).sortedBy { it.occurredAt },
            sheet = DashboardSheet.NONE,
            toast = "투약 기록됨 · 타임라인과 예상 흐름에 반영했어요",
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
        return applyRegimens(s, listOf(regimen), now, "반복 일정 저장됨")
    }

    /** Merge CSV-imported records: new doses de-duped against same-day/same-drug, labs appended. */
    fun mergeImported(
        s: DashboardState,
        doses: List<DoseEvent>,
        labs: List<LabResult>,
        skipped: Int,
    ): DashboardState {
        val newDoses = doses.filterNot { g -> s.doses.any { sameDayDrug(it, g) } }
        val addedNote = buildString {
            append("CSV 불러옴 · 투약 ${newDoses.size}건 · 검사 ${labs.size}건")
            if (skipped > 0) append(" · 건너뜀 $skipped")
        }
        return s.copy(
            doses = (s.doses + newDoses).sortedBy { it.occurredAt },
            labs = (s.labs + labs).sortedBy { it.collectedAt ?: Instant.MIN },
            toast = addedNote,
            newIds = newDoses.map { it.id.toString() }.toSet(),
        )
    }

    /** One-tap example: EV IM 10mg/2주 + CPA 경구 25mg/일, started 60 days ago. */
    fun loadSampleRegimen(s: DashboardState, now: Instant): DashboardState {
        if (s.regimens.isNotEmpty()) return s.copy(toast = "이미 반복 일정이 있어요")
        val start = now.minus(60, ChronoUnit.DAYS)
        val sample = listOf(
            Regimen(UUID.randomUUID(), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 10.0, DoseUnit.MG, 14, start, null),
            Regimen(UUID.randomUUID(), Drug.CYPROTERONE, Route.ORAL, 25.0, DoseUnit.MG, 1, start, null),
        )
        return applyRegimens(s, sample, now, "예시 데이터 넣음 · EV 2주 + CPA 매일 (2개월)")
    }

    private fun applyRegimens(s: DashboardState, regimens: List<Regimen>, now: Instant, toastPrefix: String): DashboardState {
        val generated = regimens
            .flatMap { Regimen.expand(it, now) }
            .filterNot { g -> s.doses.any { sameDayDrug(it, g) } }
            .sortedBy { it.occurredAt }
        return s.copy(
            regimens = s.regimens + regimens,
            doses = (s.doses + generated).sortedBy { it.occurredAt },
            sheet = DashboardSheet.NONE,
            toast = "$toastPrefix · ${generated.size}건 기록에 추가했어요",
            newIds = generated.map { it.id.toString() }.toSet(),
        )
    }

    private fun sameDayDrug(a: DoseEvent, b: DoseEvent): Boolean =
        a.drug == b.drug &&
            a.occurredAt.epochSecond / 86_400L == b.occurredAt.epochSecond / 86_400L

    // ── lab sheet ─────────────────────────────────────────────
    fun editLab(s: DashboardState, block: (LabDraft) -> LabDraft): DashboardState =
        s.copy(labDraft = block(s.labDraft))

    fun focusLabField(s: DashboardState, field: LabField): DashboardState =
        editLab(s) { it.copy(focus = field) }

    fun pressKey(s: DashboardState, key: String): DashboardState {
        val field = s.labDraft.focus
        if (field == LabField.NONE) return s
        val current = if (field == LabField.E2) s.labDraft.e2 else s.labDraft.tt
        val next = when {
            key == "⌫" -> current.dropLast(1)
            current.length >= 6 -> current
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
        val id = UUID.randomUUID()
        val lab = LabResult(
            id = id,
            collectedAt = resolveLabTime(d.time, now, zone),
            sourceZoneId = zone.id,
            assay = d.method,
            analytes = analytes,
            note = d.note.ifBlank { null },
        )
        return s.copy(
            labs = (s.labs + lab).sortedBy { it.collectedAt ?: Instant.MIN },
            sheet = DashboardSheet.NONE,
            labDraft = d.copy(e2 = "", tt = "", note = "", focus = LabField.NONE),
            toast = "실제 검사값으로 저장됨 · 그래프에 노란 마름모로 표시돼요",
            newIds = setOf(id.toString()),
        )
    }

    // ── helpers ───────────────────────────────────────────────
    private const val DOSE_MIN = 0.5
    private const val DOSE_MAX = 500.0
    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}

fun resolveDoseTime(choice: DoseTimeChoice, now: Instant, customEpochMillis: Long? = null): Instant = when (choice) {
    DoseTimeChoice.NOW -> now
    DoseTimeChoice.MINUS_30M -> now.minusSeconds(1800)
    DoseTimeChoice.MINUS_2H -> now.minusSeconds(7200)
    DoseTimeChoice.YESTERDAY -> now.minus(1, ChronoUnit.DAYS)
    DoseTimeChoice.CUSTOM -> customEpochMillis?.let(Instant::ofEpochMilli) ?: now
}

fun resolveLabTime(choice: LabTimeChoice, now: Instant, zone: ZoneId): Instant = when (choice) {
    LabTimeChoice.NOW -> now
    LabTimeChoice.THIS_MORNING ->
        now.atZone(zone).toLocalDate().atTime(9, 30).atZone(zone).toInstant()
    LabTimeChoice.YESTERDAY -> now.minus(1, ChronoUnit.DAYS)
}
