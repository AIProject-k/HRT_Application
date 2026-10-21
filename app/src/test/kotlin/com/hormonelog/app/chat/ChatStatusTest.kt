package com.hormonelog.app.chat

import com.hormonelog.app.analysis.Analysis
import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ChatStatusTest {
    private val now = Instant.parse("2026-10-19T06:30:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private fun query(action: String, s: AppState = AppState()) =
        ChatCommands.apply(s, ChatCommand(action), listOf("현재 상태 알려줘"), UUID.randomUUID(), now, zone)
    private fun dose(drug: Drug, amount: Double, at: Instant, status: DoseStatus = DoseStatus.ADMINISTERED) =
        DoseEvent(UUID.randomUUID(), at, zone.id, drug, if (drug == Drug.ESTRADIOL_VALERATE) Route.IM_INJECTION else Route.ORAL,
            amount, DoseUnit.MG, amount, status)
    private fun lab(at: Instant?, analyte: Analyte, value: Double, unit: String, baseline: Boolean = false) =
        LabResult(UUID.randomUUID(), at, zone.id, Assay.UNKNOWN,
            listOf(LabAnalyteValue(analyte, value, unit, LabAnalyteValue.canonical(analyte, value, unit))), isBaseline = baseline)

    @Test fun emptyStateReportsMissingDataWithoutInventingValuesOrWriting() {
        val result = query("status")
        assertNull(result.next)
        assertTrue(result.message, result.message.contains("2026-10-19 15:30"))
        assertTrue(result.message, result.message.contains("E2: 추정 불가"))
        assertTrue(result.message, result.message.contains("투약 기록 없음"))
        assertTrue(result.message, result.message.contains("검사 기록 없음"))
        assertTrue(result.message, result.message.contains("투약 일정 없음"))
    }

    @Test fun estimatesMatchExistingAnalysisAndAreSeparateFromReportedLabUnits() {
        val s = AppState(doses = listOf(dose(Drug.ESTRADIOL_VALERATE, 5.0, now.minusSeconds(86400)),
            dose(Drug.PROGESTERONE_MICRONIZED, 200.0, now.minusSeconds(7200))),
            labs = listOf(lab(now.minusSeconds(3600), Analyte.ESTRADIOL, 367.13, "pmol/L")))
        val result = query("status", s)
        val calibration = Analysis.calibrate(s.doses, s.labs, s.settings.gonadalStatus)
        val e2 = Analysis.e2At(s.doses, s.regimens, calibration, now, now, zone)!!
        val p4 = Analysis.p4At(s.doses, now)!!
        assertTrue(result.message, result.message.contains("E2: ${e2.median.toInt()} pg/mL"))
        assertTrue(result.message, result.message.contains(String.format(java.util.Locale.ROOT, "%.2f ng/mL", p4.median)))
        assertTrue(result.message, result.message.contains("367.13 pmol/L"))
        assertTrue(result.message, result.message.contains("실측 검사"))
        assertTrue(result.message, result.message.contains("실측 아님"))
        assertTrue(result.message, result.message.contains("체내 총농도 아님"))
        assertNull(result.next)
    }

    @Test fun hiddenTotalTAndUnsupportedP4DoNotTurnIntoNumericPredictions() {
        val s = AppState(settings = AppSettings(gonadalStatus = GonadalStatus.DECLINED),
            doses = listOf(dose(Drug.ESTRADIOL_VALERATE, 5.0, now.minusSeconds(86400)),
                dose(Drug.PROGESTERONE_MICRONIZED, 50.0, now.minusSeconds(3600))))
        val text = query("status", s).message
        assertTrue(text, text.contains("Total T: 비공개 설정으로 표시하지 않음"))
        assertTrue(text, text.contains("P4 복용분: 추정 불가"))
    }

    @Test fun datedLabsSortByCollectionWhileUnknownTimeAndBaselineStayExplicit() {
        val old = lab(now.minusSeconds(86400), Analyte.TOTAL_TESTOSTERONE, 0.25, "ng/mL", baseline = true)
        val recent = lab(now.minusSeconds(3600), Analyte.PROGESTERONE, 3.18, "nmol/L")
        val unknown = lab(null, Analyte.ESTRADIOL, 99.0, "pg/mL")
        val future = lab(now.plusSeconds(86400), Analyte.ESTRADIOL, 999.0, "pg/mL")
        val s = AppState(labs = listOf(old, unknown, future, recent))
        val text = query("lab_results", s).message
        assertTrue(text, text.indexOf("3.18 nmol/L") < text.indexOf("0.25 ng/mL"))
        assertTrue(text, text.contains("채혈 시각 모름"))
        assertTrue(text, text.contains("기저 검사"))
        assertFalse(text.contains("999"))
        assertNull(query("lab_results", s).next)
    }

    @Test fun recentActualDoseAndScheduleNeverReportSkippedOrFutureDosesAsTaken() {
        val taken = dose(Drug.ESTRADIOL_VALERATE, 5.0, now.minusSeconds(86400))
        val skipped = dose(Drug.CYPROTERONE, 123.0, now.minusSeconds(60), DoseStatus.SKIPPED)
        val future = dose(Drug.PROGESTERONE_MICRONIZED, 300.0, now.plusSeconds(86400))
        val plan = Regimen(UUID.randomUUID(), taken.drug, taken.route, 5.0, DoseUnit.MG, 7, taken.occurredAt, null)
        val oral = Regimen(UUID.randomUUID(), Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 100.0, DoseUnit.MG, 1, now.minusSeconds(3600), null)
        val text = query("status", AppState(doses = listOf(future, skipped, taken), regimens = listOf(plan, oral))).message
        assertTrue(text, text.contains("2026-10-18 15:30"))
        assertTrue(text, text.contains("2026-10-25 15:30"))
        val actual = text.substringAfter("최근 실제 투약").substringBefore("다음 투약")
        assertFalse(actual.contains("123 mg"))
        assertFalse(actual.contains("300 mg"))
    }

    @Test fun newReadActionsAreAcceptedByStructuredProtocol() {
        for (action in listOf("status", "lab_results"))
            assertEquals(action, ChatProtocol.decode("""{"structured_output":{"action":"$action"}}""").action)
    }
}
