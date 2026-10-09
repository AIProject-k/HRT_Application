package com.hormonelog.app.state

import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.InjectionSite
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.TUnit
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.ExtraLab
import com.hormonelog.core.domain.PrescriptionBasis
import com.hormonelog.core.domain.Symptom
import com.hormonelog.core.domain.Telehealth
import com.hormonelog.core.domain.DoseUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Largest dose the recorder accepts; beyond it the entry is refused, never quietly rewritten. */
const val DOSE_MAX = 500.0

/** Patch strengths (µg/day) the design offers as chips. */
val PATCH_STRENGTHS = listOf(25.0, 37.5, 50.0, 75.0, 100.0)

enum class DoseTime { NOW, PICK }

/** How often a repeating plan fires, as the chips say it. */
enum class IntervalChoice(val everyDays: Int?) {
    DAILY(1),
    TWICE_WEEKLY(null),
    WEEKLY(7),
    BIWEEKLY(14),
    CUSTOM(null),
}

/**
 * The 투약 기록 sheet while it is open. One draft serves both a single dose and a repeating
 * plan ([repeat]); editing an existing record keeps its [editingId].
 */
data class DoseDraft(
    val editingId: UUID? = null,
    val repeat: Boolean = false,
    val drug: Drug = Drug.ESTRADIOL_VALERATE,
    val route: Route = Route.IM_INJECTION,
    val amountText: String = "",
    /** The amount came from the last record of this drug, not from the user's hand. */
    val amountFilled: Boolean = false,
    val patchStrength: Double = 50.0,
    val patchCycle: PatchCycle = PatchCycle.TWICE_WEEKLY,
    val time: DoseTime = DoseTime.NOW,
    val pickedMillis: Long? = null,
    val site: InjectionSite? = null,
    val status: DoseStatus = DoseStatus.ADMINISTERED,
    val note: String = "",
    // ── repeating plan ──
    val interval: IntervalChoice = IntervalChoice.WEEKLY,
    val customDays: String = "",
    /** First day of a plan; null = today. */
    val startMillis: Long? = null,
    val timeMinutes: Int = 9 * 60,
) {
    val isPatch: Boolean get() = route == Route.PATCH
    val isGel: Boolean get() = route == Route.GEL
    val isInjection: Boolean get() = route == Route.IM_INJECTION || route == Route.SC_INJECTION

    /** The amount as a number, or null while the text is not one. */
    val amount: Double? get() = if (isPatch) patchStrength else amountText.trim().toDoubleOrNull()

    /** Why the amount cannot be saved, or null. A patch is chosen by chip and is always valid. */
    val amountError: String?
        get() {
            if (isPatch) return null
            val v = amountText.trim()
            return when {
                v.isEmpty() -> "용량을 입력해 주세요"
                !Regex("""\d+(\.\d+)?""").matches(v) -> "숫자 형식을 확인해 주세요 (예: 2.5)"
                v.toDouble() == 0.0 -> "0보다 큰 값을 입력해 주세요"
                v.toDouble() > DOSE_MAX -> "${plain(DOSE_MAX)} 이하로 입력해 주세요"
                else -> null
            }
        }

    val customDaysError: String?
        get() {
            if (!repeat || interval != IntervalChoice.CUSTOM) return null
            val n = customDays.trim().toIntOrNull()
            return if (n == null || n !in 1..90 || !customDays.trim().all { it.isDigit() }) "1–90 사이 정수로 입력해 주세요" else null
        }

    val canSave: Boolean get() = amountError == null && customDaysError == null

    private fun plain(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

enum class DrawChoice { NOW, MORNING, YESTERDAY_EVENING, PICK, UNKNOWN }

/** The 검사 결과 sheet while it is open. */
data class LabDraft(
    val editingId: UUID? = null,
    val e2: String = "",
    val tt: String = "",
    val e2Unit: E2Unit = E2Unit.PG_ML,
    val ttUnit: TUnit = TUnit.NG_DL,
    val draw: DrawChoice = DrawChoice.NOW,
    val pickedMillis: Long? = null,
    /** Defaults to unknown: the app must not stamp a method onto a record the user never reported one for. */
    val assay: Assay = Assay.UNKNOWN,
    val note: String = "",
    val isBaseline: Boolean = false,
) {
    val e2Value: Double? get() = e2.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    val ttValue: Double? get() = tt.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    val e2Error: String? get() = valueError(e2)
    val ttError: String? get() = valueError(tt)

    private fun valueError(text: String): String? {
        if (text.isBlank()) return null
        val v = text.trim().toDoubleOrNull()
        return when {
            v == null || !v.isFinite() || !Regex("""\d+(\.\d+)?|\.\d+""").matches(text.trim()) -> "올바른 숫자를 입력해 주세요"
            v < 0 -> "0 이상의 값을 입력해 주세요"
            else -> null
        }
    }

    /** At least one value above zero, and nothing malformed beside it. */
    val canSave: Boolean
        get() = e2Error == null && ttError == null && ((e2Value ?: 0.0) > 0.0 || (ttValue ?: 0.0) > 0.0)

    companion object {
        fun of(l: LabResult, e2Unit: E2Unit, ttUnit: TUnit): LabDraft {
            val e2 = l.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }
            val tt = l.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE }
            return LabDraft(
                editingId = l.id,
                e2 = e2?.reportedValue?.let(::plainNumber).orEmpty(),
                tt = tt?.reportedValue?.let(::plainNumber).orEmpty(),
                e2Unit = E2Unit.entries.firstOrNull { it.label.equals(e2?.reportedUnit, ignoreCase = true) } ?: e2Unit,
                ttUnit = TUnit.entries.firstOrNull { it.label.equals(tt?.reportedUnit, ignoreCase = true) } ?: ttUnit,
                draw = if (l.collectedAt == null) DrawChoice.UNKNOWN else DrawChoice.PICK,
                pickedMillis = l.collectedAt?.toEpochMilli(),
                assay = l.assay,
                note = l.note.orEmpty(),
                isBaseline = l.isBaseline,
            )
        }
    }
}

