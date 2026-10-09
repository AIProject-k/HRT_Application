package com.hormonelog.app.feature.common

import com.hormonelog.app.state.plainNumber
import com.hormonelog.core.data.SkipReason
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.ExtraLab
import com.hormonelog.core.domain.Symptom
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.InjectionSite
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.PrescriptionBasis
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.Telehealth
import com.hormonelog.core.modelengine.DoseExclusion
import com.hormonelog.core.modelengine.LabExclusion
import java.time.DayOfWeek

/** Korean display words for domain values; the one place the app's vocabulary lives. */

val Drug.label: String
    get() = when (this) {
        Drug.ESTRADIOL_VALERATE -> "에스트라디올 발레레이트"
        Drug.ESTRADIOL_CYPIONATE -> "에스트라디올 사이피오네이트"
        Drug.ESTRADIOL_TABLET -> "에스트라디올"
        Drug.ESTRADIOL_PATCH -> "에스트라디올 패치"
        Drug.ESTRADIOL_GEL -> "에스트라디올 젤"
        Drug.SPIRONOLACTONE -> "스피로노락톤"
        Drug.CYPROTERONE -> "사이프로테론"
    }

/** The short form used in rows and tiles ("EV 5 mg · 주사"). */
val Drug.shortLabel: String
    get() = when (this) {
        Drug.ESTRADIOL_VALERATE -> "EV"
        Drug.ESTRADIOL_CYPIONATE -> "EC"
        Drug.ESTRADIOL_TABLET -> "에스트라디올"
        Drug.ESTRADIOL_PATCH -> "패치"
        Drug.ESTRADIOL_GEL -> "젤"
        Drug.SPIRONOLACTONE -> "스피로노락톤"
        Drug.CYPROTERONE -> "사이프로테론"
    }

/** What the chip row calls a route; both injections are one "주사" until the sub-choice. */
val Route.label: String
    get() = when (this) {
        Route.ORAL -> "경구"
        Route.SUBLINGUAL -> "설하"
        Route.PATCH -> "패치"
        Route.GEL -> "젤"
        Route.IM_INJECTION -> "근육주사"
        Route.SC_INJECTION -> "피하주사"
    }

/** "주사" for both kinds, as a row title says; the exact kind shows in the dose sheet. */
val Route.shortLabel: String
    get() = when (this) {
        Route.IM_INJECTION -> "주사"
        Route.SC_INJECTION -> "주사(피하)"
        else -> label
    }

val Route.isInjection: Boolean get() = this == Route.IM_INJECTION || this == Route.SC_INJECTION

val DoseStatus.label: String
    get() = when (this) {
        DoseStatus.ADMINISTERED -> "투약함"
        DoseStatus.DELAYED -> "늦게"
        DoseStatus.SKIPPED -> "놓침"
        DoseStatus.CORRECTED -> "정정됨"
    }

val DoseStatus.hint: String
    get() = when (this) {
        DoseStatus.ADMINISTERED -> "예정대로 맞았어요"
        DoseStatus.DELAYED -> "늦었지만 맞았어요 · 곡선에 그대로 반영돼요"
        DoseStatus.SKIPPED -> "건너뛰었어요 · 곡선에서 빠져요"
        DoseStatus.CORRECTED -> "기록을 정정했어요"
    }

/** Statuses the recorder offers; CORRECTED comes from editing, never from a chip. */
val DOSE_STATUS_CHOICES = listOf(DoseStatus.ADMINISTERED, DoseStatus.DELAYED, DoseStatus.SKIPPED)

val DoseUnit.label: String
    get() = when (this) {
        DoseUnit.MG -> "mg"
        DoseUnit.MG_PER_DAY -> "mg/일"
        DoseUnit.PATCH -> "매"
        DoseUnit.UG_PER_DAY -> "µg/일"
    }

val Assay.label: String
    get() = when (this) {
        Assay.LC_MS_MS -> "LC-MS/MS"
        Assay.IMMUNOASSAY -> "면역측정"
        Assay.UNKNOWN -> "검사 방식 모름"
    }

