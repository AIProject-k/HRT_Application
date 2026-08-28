package com.hormonelog.core.domain

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegimenTest {
    private val now = Instant.parse("2026-08-27T00:00:00Z")

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
}