/** "412.0" -> "412"; "12.50" -> "12.5". */
fun plainNumber(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString().trimEnd('0').trimEnd('.')

/** Time a single dose is recorded at; [now] is read when the user saves, never earlier. */
fun resolveDoseTime(d: DoseDraft, now: Instant): Instant = when (d.time) {
    DoseTime.NOW -> now
    DoseTime.PICK -> d.pickedMillis?.let(Instant::ofEpochMilli) ?: now
}

/** Null for "시각 모름": the result is kept but cannot be placed against a curve. */
fun resolveDrawTime(d: LabDraft, now: Instant, zone: ZoneId): Instant? = when (d.draw) {
    DrawChoice.NOW -> now
    DrawChoice.MORNING -> now.atZone(zone).toLocalDate().atTime(9, 0).atZone(zone).toInstant()
    DrawChoice.YESTERDAY_EVENING -> now.atZone(zone).toLocalDate().minusDays(1).atTime(18, 0).atZone(zone).toInstant()
    DrawChoice.PICK -> d.pickedMillis?.let(Instant::ofEpochMilli) ?: now
    DrawChoice.UNKNOWN -> null
}

/** First day of a repeating plan, today unless the user picked one. */
fun planStartDate(d: DoseDraft, now: Instant, zone: ZoneId): LocalDate =
    d.startMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: now.atZone(zone).toLocalDate()

/** The two weekdays "주 2회" falls on: the start day and the one three days on (Mon/Thu, Sat/Tue). */
fun twiceWeeklyDays(start: DayOfWeek): Set<DayOfWeek> = setOf(start, start.plus(3))

/** A drug used together with a route, the unit of "자주 쓴 조합". */
data class Combo(
    val drug: Drug,
    val route: Route,
    val amount: Double,
    val unit: DoseUnit,
    val patchCycle: PatchCycle?,
    val uses: Int,
    val last: Instant,
)

/** Most-used combinations of the last [days] days (all time when nothing is recent), most-used first. */
fun frequentCombos(doses: List<DoseEvent>, now: Instant, days: Long = 30, limit: Int = 3): List<Combo> {
    fun build(list: List<DoseEvent>) = list
        .groupBy { listOf(it.drug, it.route, it.amountEntered, it.enteredUnit) }
        .map { (_, g) ->
            val newest = g.maxBy { it.occurredAt }
            Combo(newest.drug, newest.route, newest.amountEntered, newest.enteredUnit, newest.patchCycle, g.size, newest.occurredAt)
        }
        .sortedWith(compareByDescending<Combo> { it.uses }.thenByDescending { it.last })
    val taken = doses.filter { it.status.wasTaken }
    val recent = build(taken.filter { it.occurredAt.isAfter(now.minusSeconds(days * 86_400)) && !it.occurredAt.isAfter(now) })
    return (if (recent.isEmpty()) build(taken) else recent).take(limit)
}

/** What the user last took of [drug] by [route] — the amount to pre-fill, with its patch cycle. */
fun lastOf(doses: List<DoseEvent>, drug: Drug, route: Route): DoseEvent? =
    doses.filter { it.drug == drug && it.route == route && it.status.wasTaken }.maxByOrNull { it.occurredAt }

// ── 다른 기록: 컨디션 · 몸 · 재고 · 추가 검사 ──────────────────────────

enum class RecordTab { CONDITION, BODY, STOCK, LABS }

/** The 컨디션 / 몸 / 추가 검사 forms of the 다른 기록 screen; 재고 edits the stock list directly. */
data class JournalDraft(
    /** Non-null while an existing entry is being changed. */
    val editingId: UUID? = null,
    val tab: RecordTab = RecordTab.CONDITION,
    val score: Int = 3,
    val symptoms: Set<Symptom> = emptySet(),
    val note: String = "",
    val weight: String = "",
    val systolic: String = "",
    val diastolic: String = "",
    val labs: Map<ExtraLab, String> = emptyMap(),
) {
    private fun number(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

    val weightError: String? get() = if (weight.isBlank() || number(weight) != null) null else "올바른 숫자를 입력해 주세요"
    val pressureError: String?
        get() {
            if (systolic.isBlank() && diastolic.isBlank()) return null
            val s = systolic.trim().toIntOrNull()
            val d = diastolic.trim().toIntOrNull()
            return if (s == null || d == null || s !in 50..300 || d !in 30..200 || d >= s) "수축기·이완기를 모두 올바르게 입력해 주세요" else null
        }

    val canSave: Boolean
        get() = when (tab) {
            RecordTab.CONDITION -> true
            RecordTab.BODY -> (number(weight) != null || (systolic.isNotBlank() && diastolic.isNotBlank())) && weightError == null && pressureError == null
            RecordTab.LABS -> labs.values.any { number(it) != null } && labs.values.all { it.isBlank() || number(it) != null }
            RecordTab.STOCK -> false
        }

    fun weightValue(): Double? = number(weight)
    fun labValues(): Map<ExtraLab, Double> = labs.mapNotNull { (k, v) -> number(v)?.let { k to it } }.toMap()
}

// ── 반복 일정 편집 ─────────────────────────────────────────────

/** A plan being created or changed on the 일정 screens. */
data class ScheduleDraft(
    val editingId: UUID? = null,
    val drug: Drug = Drug.ESTRADIOL_VALERATE,
    val route: Route = Route.IM_INJECTION,
    val amountText: String = "",
    val patchStrength: Double = 50.0,
    val patchCycle: PatchCycle = PatchCycle.TWICE_WEEKLY,
    val interval: IntervalChoice = IntervalChoice.WEEKLY,
    val customDays: String = "",
    val weekdays: Set<DayOfWeek> = emptySet(),
    val timeMinutes: Int = 9 * 60,
    /** First day of the plan; null = today. Fixed once the plan exists. */
    val startMillis: Long? = null,
    /** Last day; null = no end. */
    val endMillis: Long? = null,
) {
    val isPatch: Boolean get() = route == Route.PATCH
    val amount: Double? get() = if (isPatch) patchStrength else amountText.trim().toDoubleOrNull()

    val amountError: String?
        get() {
            if (isPatch) return null
            val v = amountText.trim()
            return when {
                v.isEmpty() -> "용량을 입력해 주세요"
                !Regex("""\d+(\.\d+)?""").matches(v) -> "숫자 형식을 확인해 주세요 (예: 2.5)"
                v.toDouble() == 0.0 -> "0보다 큰 값을 입력해 주세요"
                v.toDouble() > DOSE_MAX -> "용량이 너무 커요"
                else -> null
            }
        }

    val customDaysError: String?
        get() {
            if (interval != IntervalChoice.CUSTOM) return null
            val n = customDays.trim().toIntOrNull()
            return if (n == null || n !in 1..90 || !customDays.trim().all { it.isDigit() }) "1–90 사이 정수로 입력해 주세요" else null
        }

    /** "주 2회" needs two weekdays, "매주" one. */
    val weekdaysError: String?
        get() = when {
            effectiveInterval == IntervalChoice.TWICE_WEEKLY && weekdays.size != 2 -> "요일 2개를 골라 주세요"
            effectiveInterval == IntervalChoice.WEEKLY && weekdays.size != 1 -> "요일 1개를 골라 주세요"
            else -> null
        }

    /** A patch is on a weekly plan or a twice-weekly one, by its change cycle, not by the interval chips. */
    val effectiveInterval: IntervalChoice
        get() = if (isPatch) (if (patchCycle == PatchCycle.WEEKLY) IntervalChoice.WEEKLY else IntervalChoice.TWICE_WEEKLY) else interval

    /** Whether the end day is before the first day. */
    fun endBeforeStart(todayMillis: Long): Boolean = endMillis != null && endMillis < (startMillis ?: todayMillis)

    fun canSave(todayMillis: Long): Boolean =
        amountError == null && customDaysError == null && weekdaysError == null && !endBeforeStart(todayMillis)
}

// ── 진료 메모 · 병원 ──────────────────────────────────────────

data class MemoDraft(
    val editingId: UUID? = null,
    val date: LocalDate = LocalDate.now(),
    val title: String = "",
    val body: String = "",
    val prescription: String = "",
    val link: String = "",
) {
    val canSave: Boolean get() = title.isNotBlank()
    val linkError: String? get() = if (link.isBlank() || link.trim().startsWith("http://") || link.trim().startsWith("https://")) null else "http:// 또는 https:// 로 시작하는 주소만 쓸 수 있어요"
}

/** Draft backing the 병원 정보 add/edit sheet. */
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
    val linkError: String? get() = if (sourceUrl.isBlank() || sourceUrl.trim().startsWith("http://") || sourceUrl.trim().startsWith("https://")) null else "http:// 또는 https:// 로 시작하는 주소만 쓸 수 있어요"
}