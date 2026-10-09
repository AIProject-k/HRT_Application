package com.hormonelog.app.state

import com.hormonelog.core.data.RecordSnapshot
import com.hormonelog.core.data.SkippedRow
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.Clinic
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.JournalEntry
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.StockItem
import com.hormonelog.core.domain.TUnit
import com.hormonelog.core.domain.VisitMemo
import com.hormonelog.core.modelengine.AnalyteCalibration
import com.hormonelog.core.modelengine.CycleSpot
import com.hormonelog.core.modelengine.EstimatePoint
import java.time.Instant
import java.util.UUID

/** Every place the app can be. The first four are tabs; the rest are pushed on top of one. */
enum class Screen {
    HOME, TIMELINE, FLOW, ME,
    MODEL, EVIDENCE, SECURITY, RESTORE, SCHEDULES, SCHEDULE_EDIT, NOTIFICATIONS, MEMOS, RECORDS, REPORT, LICENSES,
    LAB_RESULT, ONBOARDING;

    val isTab: Boolean get() = this == HOME || this == TIMELINE || this == FLOW || this == ME
}

/** The record a timeline row stands for. */
sealed interface RowTarget {
    val id: UUID

    data class Dose(override val id: UUID) : RowTarget
    data class Lab(override val id: UUID) : RowTarget
    data class Journal(override val id: UUID) : RowTarget
    data class Memo(override val id: UUID) : RowTarget
}

/** The sheets that slide up over a screen. */
sealed interface Sheet {
    data object None : Sheet
    data object Dose : Sheet
    data object Lab : Sheet

    /** "무엇을 기록할까요?" opened from the timeline. */
    data object AddRecord : Sheet
    data class RowMenu(val target: RowTarget) : Sheet
    data object Testes : Sheet
    data object Appointment : Sheet
    data object MemoEditor : Sheet
    data object ClinicEditor : Sheet
    data object StockAdd : Sheet
}

/** The record lists as they were just before a change, so one tap puts them back. */
data class UndoSnapshot(
    val doses: List<DoseEvent>,
    val labs: List<LabResult>,
    val regimens: List<Regimen>,
    val clinics: List<Clinic>,
    val memos: List<VisitMemo>,
    val journal: List<JournalEntry>,
    val stock: List<StockItem>,
    val nextVisitMillis: Long?,
    /** Records set aside as unreadable travel with the lists, so "delete all" can take them and still be undone. */
    val carried: Map<String, List<String>> = emptyMap(),
)

/** Past doses a plan would have produced, held back until the user has seen how many. */
data class PendingBackfill(
    val doses: List<DoseEvent>,
    val regimens: List<Regimen>,
    val title: String,
    val body: String,
)

/** A question the user must answer before anything is changed. */
sealed interface Confirm {
    /** A same-drug dose already sits within hours of the one being saved. */
    data class DuplicateDose(val existing: DoseEvent) : Confirm

    data class Backfill(val pending: PendingBackfill) : Confirm

    /** A plan for the same drug and route is already running. */
    data class ReplaceRegimen(val existing: Regimen, val replacement: Regimen, val backfill: PendingBackfill?) : Confirm

    data object ClearAll : Confirm
    data class DeleteRegimen(val id: UUID) : Confirm
    data class DeleteMemo(val id: UUID) : Confirm
    data class DeleteClinic(val id: UUID) : Confirm
}

/** What happened to the lab that was just saved, for the result screen. */
data class LabResultSummary(
    val labId: UUID,
    val collectedAt: Instant?,
    val e2: E2Outcome?,
    val tt: TtOutcome?,
    val hoursSinceDose: Double?,
    /** The draw is earlier than the first dose on record: it belongs to no cycle. */
    val beforeFirstDose: Boolean,
    /** 1-based place of this lab among the labs that shaped the same curve, by time; null if it did not. */
    val calibrationOrdinal: Int? = null,
)

data class E2Outcome(
    val reported: Double,
    val unit: E2Unit,
    val measuredPg: Double,
    val expected: EstimatePoint?,
    val diffPercent: Int?,
    val withinRange: Boolean?,
    val spot: CycleSpot?,
    val calibration: AnalyteCalibration?,
)

data class TtOutcome(
    val reported: Double,
    val unit: TUnit,
    val measuredNgDl: Double,
    val expected: EstimatePoint?,
    val diffPercent: Int?,
    val withinRange: Boolean?,
    val calibration: AnalyteCalibration?,
)

/** The six steps of first-run setup, and what has been picked so far. */
data class OnboardingState(
    val step: Int = 0,
    val route: Route = Route.IM_INJECTION,
    val drug: Drug = Drug.ESTRADIOL_VALERATE,
    val amountText: String = "",
    val patchStrength: Double = 50.0,
    val patchCycle: PatchCycle = PatchCycle.TWICE_WEEKLY,
    val interval: IntervalChoice = IntervalChoice.WEEKLY,
    /** The user skipped the schedule step, so there is nothing to back-fill either. */
    val noSchedule: Boolean = false,
    val startMillis: Long? = null,
    val backfill: Boolean = true,
    val gonadal: GonadalStatus? = null,
    val e2Unit: E2Unit = E2Unit.PG_ML,
    val tUnit: TUnit = TUnit.NG_DL,
    val baselineE2: String = "",
    val baselineT: String = "",
    val baselineMillis: Long? = null,
) {
    companion object {
        const val STEPS = 6
    }
}

