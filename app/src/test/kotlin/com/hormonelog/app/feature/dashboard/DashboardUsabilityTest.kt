package com.hormonelog.app.feature.dashboard

import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class DashboardUsabilityTest {
    private val now = Instant.parse("2026-10-03T03:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")

    private fun dose(at: Instant, drug: Drug = Drug.ESTRADIOL_VALERATE, route: Route = drug.allowedRoutes.first()) =
        DoseEvent(UUID.randomUUID(), at, zone.id, drug, route, 5.0, DoseUnit.MG, 5.0, DoseStatus.ADMINISTERED, null)

    private fun regimen(start: Instant, drug: Drug = Drug.ESTRADIOL_VALERATE, days: Int = 7) =
        Regimen(UUID.randomUUID(), drug, drug.allowedRoutes.first(), 5.0, DoseUnit.MG, days, start, null)

    private fun lab(at: Instant?, unit: String = "pmol/L", value: Double = 367.13) =
        LabResult(UUID.randomUUID(), at, zone.id, Assay.UNKNOWN, listOf(
            LabAnalyteValue(Analyte.ESTRADIOL, value, unit, LabAnalyteValue.canonical(Analyte.ESTRADIOL, value, unit)),
        ))

    @Test
    fun timelineKeepsTheReportedValueAndUnit() {
        val state = DashboardState(labs = listOf(lab(now)))
        assertEquals("E2 367.13 pmol/L", timelineEntries(state, now, zone, false).single().subtitle)
    }

    @Test
    fun chartReadoutUsesTheSameConvertedValueAsTheMarker() {
        val result = nearestLabWithin(DashboardState(labs = listOf(lab(now))), now, HormoneSeries.E2, 24)!!
        assertEquals(100.0, result.first, 0.0001)
        assertTrue(result.second.endsWith("100 pg/mL"))
    }

    @Test
    fun chartDoesNotRelabelAnUnknownUnitAsPgMl() {
        assertNull(nearestLabWithin(DashboardState(labs = listOf(lab(now, "unknown"))), now, HormoneSeries.E2, 24))
    }

    @Test
    fun malformedInputCannotBeSavedEvenWhenTheOtherAnalyteIsValid() {
        for (invalid in listOf(".", "1..2", "NaN", "Infinity", "-1")) {
            val draft = LabDraft(e2 = invalid, tt = "20")
            assertFalse("E2=$invalid", draft.canSave)
            val state = DashboardState(labDraft = draft)
            assertSame(state, DashboardReducer.saveLab(state, now))
            assertFalse(LabDraft(e2 = "120", tt = invalid).canSave)
        }
    }

    @Test
    fun blankAndValidInputsRemainOptionalAndZeroIsAllowed() {
        assertFalse(LabDraft().canSave)
        assertTrue(LabDraft(e2 = "120.5").canSave)
        assertTrue(LabDraft(tt = "0").canSave)
        assertTrue(LabDraft(e2 = "120", tt = "20").canSave)
    }

    @Test
    fun numpadAllowsOnlyOneDecimalPoint() {
        var state = DashboardState(labDraft = LabDraft(focus = LabField.E2))
        state = DashboardReducer.pressKey(state, ".")
        assertEquals("0.", state.labDraft.e2)
        state = DashboardReducer.pressKey(state, "5")
        state = DashboardReducer.pressKey(state, ".")
        assertEquals("0.5", state.labDraft.e2)
    }

    @Test
    fun datesWithTheSameMonthAndDayInDifferentYearsStaySeparate() {
        val prior = Instant.parse("2025-10-03T03:00:00Z")
        val state = DashboardState(doses = listOf(dose(now), dose(prior)), labs = listOf(lab(now), lab(prior)))
        val groups = timelineGroups(timelineEntries(state, now, zone, false), TimelineFilter.ALL)
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].items.size)
        assertTrue(groups[0].dateLabel.startsWith("2026년 10월 3일"))
        assertTrue(groups[1].dateLabel.startsWith("2025년 10월 3일"))
    }

    @Test
    fun unknownCollectionTimeStillHasItsOwnGroup() {
        val state = DashboardState(labs = listOf(lab(null), lab(now)))
        val groups = timelineGroups(timelineEntries(state, now, zone, false), TimelineFilter.LAB)
        assertEquals("시간 미상", groups.last().dateLabel)
    }

    @Test
    fun noScheduleNeverAssumesWeeklyDosing() {
        assertEquals("일정 미설정", homeSummary(DashboardState(doses = listOf(dose(now))), now).nextDose)
    }

    @Test
    fun nextDoseUsesOnlyTheMatchingDrugAndRoute() {
        val recorded = now.minusSeconds(2 * 86400L)
        val state = DashboardState(
            doses = listOf(dose(recorded), dose(now, Drug.ESTRADIOL_TABLET), dose(now, route = Route.SC_INJECTION)),
            regimens = listOf(regimen(now.minusSeconds(10 * 86400L))),
        )
        assertEquals("5일 뒤", homeSummary(state, now).nextDose)
    }

    @Test
    fun multipleSchedulesShowTheEarliestDueDose() {
        val state = DashboardState(
            doses = listOf(dose(now), dose(now.minusSeconds(3 * 86400L), Drug.ESTRADIOL_TABLET)),
            regimens = listOf(regimen(now), regimen(now.minusSeconds(10 * 86400L), Drug.ESTRADIOL_TABLET, 4)),
        )
        assertEquals("1일 뒤", homeSummary(state, now).nextDose)
    }

    @Test
    fun overdueScheduleIsNotShownAsToday() {
        val at = now.minusSeconds(9 * 86400L)
        assertEquals("2일 지남", homeSummary(DashboardState(doses = listOf(dose(at)), regimens = listOf(regimen(at))), now).nextDose)
    }

    @Test
    fun sameDayUpcomingAndOverdueTimesAreDistinct() {
        assertEquals("오늘", homeSummary(DashboardState(regimens = listOf(regimen(now.plusSeconds(3600)))), now).nextDose)
        assertEquals("예정 시각 지남", homeSummary(DashboardState(regimens = listOf(regimen(now.minusSeconds(3600)))), now).nextDose)
    }

    @Test
    fun futureScheduleUsesItsStartWithoutAnActualDose() {
        assertEquals("3일 뒤", homeSummary(DashboardState(regimens = listOf(regimen(now.plusSeconds(3 * 86400L)))), now).nextDose)
    }

    @Test
    fun finishedAndInactiveSchedulesAreExcluded() {
        val schedule = regimen(now.minusSeconds(10 * 86400L))
        assertEquals("일정 미설정", homeSummary(DashboardState(regimens = listOf(schedule.copy(active = false))), now).nextDose)
        assertEquals("일정 미설정", homeSummary(DashboardState(regimens = listOf(schedule.copy(endAt = now.minusSeconds(86400)))), now).nextDose)
    }

    @Test
    fun futureSkippedAndPreScheduleDosesDoNotMoveTheDueTime() {
        val schedule = regimen(now.plusSeconds(2 * 86400L))
        val state = DashboardState(
            regimens = listOf(schedule),
            doses = listOf(dose(now.minusSeconds(86400)), dose(now.plusSeconds(4 * 86400L)), dose(now).copy(status = DoseStatus.SKIPPED)),
        )
        assertEquals("2일 뒤", homeSummary(state, now).nextDose)
    }
}
