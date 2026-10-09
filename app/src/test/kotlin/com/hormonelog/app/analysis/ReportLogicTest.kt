package com.hormonelog.app.analysis

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.daily
import com.hormonelog.app.dailyDoses
import com.hormonelog.app.dose
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.cadenceLabel
import com.hormonelog.app.lab
import com.hormonelog.app.saturdayDoses
import com.hormonelog.app.settings
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.ReportPart
import com.hormonelog.app.state.ReportPeriod
import com.hormonelog.app.state.ReportUi
import com.hormonelog.app.weekly
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.VisitMemo
import com.hormonelog.core.evidence.EvidenceBundleV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class ReportLogicTest {
    private val now = at(2026, 10, 5, 16, 30)
    private val fmt = Fmt(SEOUL, clock24 = false, now = now)

    private val running = weekly(at(2026, 9, 19))
    private val earlier = weekly(at(2026, 3, 7), amount = 4.0, end = at(2026, 9, 18))
    private val tablets = daily(at(2026, 3, 1, 8, 30))

    private val state = AppState(
        settings = settings.copy(gonadalStatus = GonadalStatus.INTACT),
        doses = (
            saturdayDoses(at(2026, 3, 7), at(2026, 10, 3)) +
                dailyDoses(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 4), missed = setOf(LocalDate.of(2026, 10, 2))) +
                listOf(
                    dose(at(2026, 9, 14, 21), drug = Drug.ESTRADIOL_GEL, route = Route.GEL, amount = 1.5),
                    dose(at(2026, 9, 21, 21), drug = Drug.ESTRADIOL_GEL, route = Route.GEL, amount = 1.5),
                )
            ).sortedBy { it.occurredAt },
        labs = listOf(
            lab(at(2026, 2, 20, 10), e2 = 28.0, tt = 520.0, baseline = true),
            lab(at(2026, 4, 10, 10), e2 = 95.0),
            lab(at(2026, 6, 19, 11), e2 = 162.0, tt = 48.0),
            lab(null, e2 = 410.0),
            lab(at(2026, 8, 14, 11), e2 = 188.0, tt = 36.0),
            lab(at(2026, 9, 22, 14), e2 = 168.0, tt = 41.0),
        ),
        regimens = listOf(running, earlier, tablets),
        memos = listOf(
            VisitMemo(UUID.randomUUID(), LocalDate.of(2026, 9, 18), "Dose change", "EV 4 -> 5 mg", prescription = "EV 5 mg / 7d"),
            VisitMemo(UUID.randomUUID(), LocalDate.of(2026, 8, 14), "Potassium normal", "Keep CPA"),
            VisitMemo(UUID.randomUUID(), LocalDate.of(2026, 6, 19), "First lab", "Draw before the next injection"),
        ),
    )

    private val everything = ReportUi(include = ReportPart.entries.toSet())

    private fun build(ui: ReportUi = everything, s: AppState = state) = ReportLogic.build(s.copy(report = ui), fmt)

    @Test
    fun theHeaderNamesThePeriodTheDayItWasMadeAndTheEvidenceUsed() {
        val d = build()
        assertEquals("2026년 7월 7일 – 10월 5일 (3개월)", d.periodText)
        assertEquals("만든 날 2026년 10월 5일", d.createdText)
        assertEquals("근거 세트 ${EvidenceBundleV1.bundle.version}", d.modelText)
    }

    @Test
    fun otherPeriodsAreNamedToo() {
        assertEquals("2026년 9월 5일 – 10월 5일 (1개월)", build(everything.copy(period = ReportPeriod.MONTH)).periodText)
        assertEquals("2026년 4월 8일 – 10월 5일 (6개월)", build(everything.copy(period = ReportPeriod.HALF)).periodText)
        // "전체" starts at the earliest record of any kind: here the baseline lab of 20 February.
        assertEquals("2026년 2월 20일 – 10월 5일 (전체)", build(everything.copy(period = ReportPeriod.ALL)).periodText)
    }

    @Test
    fun theScheduleListsOnlyRunningPlansWithTheirDrugRouteAndCadence() {
        val rows = build().schedule!!
        assertEquals(2, rows.size)
        val ev = rows.single { it.drug == "에스트라디올 발레레이트" }
        assertEquals("주사 5 mg", ev.routeDose)
        assertEquals("7일마다 · 토", ev.cadence)
        assertEquals("2026년 9월 19일", ev.start)
        val cpa = rows.single { it.drug == "사이프로테론" }
        assertEquals("경구 12.5 mg", cpa.routeDose)
        assertEquals("매일", cpa.cadence)
    }

    @Test
    fun theLabTableHoldsE2LabsOfThePeriodAndTheOneWithNoTimeNeverLeavesOut() {
        val labs = build().labs!!
        assertEquals(listOf("8월 14일", "9월 22일", "시각 모름"), labs.map { it.date })
        val last = labs[1]
        assertEquals("168", last.measured)
        assertTrue(last.expected.matches(Regex("""\d+ \(\d+–\d+\)""")))
        assertTrue(last.diff.matches(Regex("""[+-]\d+%""")))
        val unknown = labs[2]
        assertEquals("410", unknown.measured)
        assertEquals("—", unknown.expected)
        assertEquals("—", unknown.diff)
        assertEquals("시각 없음", unknown.status)
    }

    @Test
    fun totalTIsListedBelowTheTableWithItsOwnUnitAndNeverTheBaseline() {
        val note = build().labsNote!!
        assertTrue(note, note.startsWith("E2 단위 pg/mL"))
        assertTrue(note, note.contains("9월 22일 41 ng/dL"))
        assertTrue(note, note.contains("8월 14일 36 ng/dL"))
        assertFalse(note, note.contains("520"))
    }

    @Test
    fun adherenceCountsWhatWasTakenPerKindAndNamesASingleMissedDay() {
        val lines = build().adherence!!
        assertTrue(lines.toString(), "주사 13 / 13회 (100%)" in lines)
        assertTrue(lines.toString(), "경구 45 / 46회 (98%) · 놓침 10월 2일" in lines)
        assertTrue(lines.toString(), "젤 2 / 2회 (100%)" in lines)
    }

    @Test
    fun theModelLinesSayWhatWasCalibratedAndWhatWasLeftOut() {
        val lines = build().model!!.joinToString("\n")
        assertTrue(lines, lines.contains("주사 E2: 검사"))
        assertTrue(lines, lines.contains("젤 기록 2건은 곡선 계산에서 제외"))
        val declined = build(s = state.copy(settings = state.settings.copy(gonadalStatus = GonadalStatus.DECLINED))).model!!.joinToString("\n")
        assertTrue(declined, declined.contains("Total T 곡선은 그리지 않았어요"))
    }

    @Test
    fun withNothingToCalibrateTheModelSaysItUsedTheLiteratureAverage() {
        val bare = AppState(settings = settings, doses = listOf(dose(at(2026, 10, 3, 9))))
        assertEquals(listOf("보정 없이 문헌 평균으로 그렸어요"), build(s = bare).model)
    }

    @Test
    fun memosAreOnlyThoseOfThePeriodAndTheirTextIsShortened() {
        val memos = build().memos!!
        assertEquals(listOf("9월 18일", "8월 14일"), memos.map { it.date })
        assertEquals("EV 5 mg / 7d", memos.first().prescription)
        val long = state.copy(memos = listOf(VisitMemo(UUID.randomUUID(), LocalDate.of(2026, 9, 18), "T", "가".repeat(500))))
        assertEquals(240, build(s = long).memos!!.single().body.length)
    }

    @Test
    fun aPartTheUserLeftOutIsNullNotEmpty() {
        val d = build(ReportUi(include = setOf(ReportPart.CHART)))
        assertNull(d.schedule)
        assertNull(d.labs)
        assertNull(d.labsNote)
        assertNull(d.adherence)
        assertNull(d.model)
        assertNull(d.memos)
        assertNull(d.modelText)
        assertTrue(d.chartWanted)
        assertNotNull(d.chart)

        val noChart = build(ReportUi(include = setOf(ReportPart.LABS)))
        assertFalse(noChart.chartWanted)
        assertNull(noChart.chart)
    }

    @Test
    fun theShareImageGetsTheNewestRealLabBesideWhatTheModelExpected() {
        val h = build().highlight!!
        assertEquals("9월 22일", h.dateText)
        assertEquals("168", h.measured)
        assertEquals("pg/mL", h.unit)
        assertNotNull(h.expected)
        assertTrue(h.range!!.matches(Regex("""\d+–\d+""")))
    }

    @Test
    fun withoutAnyLabThereIsNothingToHighlight() {
        assertNull(build(s = state.copy(labs = emptyList())).highlight)
    }

    @Test
    fun anEmptyAppStillMakesAValidReport() {
        val d = build(s = AppState(settings = settings))
        assertTrue(d.schedule!!.isEmpty())
        assertTrue(d.labs!!.isEmpty())
        assertEquals(listOf("기록된 투약이 없어요"), d.adherence)
        assertNull(d.chart)
        assertTrue(d.chartWanted)
    }

    @Test
    fun theChartCoversThePeriodAndEndsToday() {
        val chart = build().chart!!
        assertEquals(now, chart.to)
        assertEquals(now.minusSeconds(90L * 86_400L), chart.from)
        assertFalse(chart.isTotalT)
        assertTrue(chart.labs.any { it.at == at(2026, 9, 22, 14) })
    }

    // ── how a plan is worded ──────────────────────────────────

    @Test
    fun cadenceIsWordedLikeTheScheduleList() {
        assertEquals("7일마다 · 토", running.cadenceLabel())
        assertEquals("매일", tablets.cadenceLabel())
        assertEquals("14일마다", tablets.copy(everyDays = 14).cadenceLabel())
        assertEquals("주 2회 · 월·목", running.copy(weekdays = Regimen.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))).cadenceLabel())
        val patch = running.copy(route = Route.PATCH, drug = Drug.ESTRADIOL_PATCH, amountEntered = 50.0, enteredUnit = DoseUnit.UG_PER_DAY)
        assertEquals("주 1회 교체 · 토", patch.cadenceLabel())
        assertEquals("주 2회 교체 · 월·목", patch.copy(weekdays = Regimen.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), everyDays = 3).cadenceLabel())
    }
}