/** Making a backup file: built in memory, handed to the file picker, then reported. */
sealed interface BackupUi {
    data object Idle : BackupUi
    data object Running : BackupUi

    /** Built and waiting for the user to pick where it goes. */
    data class ReadyToSave(val fileName: String) : BackupUi
    data class Done(val fileName: String, val sizeKb: Int, val doses: Int, val labs: Int, val regimens: Int, val memos: Int) : BackupUi
    data class Failed(val reason: String) : BackupUi
}

enum class RestoreKind { BACKUP, CSV }

/** A file read and previewed, not yet applied. */
data class RestorePreview(
    val fileName: String,
    val kind: RestoreKind,
    val createdAtMillis: Long?,
    val snapshot: RecordSnapshot,
    val settings: AppSettings?,
    val skipped: List<SkippedRow>,
    /** For a CSV: how many of its records the app already has. */
    val duplicates: Int = 0,
)

/** Something the file picker returned that could not be previewed. */
sealed interface RestoreIssue {
    data object Unreadable : RestoreIssue
}

enum class ChartRange(val days: Int?) { WEEK(7), MONTH(30), QUARTER(90), HALF(180), YEAR(365), ALL(null), CUSTOM(null) }

enum class HormoneSeries { E2, TT }

data class FlowUi(
    val series: HormoneSeries = HormoneSeries.E2,
    val range: ChartRange = ChartRange.QUARTER,
    val customFromMillis: Long? = null,
    /** 0..1 along the chart while a finger is on it. */
    val scrub: Float? = null,
    val guide: Boolean = false,
)

enum class TimelineType { ALL, DOSE, LAB, MISSED, CONDITION, MEMO }

enum class TimelinePeriod(val days: Int?) { MONTH(30), QUARTER(90), ALL(null) }

data class TimelineUi(
    val query: String = "",
    val type: TimelineType = TimelineType.ALL,
    val drug: Drug? = null,
    val period: TimelinePeriod = TimelinePeriod.ALL,
    /** Month keys ("2026-10") of the folded daily-medication groups that are open. */
    val openMonths: Set<String> = emptySet(),
) {
    val isDefault: Boolean get() = query.isEmpty() && type == TimelineType.ALL && drug == null && period == TimelinePeriod.ALL
}

enum class ReportPeriod(val days: Int?) { MONTH(30), QUARTER(90), HALF(180), ALL(null) }

enum class ReportPart { CHART, LABS, SCHEDULE, ADHERENCE, MODEL, MEMOS }

enum class ReportKind { PDF, IMAGE }

/** A finished report in the app's private cache, waiting for the user to pick where to send it. */
data class ReportFile(val path: String, val mime: String, val name: String)

data class ReportUi(
    val period: ReportPeriod = ReportPeriod.QUARTER,
    val include: Set<ReportPart> = setOf(ReportPart.CHART, ReportPart.LABS, ReportPart.SCHEDULE, ReportPart.ADHERENCE, ReportPart.MODEL),
    val busy: Boolean = false,
    val ready: ReportFile? = null,
)

/** Whole-app state. Held in the ViewModel and changed only through the reducers. */
data class AppState(
    // ── records ──
    val doses: List<DoseEvent> = emptyList(),
    val labs: List<LabResult> = emptyList(),
    val regimens: List<Regimen> = emptyList(),
    val clinics: List<Clinic> = emptyList(),
    val memos: List<VisitMemo> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val stock: List<StockItem> = emptyList(),
    val nextVisitMillis: Long? = null,
    /** Records a newer build wrote that this one cannot read; kept untouched and written back. */
    val carried: Map<String, List<String>> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    // ── navigation ──
    val screen: Screen = Screen.HOME,
    val stack: List<Screen> = emptyList(),
    val sheet: Sheet = Sheet.None,
    val confirm: Confirm? = null,
    // ── feedback ──
    val toast: String? = null,
    val undo: UndoSnapshot? = null,
    /** Persistent banner for a storage problem; it does not time out because losing it could cost the records. */
    val storageWarning: String? = null,
    // ── drafts ──
    val doseDraft: DoseDraft = DoseDraft(),
    val labDraft: LabDraft = LabDraft(),
    val journalDraft: JournalDraft = JournalDraft(),
    val scheduleDraft: ScheduleDraft? = null,
    val memoDraft: MemoDraft? = null,
    val clinicDraft: ClinicDraft? = null,
    val onboarding: OnboardingState = OnboardingState(),
    val labResult: LabResultSummary? = null,
    // ── backup and restore ──
    val backup: BackupUi = BackupUi.Idle,
    val restore: RestorePreview? = null,
    val restoreIssue: RestoreIssue? = null,
    // ── per-screen view state ──
    val flow: FlowUi = FlowUi(),
    val timeline: TimelineUi = TimelineUi(),
    val report: ReportUi = ReportUi(),
) {
    val snapshot: RecordSnapshot
        get() = RecordSnapshot(doses, labs, regimens, clinics, memos, journal, stock, nextVisitMillis, carried)

    /** True once there is anything at all to draw a curve from or to show. */
    val hasRecords: Boolean get() = doses.isNotEmpty() || labs.isNotEmpty() || regimens.isNotEmpty()

    /** Everything the user has entered, for the delete-all count and the restore warning. */
    val recordCount: Int get() = doses.size + labs.size + regimens.size + memos.size + journal.size + clinics.size
}
