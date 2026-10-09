package com.hormonelog.app

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** The zone every test works in; Korea has no daylight saving, so wall-clock arithmetic is exact. */
internal val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

internal fun at(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0): Instant = LocalDateTime.of(y, m, d, h, min).atZone(SEOUL).toInstant()

/** Local midnight of a day, as the epoch millis the date pickers hand over. */
internal fun dayMillis(y: Int, m: Int, d: Int): Long = LocalDateTime.of(y, m, d, 0, 0).atZone(SEOUL).toInstant().toEpochMilli()

internal val settings = AppSettings(onboardingDone = true, gonadalStatus = GonadalStatus.INTACT)

internal fun dose(
    at: Instant,
    drug: Drug = Drug.ESTRADIOL_VALERATE,
    route: Route = Route.IM_INJECTION,
    amount: Double = 5.0,
    status: DoseStatus = DoseStatus.ADMINISTERED,
    source: RecordSource = RecordSource.MANUAL,
): DoseEvent {
    val unit = if (route == Route.PATCH) DoseUnit.UG_PER_DAY else DoseUnit.MG
    return DoseEvent(
        id = UUID.randomUUID(), occurredAt = at, sourceZoneId = SEOUL.id, drug = drug, route = route,
        amountEntered = amount, enteredUnit = unit, normalizedMilligrams = DoseEvent.normalizeMilligrams(amount, unit),
        status = status, source = source,
    )
}

/** Once a week on [day] at 09:00, from [start]. */
internal fun weekly(
    start: Instant,
    day: DayOfWeek = DayOfWeek.SATURDAY,
    drug: Drug = Drug.ESTRADIOL_VALERATE,
    route: Route = Route.IM_INJECTION,
    amount: Double = 5.0,
    end: Instant? = null,
): Regimen = Regimen(
    id = UUID.randomUUID(), drug = drug, route = route, amountEntered = amount, enteredUnit = DoseUnit.MG,
    everyDays = 7, startAt = start, endAt = end, active = end == null,
    weekdays = Regimen.maskOf(listOf(day)), timeMinutes = 9 * 60,
)

/** Every day at [timeMinutes] after midnight. */
internal fun daily(
    start: Instant,
    drug: Drug = Drug.CYPROTERONE,
    route: Route = Route.ORAL,
    amount: Double = 12.5,
    timeMinutes: Int = 8 * 60 + 30,
): Regimen = Regimen(
    id = UUID.randomUUID(), drug = drug, route = route, amountEntered = amount, enteredUnit = DoseUnit.MG,
    everyDays = 1, startAt = start, endAt = null, timeMinutes = timeMinutes,
)

internal fun lab(at: Instant?, e2: Double? = null, tt: Double? = null, baseline: Boolean = false): LabResult = LabResult(
    id = UUID.randomUUID(),
    collectedAt = at,
    sourceZoneId = SEOUL.id,
    assay = com.hormonelog.core.domain.Assay.UNKNOWN,
    analytes = buildList {
        if (e2 != null) add(LabAnalyteValue(Analyte.ESTRADIOL, e2, "pg/mL", LabAnalyteValue.canonical(Analyte.ESTRADIOL, e2, "pg/mL")))
        if (tt != null) add(LabAnalyteValue(Analyte.TOTAL_TESTOSTERONE, tt, "ng/dL", LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, tt, "ng/dL")))
    },
    isBaseline = baseline,
)

/** A weekly injection history: one dose every Saturday from [from] up to and including [until] (09:00). */
internal fun saturdayDoses(from: Instant, until: Instant, amount: Double = 5.0): List<DoseEvent> {
    val out = ArrayList<DoseEvent>()
    var t = from
    while (!t.isAfter(until)) {
        out += dose(t, amount = amount)
        t = t.atZone(SEOUL).plusDays(7).toInstant()
    }
    return out
}

/** One tablet a day at 08:30, from [from] to [until] (dates inclusive). */
internal fun dailyDoses(
    from: java.time.LocalDate,
    until: java.time.LocalDate,
    drug: Drug = Drug.CYPROTERONE,
    route: Route = Route.ORAL,
    amount: Double = 12.5,
    missed: Set<java.time.LocalDate> = emptySet(),
): List<DoseEvent> = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }.map { day ->
    dose(
        day.atTime(8, 30).atZone(SEOUL).toInstant(), drug = drug, route = route, amount = amount,
        status = if (day in missed) DoseStatus.SKIPPED else DoseStatus.ADMINISTERED, source = RecordSource.SCHEDULE,
    )
}.toList()
