package com.hormonelog.app.chat

import com.hormonelog.app.analysis.Analysis
import com.hormonelog.app.analysis.HomeLogic
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.plainNumber
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.Route
import com.hormonelog.core.modelengine.EstimatePoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Read-only answers built from local records; none of these values come from an LLM. */
object ChatStatus {
    fun nextInjection(s: AppState, now: Instant, zone: ZoneId): String {
        val next = HomeLogic.nextDose(s.regimens.filter { it.route in setOf(Route.IM_INJECTION, Route.SC_INJECTION) },
            s.doses.filter { !it.occurredAt.isAfter(now) }, now, zone)
        return "다음 주사 · 등록된 일정 기준\n" + (next?.let {
            val r = it.regimen
            "${time(it.at, zone)} · ${doseSummary(r.drug, r.route, r.amountEntered, r.enteredUnit)}${if (it.overdue) " · 예정 시각 지남" else ""}"
        } ?: "등록된 주사 일정이 없어요.")
    }
    private fun time(at: Instant, zone: ZoneId) = at.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    private fun label(analyte: Analyte) = when (analyte) {
        Analyte.ESTRADIOL -> "E2"
        Analyte.TOTAL_TESTOSTERONE -> "Total T"
        Analyte.PROGESTERONE -> "P4"
    }
    private fun estimate(name: String, point: EstimatePoint?, unit: String): String =
        if (point == null) "$name: 추정 불가 · 사용 가능한 투약 기록/모델 없음"
        else "$name: ${point.median.toInt()} $unit · 예상 범위 ${point.lower.toInt()}–${point.upper.toInt()}"

    fun summary(s: AppState, now: Instant, zone: ZoneId): String {
        val doses = s.doses.filter { !it.occurredAt.isAfter(now) }
        val labs = s.labs.filter { it.collectedAt?.isAfter(now) != true }
        val status = s.settings.gonadalStatus
        val calibration = Analysis.calibrate(doses, labs, status)
        val e2 = Analysis.e2At(doses, s.regimens, calibration, now, now, zone)
        val tt = Analysis.ttAt(doses, labs, status, calibration, now)
        val p4 = Analysis.p4At(doses, now)
        val last = doses.filter { it.status.wasTaken }.maxByOrNull { it.occurredAt }
        val next = HomeLogic.nextDose(s.regimens, doses, now, zone)
        val injection = HomeLogic.nextDose(s.regimens.filter { it.route in setOf(Route.IM_INJECTION, Route.SC_INJECTION) }, doses, now, zone)
        return buildString {
            appendLine("현재 상태 · ${time(now, zone)} (${zone.id})")
            appendLine("\n현재 추정 · 실측 아님")
            appendLine(estimate("E2", e2, "pg/mL"))
            appendLine(if (status == GonadalStatus.DECLINED) "Total T: 비공개 설정으로 표시하지 않음" else estimate("Total T", tt, "ng/dL"))
            appendLine(p4?.let { "P4 복용분: ${String.format(Locale.ROOT, "%.2f", it.median)} ng/mL · 체내 총농도 아님" }
                ?: "P4 복용분: 추정 불가 · 복용 기록 없음 또는 경구 미분화 100–300 mg 지원 범위 밖")
            appendLine("문헌 모델 참고값이며 지원하는 투약만 반영해요. P4의 제품·식사 조건과 개인별 불확실성은 반영하지 않아요.")
            appendLine("\n최근 실제 투약")
            appendLine(last?.let { "${time(it.occurredAt, zone)} · ${doseSummary(it.drug, it.route, it.amountEntered, it.enteredUnit)}" } ?: "투약 기록 없음")
            appendLine("\n다음 투약 · 등록된 일정 기준")
            appendLine(next?.let { n ->
                val r = n.regimen
                "${time(n.at, zone)} · ${doseSummary(r.drug, r.route, r.amountEntered, r.enteredUnit)}${if (n.overdue) " · 예정 시각 지남" else ""}"
            } ?: "투약 일정 없음")
            if (injection != null && injection.regimen.id != next?.regimen?.id) {
                val r = injection.regimen
                appendLine("다음 주사: ${time(injection.at, zone)} · ${doseSummary(r.drug, r.route, r.amountEntered, r.enteredUnit)}${if (injection.overdue) " · 예정 시각 지남" else ""}")
            }
            appendLine()
            append(labResults(s, now, zone, limit = 3))
        }
    }

    fun labResults(s: AppState, now: Instant, zone: ZoneId, limit: Int = 10): String {
        val labs = s.labs.filter { it.collectedAt?.isAfter(now) != true }
        if (labs.isEmpty()) return "실측 검사 · 검사 기록 없음"
        val dated = labs.filter { it.collectedAt != null }.sortedByDescending { it.collectedAt }
        val unknown = labs.filter { it.collectedAt == null }
        return buildString {
            appendLine("실측 검사 · ${time(now, zone)} 조회 · 최대 ${limit}회")
            (dated + unknown).take(limit).forEach { lab ->
                appendLine("${lab.collectedAt?.let { time(it, zone) } ?: "채혈 시각 모름"}${if (lab.isBaseline) " · 기저 검사" else ""}")
                appendLine(lab.analytes.joinToString(" · ") { "${label(it.analyte)} ${plainNumber(it.reportedValue)} ${it.reportedUnit}" }.ifBlank { "검사 항목 없음" })
            }
            if (unknown.isNotEmpty()) appendLine("채혈 시각 모름 ${unknown.size}회는 시간 순서를 정할 수 없어 날짜 있는 검사 뒤에 표시해요.")
            if (labs.size > limit) appendLine("총 ${labs.size}회 중 ${limit}회 표시 · 나머지는 타임라인에서 확인해 주세요.")
            append("검사값은 채혈 당시 실측이며 현재 추정치와 달라요.")
        }
    }
}
