package com.hormonelog.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegimenTest {
    private val now = Instant.parse("2026-08-27T00:00:00Z")
    private val seoul = ZoneId.of("Asia/Seoul")

    private fun regimen(everyDays: Int, startDaysAgo: Long, endAt: Instant? = null) = Regimen(
        id = UUID.randomUUID(),
        drug = Drug.ESTRADIOL_VALERATE,
        route = Route.IM_INJECTION,
        amountEntered = 10.0,
        enteredUnit = DoseUnit.MG,
        everyDays = everyDays,
        startAt = now.minus(startDaysAgo, ChronoUnit.DAYS),
        endAt = endAt,
    )

    private fun at(text: String): Instant = ZonedDateTime.parse("$text[Asia/Seoul]").toInstant()

    private fun plan(
        start: String,
        everyDays: Int = 7,
        days: Collection<DayOfWeek> = emptyList(),
        timeMinutes: Int? = 9 * 60,
        end: String? = null,
    ) = Regimen(
        id = UUID.randomUUID(),
        drug = Drug.ESTRADIOL_VALERATE,
        route = Route.IM_INJECTION,
        amountEntered = 5.0,
        enteredUnit = DoseUnit.MG,
        everyDays = everyDays,
        startAt = at(start),
        endAt = end?.let(::at),
        weekdays = Regimen.maskOf(days),
        timeMinutes = timeMinutes,
    )

    private fun taken(whenText: String) = DoseEvent(
        id = UUID.randomUUID(),
        occurredAt = at(whenText),
        sourceZoneId = "Asia/Seoul",
        drug = Drug.ESTRADIOL_VALERATE,
        route = Route.IM_INJECTION,
        amountEntered = 5.0,
        enteredUnit = DoseUnit.MG,
        normalizedMilligrams = 5.0,
        status = DoseStatus.ADMINISTERED,
    )

    @Test
    fun biweeklyRegimenOverSixtyDaysExpandsToFiveEvents() {
        val events = Regimen.expand(regimen(everyDays = 14, startDaysAgo = 60), now)
        assertEquals(5, events.size) // day -60, -46, -32, -18, -4
        assertTrue(events.all { it.status == DoseStatus.ADMINISTERED })
        assertEquals(10.0, events.first().normalizedMilligrams!!, 0.0)
    }

    @Test
    fun dailyRegimenOverSixtyDaysExpandsToSixtyOneEvents() {
        val events = Regimen.expand(regimen(everyDays = 1, startDaysAgo = 60), now)
        assertEquals(61, events.size) // inclusive of both ends
    }

    @Test
    fun endDateStopsExpansionEarly() {
        val events = Regimen.expand(
            regimen(everyDays = 1, startDaysAgo = 60, endAt = now.minus(50, ChronoUnit.DAYS)),
            now,
        )
        assertEquals(11, events.size)
    }

    @Test
    fun expandedEventsAreMarkedAsComingFromTheSchedule() {
        val events = Regimen.expand(regimen(everyDays = 7, startDaysAgo = 14), now)
        assertTrue(events.all { it.source == RecordSource.SCHEDULE })
    }

    @Test
    fun weeklyPlanFallsOnItsWeekdayAtTheChosenTime() {
        val saturdays = plan("2026-09-19T09:00:00+09:00", days = listOf(DayOfWeek.SATURDAY))
        val events = Regimen.expand(saturdays, at("2026-10-04T12:00:00+09:00"), seoul)
        assertEquals(listOf("2026-09-19", "2026-09-26", "2026-10-03"), events.map { it.occurredAt.atZone(seoul).toLocalDate().toString() })
        assertTrue(events.all { it.occurredAt.atZone(seoul).hour == 9 })
    }

    @Test
    fun twicePerWeekPlanUsesBothWeekdays() {
        val p = plan("2026-09-21T08:30:00+09:00", days = listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), timeMinutes = 8 * 60 + 30)
        val events = Regimen.expand(p, at("2026-10-04T12:00:00+09:00"), seoul)
        assertEquals(
            listOf("2026-09-21", "2026-09-24", "2026-09-28", "2026-10-01"),
            events.map { it.occurredAt.atZone(seoul).toLocalDate().toString() },
        )
    }

    @Test
    fun planTimeOverridesTheTimeOfStart() {
        val p = plan("2026-09-19T14:10:00+09:00", everyDays = 1, timeMinutes = 9 * 60)
        val first = Regimen.expand(p, at("2026-09-22T00:00:00+09:00"), seoul).map { it.occurredAt.atZone(seoul).toLocalTime().toString() }
        // The first 09:00 is before the start instant, so the plan begins the day after.
        assertEquals(listOf("09:00", "09:00"), first)
    }

    @Test
    fun anEarlyRealDoseCoversTheNextScheduledOne() {
        val saturdays = plan("2026-09-19T09:00:00+09:00", days = listOf(DayOfWeek.SATURDAY))
        val doses = listOf(taken("2026-09-19T09:00:00+09:00"), taken("2026-09-26T09:00:00+09:00"), taken("2026-10-02T09:00:00+09:00"))
        val ahead = Regimen.expected(saturdays, at("2026-10-02T12:00:00+09:00"), at("2026-10-20T00:00:00+09:00"), doses, seoul)
        // Friday's injection already stands for Saturday 10-03.
        assertEquals(listOf("2026-10-10", "2026-10-17"), ahead.map { it.atZone(seoul).toLocalDate().toString() })
    }

    @Test
    fun everyNDaysPlanCountsOnFromTheLastRealDose() {
        val p = plan("2026-09-01T09:00:00+09:00", everyDays = 7, timeMinutes = null)
        val doses = listOf(taken("2026-09-01T09:00:00+09:00"), taken("2026-09-08T14:10:00+09:00"))
        val next = Regimen.nextDue(p, at("2026-09-10T00:00:00+09:00"), doses, seoul)
        assertEquals(at("2026-09-15T09:00:00+09:00"), next)
    }

    @Test
    fun anOverduePlanStillReportsWhatIsOwed() {
        val p = plan("2026-09-01T09:00:00+09:00", everyDays = 7, timeMinutes = null)
        val doses = listOf(taken("2026-09-08T09:00:00+09:00"))
        val next = Regimen.nextDue(p, at("2026-09-30T00:00:00+09:00"), doses, seoul)
        assertEquals(at("2026-09-15T09:00:00+09:00"), next)
    }

    @Test
    fun aPlanWithNoDoseYetDueAtItsStart() {
        val p = plan("2026-10-10T09:00:00+09:00", everyDays = 7, timeMinutes = null)
        assertEquals(at("2026-10-10T09:00:00+09:00"), Regimen.nextDue(p, at("2026-10-04T00:00:00+09:00"), emptyList(), seoul))
    }

    @Test
    fun aFinishedPlanOwesNothing() {
        val p = plan("2026-09-01T09:00:00+09:00", everyDays = 7, timeMinutes = null, end = "2026-09-20T00:00:00+09:00")
        val doses = listOf(taken("2026-09-15T09:00:00+09:00"))
        assertNull(Regimen.nextDue(p, at("2026-10-04T00:00:00+09:00"), doses, seoul))
    }
}