val InjectionSite.label: String
    get() = when (this) {
        InjectionSite.LEFT_THIGH -> "왼 허벅지"
        InjectionSite.RIGHT_THIGH -> "오른 허벅지"
        InjectionSite.LEFT_GLUTE -> "왼 엉덩이"
        InjectionSite.RIGHT_GLUTE -> "오른 엉덩이"
        InjectionSite.LEFT_ABDOMEN -> "왼 배"
        InjectionSite.RIGHT_ABDOMEN -> "오른 배"
    }

val PatchCycle.label: String
    get() = when (this) {
        PatchCycle.TWICE_WEEKLY -> "주 2회 교체"
        PatchCycle.WEEKLY -> "주 1회 교체"
    }

val GonadalStatus.label: String
    get() = when (this) {
        GonadalStatus.INTACT -> "있음"
        GonadalStatus.POST_ORCHIECTOMY -> "수술함"
        GonadalStatus.DECLINED -> "말하고 싶지 않음"
        GonadalStatus.UNKNOWN -> "아직 안 골랐어요"
    }

val RecordSource.label: String
    get() = when (this) {
        RecordSource.MANUAL -> "직접"
        RecordSource.SCHEDULE -> "일정"
        RecordSource.IMPORT -> "CSV"
        RecordSource.SAMPLE -> "예시"
    }

val DayOfWeek.shortLabel: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "월"
        DayOfWeek.TUESDAY -> "화"
        DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"
        DayOfWeek.FRIDAY -> "금"
        DayOfWeek.SATURDAY -> "토"
        DayOfWeek.SUNDAY -> "일"
    }

/** The week as the design lists it: 일 월 화 수 목 금 토. */
val WEEK_DISPLAY_ORDER = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
)

val PrescriptionBasis.label: String
    get() = when (this) {
        PrescriptionBasis.INFORMED_CONSENT -> "정보동의서"
        PrescriptionBasis.DIAGNOSIS -> "진단서·소견서"
        PrescriptionBasis.REFERRAL -> "자문의뢰"
        PrescriptionBasis.UNKNOWN -> "모름"
    }

val Telehealth.label: String
    get() = when (this) {
        Telehealth.YES -> "비대면 가능"
        Telehealth.NO -> "대면만"
        Telehealth.UNKNOWN -> "모름"
    }

/** "EV 5 mg · 주사", "패치 50 µg/일", "사이프로테론 12.5 mg · 경구". */
fun DoseEvent.summary(): String = doseSummary(drug, route, amountEntered, enteredUnit)

fun doseSummary(drug: Drug, route: Route, amount: Double, unit: DoseUnit): String {
    val n = plainNumber(amount)
    return when (route) {
        Route.PATCH -> "패치 $n ${unit.label}"
        Route.GEL -> "젤 $n ${unit.label}"
        Route.IM_INJECTION, Route.SC_INJECTION -> "${drug.shortLabel} $n ${unit.label} · ${route.shortLabel}"
        else -> "${drug.shortLabel} $n ${unit.label} · ${route.label}"
    }
}

fun Regimen.summary(): String {
    val n = plainNumber(amountEntered)
    return when (route) {
        Route.PATCH -> "패치 $n ${enteredUnit.label}"
        else -> "${drug.label} $n ${enteredUnit.label} · ${if (route == Route.IM_INJECTION || route == Route.SC_INJECTION) "주사" else route.label}"
    }
}

/** "7일마다 · 토", "주 2회 · 월·목", "매일", "14일마다": how often a plan fires. */
fun Regimen.cadenceLabel(): String {
    val days = WEEK_DISPLAY_ORDER.filter { it in weekdaySet }.joinToString("·") { it.shortLabel }
    return when {
        route == Route.PATCH -> (if (everyDays == 7 || weekdaySet.size == 1) "주 1회 교체" else "주 2회 교체") + if (days.isNotEmpty()) " · $days" else ""
        isWeekdayPlan && weekdaySet.size >= 2 -> "주 ${weekdaySet.size}회 · $days"
        isWeekdayPlan -> "7일마다 · $days"
        everyDays == 1 -> "매일"
        else -> "${everyDays}일마다"
    }
}

/** Why one dose is not on the curve, in the words of the "곡선에서 제외" badge. */
val DoseExclusion.reasonText: String
    get() = when (this) {
        DoseExclusion.NO_MODEL -> "근거가 부족해 곡선을 그리지 않아요"
        DoseExclusion.UNSUPPORTED_COMBINATION -> "약물과 경로 조합을 계산할 수 없어요"
        DoseExclusion.PATCH_STRENGTH_MISSING -> "패치 규격(µg/일)이 없어요"
    }

