package com.hormonelog.core.domain

import java.time.Instant
import java.util.UUID

/**
 * A repeating plan (설계서 §3.1). Regimens drive *forecasting*; historical
 * reconstruction still uses actual [DoseEvent]s. When a user backfills a past
 * regimen, [expand] turns the elapsed portion into concrete administration events
 * (they really happened) and the regimen itself covers the future.
 */
data class Regimen(
    val id: UUID,
    val drug: Drug,
    val route: Route,
    val amountEntered: Double,
    val enteredUnit: DoseUnit,
    /** Interval in days between administrations. 1 = daily. */
    val everyDays: Int,
    val startAt: Instant,
    /** null = ongoing. */
    val endAt: Instant?,
    val active: Boolean = true,
) {
    companion object {
        /** Administration events from [startAt] to min(endAt, until) at [everyDays] spacing. */
        fun expand(regimen: Regimen, until: Instant): List<DoseEvent> {
            val stop = regimen.endAt?.let { if (it.isBefore(until)) it else until } ?: until
            if (regimen.startAt.isAfter(stop)) return emptyList()
            val step = regimen.everyDays.coerceAtLeast(1).toLong() * 86_400L
            val out = ArrayList<DoseEvent>()
            var t = regimen.startAt
            var guard = 0
            while (!t.isAfter(stop) && guard < 10_000) {
                out += DoseEvent(
                    id = UUID.randomUUID(),
                    occurredAt = t,
                    sourceZoneId = "UTC",
                    drug = regimen.drug,
                    route = regimen.route,
                    amountEntered = regimen.amountEntered,
                    enteredUnit = regimen.enteredUnit,
                    normalizedMilligrams = DoseEvent.normalizeMilligrams(regimen.amountEntered, regimen.enteredUnit),
                    status = DoseStatus.ADMINISTERED,
                    note = null,
                )
                t = t.plusSeconds(step)
                guard++
            }
            return out
        }
    }
}
