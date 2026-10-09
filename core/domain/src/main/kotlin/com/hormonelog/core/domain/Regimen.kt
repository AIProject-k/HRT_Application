package com.hormonelog.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A repeating plan (설계서 §3.1). Regimens drive *forecasting*; historical
 * reconstruction still uses actual [DoseEvent]s. When a user backfills a past
 * regimen, [expand] turns the elapsed portion into concrete administration events
 * (they really happened) and the regimen itself covers the future.
 *
 * Two shapes: every [everyDays] days from [startAt], or — when [weekdays] is not
 * empty — on those weekdays each week (once a week, twice a week …).
 */
data class Regimen(
    val id: UUID,
    val drug: Drug,
    val route: Route,
    val amountEntered: Double,
    val enteredUnit: DoseUnit,
    /** Interval in days between administrations. 1 = daily. Ignored when [weekdays] is set. */
    val everyDays: Int,
    val startAt: Instant,
    /** null = ongoing. */
    val endAt: Instant?,
    val active: Boolean = true,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 = a plain every-N-days plan. */
    val weekdays: Int = 0,
    /** Local wall-clock time as minutes after midnight; null = the time of day of [startAt]. */
    val timeMinutes: Int? = null,
    /** Only for a patch: how often it is changed. */
    val patchCycle: PatchCycle? = null,
) {
    val isWeekdayPlan: Boolean get() = weekdays != 0

    /** µg/day for a patch plan, or null when this is not one. */
    val patchMicrogramsPerDay: Double? get() = DoseEvent.patchMicrograms(amountEntered, enteredUnit)

    /** Whether the plan is switched on and has not ended by [instant]. */
    fun isRunningAt(instant: Instant): Boolean = active && (endAt == null || endAt.isAfter(instant))

    val weekdaySet: Set<DayOfWeek>
        get() = DayOfWeek.entries.filter { weekdays and (1 shl (it.value - 1)) != 0 }.toSet()

    /** Average days between two doses; a dose closer than half of this already covers an occurrence. */
    val intervalDays: Double
        get() = if (isWeekdayPlan) 7.0 / Integer.bitCount(weekdays) else everyDays.coerceAtLeast(1).toDouble()

    /** The same drug taken the same way — what makes an actual dose belong to this plan. */
    fun covers(dose: DoseEvent): Boolean =
        dose.drug == drug && dose.route == route && dose.status.wasTaken && !dose.occurredAt.isBefore(startAt)

    companion object {
        private const val MAX_DAYS = 40_000L

        fun maskOf(days: Collection<DayOfWeek>): Int = days.fold(0) { m, d -> m or (1 shl (d.value - 1)) }

        /** Administration events from [startAt] to min(endAt, until) on the plan's own grid. */
        fun expand(
            regimen: Regimen,
            until: Instant,
            zone: ZoneId = ZoneId.systemDefault(),
            source: RecordSource = RecordSource.SCHEDULE,
        ): List<DoseEvent> = gridOccurrences(regimen, until, zone).map { at ->
            DoseEvent(
                id = UUID.randomUUID(),
                occurredAt = at,
                sourceZoneId = zone.id,
                drug = regimen.drug,
                route = regimen.route,
                amountEntered = regimen.amountEntered,
                enteredUnit = regimen.enteredUnit,
                normalizedMilligrams = DoseEvent.normalizeMilligrams(regimen.amountEntered, regimen.enteredUnit),
                status = DoseStatus.ADMINISTERED,
                note = null,
                source = source,
                patchCycle = regimen.patchCycle,
            )
        }

        /** Every grid instant from [Regimen.startAt] to min(endAt, until). */
        fun gridOccurrences(regimen: Regimen, until: Instant, zone: ZoneId = ZoneId.systemDefault()): List<Instant> {
            val stop = regimen.endAt?.let { if (it.isBefore(until)) it else until } ?: until
            if (regimen.startAt.isAfter(stop)) return emptyList()
            val time = timeOfDay(regimen, zone)
            val startDate = regimen.startAt.atZone(zone).toLocalDate()
            val out = ArrayList<Instant>()
            if (regimen.isWeekdayPlan) {
                val lastDate = stop.atZone(zone).toLocalDate()
                var date = startDate
                var guard = 0L
                while (!date.isAfter(lastDate) && guard < MAX_DAYS) {
                    if (regimen.weekdays and (1 shl (date.dayOfWeek.value - 1)) != 0) {
                        val at = date.atTime(time).atZone(zone).toInstant()
                        if (!at.isBefore(regimen.startAt) && !at.isAfter(stop)) out += at
                    }
                    date = date.plusDays(1)
                    guard++
                }
            } else {
                val step = regimen.everyDays.coerceAtLeast(1).toLong()
                var k = 0L
                while (k < MAX_DAYS) {
                    val at = startDate.plusDays(k * step).atTime(time).atZone(zone).toInstant()
                    if (at.isAfter(stop)) break
                    if (!at.isBefore(regimen.startAt)) out += at
                    k++
                }
            }
            return out
        }

        /**
         * Occurrences in (from, until] that are still *expected*, given what was really
         * taken. An every-N-days plan counts on from the last real dose, and any plan
         * skips an occurrence that an early or late real dose already covers — otherwise
         * a Friday injection for a Saturday plan would be forecast twice.
         */
        fun expected(
            regimen: Regimen,
            from: Instant,
            until: Instant,
            doses: List<DoseEvent>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<Instant> {
            val stop = regimen.endAt?.let { if (it.isBefore(until)) it else until } ?: until
            val taken = doses.filter(regimen::covers)
            val lastTaken = taken.maxOfOrNull { it.occurredAt }
            val candidates: List<Instant> = if (!regimen.isWeekdayPlan && lastTaken != null) {
                val time = timeOfDay(regimen, zone)
                val anchor = lastTaken.atZone(zone).toLocalDate()
                val step = regimen.everyDays.coerceAtLeast(1).toLong()
                val out = ArrayList<Instant>()
                var k = 1L
                while (k < MAX_DAYS) {
                    val at = anchor.plusDays(k * step).atTime(time).atZone(zone).toInstant()
                    if (at.isAfter(stop)) break
                    out += at
                    k++
                }
                out
            } else {
                gridOccurrences(regimen, stop, zone)
            }
            val coverMillis = (regimen.intervalDays * 86_400_000.0 / 2).toLong()
            return candidates.filter { at ->
                at.isAfter(from) && !at.isAfter(stop) &&
                    taken.none { kotlin.math.abs(it.occurredAt.toEpochMilli() - at.toEpochMilli()) < coverMillis }
            }
        }

        /**
         * The next dose still owed, which may already be in the past (overdue): the first
         * expected occurrence after the last real dose, or the plan's first one if none yet.
         */
        fun nextDue(
            regimen: Regimen,
            now: Instant,
            doses: List<DoseEvent>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): Instant? {
            if (regimen.endAt?.isBefore(regimen.startAt) == true) return null
            val after = doses.filter(regimen::covers).filter { !it.occurredAt.isAfter(now) }
                .maxOfOrNull { it.occurredAt }
                ?: regimen.startAt.minusMillis(1)
            val horizon = maxOf(now, after).plusSeconds(400L * 86_400L)
            return expected(regimen, after, horizon, doses.filter { !it.occurredAt.isAfter(now) }, zone).firstOrNull()
        }

        private fun timeOfDay(regimen: Regimen, zone: ZoneId): LocalTime =
            regimen.timeMinutes?.let { LocalTime.of((it / 60).coerceIn(0, 23), (it % 60).coerceIn(0, 59)) }
                ?: regimen.startAt.atZone(zone).toLocalTime()
    }
}