/** Why a lab did not shape the curve. */
val LabExclusion.text: String
    get() = when (this) {
        LabExclusion.BASELINE -> "HRT 시작 전 검사예요"
        LabExclusion.NO_COLLECTION_TIME -> "채혈 시각 없음"
        LabExclusion.UNKNOWN_UNIT -> "단위를 알 수 없어요"
        LabExclusion.NO_PRIOR_DOSE -> "채혈 전 투약 기록이 없어요"
        LabExclusion.TOO_EARLY -> "기록 시작 직후라 이전 투약 정보 부족"
        LabExclusion.NO_MODEL -> "지원하는 모델이 없는 경로예요"
        LabExclusion.MIXED_UNMODELLED -> "곡선에 없는 경로(젤 등) 투약이 섞여 있어요"
        LabExclusion.MIXED_ROUTES -> "여러 경로가 섞여 있어 하나로 보정하기 어려워요"
        LabExclusion.LOW_PREDICTION -> "예상값이 너무 낮아 비교할 수 없어요"
        LabExclusion.TOO_FEW_LABS -> "T 검사가 2건 이상 있어야 보정해요"
    }

/** A word or two for a table cell. */
val LabExclusion.shortText: String
    get() = when (this) {
        LabExclusion.BASELINE -> "시작 전"
        LabExclusion.NO_COLLECTION_TIME -> "시각 없음"
        LabExclusion.UNKNOWN_UNIT -> "단위 불명"
        LabExclusion.NO_PRIOR_DOSE -> "투약 기록 없음"
        LabExclusion.TOO_EARLY -> "기록 초기"
        LabExclusion.NO_MODEL -> "모델 없음"
        LabExclusion.MIXED_UNMODELLED -> "젤 혼합"
        LabExclusion.MIXED_ROUTES -> "경로 혼합"
        LabExclusion.LOW_PREDICTION -> "예상값 낮음"
        LabExclusion.TOO_FEW_LABS -> "검사 부족"
    }

/** What to do about it, when there is something to do. */
val LabExclusion.fix: String?
    get() = when (this) {
        LabExclusion.NO_COLLECTION_TIME -> "채혈 시각 추가하기"
        LabExclusion.UNKNOWN_UNIT -> "단위 고치기"
        else -> null
    }

val SkipReason.text: String
    get() = when (this) {
        SkipReason.TOO_FEW_COLUMNS -> "열이 부족하거나 형식이 깨진 행"
        SkipReason.UNKNOWN_TYPE -> "행 종류(dose/lab)를 알 수 없음"
        SkipReason.BAD_DATE -> "날짜 형식을 읽을 수 없음"
        SkipReason.UNKNOWN_DRUG -> "알 수 없는 약물 이름"
        SkipReason.UNKNOWN_ROUTE -> "알 수 없는 투여 경로"
        SkipReason.BAD_AMOUNT -> "용량이 0이거나 숫자가 아님"
        SkipReason.BAD_COMBINATION -> "약물과 경로 조합이 맞지 않음"
        SkipReason.NO_VALUE -> "E2·Total T 값이 모두 비어 있음"
    }

val Symptom.label: String
    get() = when (this) {
        Symptom.FATIGUE -> "피곤함"
        Symptom.HEADACHE -> "두통"
        Symptom.CHEST_PAIN -> "가슴 통증"
        Symptom.DIZZINESS -> "어지러움"
        Symptom.LOW_MOOD -> "기분 저하"
        Symptom.ANXIETY -> "불안"
        Symptom.SLEEP -> "잠 문제"
        Symptom.SWELLING -> "부종"
        Symptom.HOT_FLASH -> "홍조"
        Symptom.SKIN -> "피부 변화"
    }

val ExtraLab.label: String
    get() = when (this) {
        ExtraLab.LH -> "LH"
        ExtraLab.FSH -> "FSH"
        ExtraLab.PROLACTIN -> "프로락틴"
        ExtraLab.SHBG -> "SHBG"
        ExtraLab.POTASSIUM -> "칼륨"
    }