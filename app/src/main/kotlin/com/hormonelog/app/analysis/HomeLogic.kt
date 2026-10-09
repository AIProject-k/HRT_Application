package com.hormonelog.app.analysis

import com.hormonelog.app.feature.flow.ChartDose
import com.hormonelog.app.feature.flow.ChartLab
import com.hormonelog.app.feature.flow.ChartModel
import com.hormonelog.app.feature.flow.TickState
import com.hormonelog.app.state.HormoneSeries
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.isAntiandrogen
import java.time.Instant
import java.time.ZoneId

/** The dose a running plan still owes: when, from which plan, and whether that time has passed. */
data class NextDose(val at: Instant, val regimen: Regimen, val overdue: Boolean)

/** The newest real E2 value, for the "마지막 검사" tile. */
data class LastLab(val lab: LabResult, val reported: Double, val unit: String)

/** Small pure questions the home screen asks of the records. */
object HomeLogic {

    /**
     * The earliest dose any running estrogen plan is waiting for. Anti-androgen plans only count
     * when they are the only ones: a daily tablet would otherwise always be "next".
     */
    fun nextDose(regimens: List<Regimen>, doses: List<DoseEvent>, now: Instant, zone: ZoneId): NextDose? {
        val running = regimens.filter { it.isRunningAt(now) }
        val candidates = running.filter { !it.drug.isAntiandrogen }.ifEmpty { running }
        return candidates
            .mapNotNull { r -> Regimen.nextDue(r, now, doses, zone)?.let { NextDose(it, r, overdue = it.isBefore(now)) } }
            .minByOrNull { it.at }
    }

    fun lastLab(labs: List<LabResult>): LastLab? =
        labs.filter { !it.isBaseline && it.collectedAt != null }
            .sortedByDescending { it.collectedAt }
            .firstNotNullOfOrNull { lab ->
                lab.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }?.let { LastLab(lab, it.reportedValue, it.reportedUnit) }
            }

    /** Days since the last backup for the nudge, -1 for never; null when no nudge is due. */
    fun backupNudgeDays(settings: AppSettings, hasRecords: Boolean, now: Instant): Int? {
        if (!hasRecords) return null
        if (settings.backupBannerHiddenUntilMillis?.let { now.toEpochMilli() < it } == true) return null
        val last = settings.lastBackupAtMillis ?: return -1
        val days = ((now.toEpochMilli() - last) / 86_400_000L).toInt()
        return if (days >= BACKUP_NUDGE_DAYS) days else null
    }

    const val BACKUP_NUDGE_DAYS = 30
}

/** The chart for [series] over [from]..[to]: the curve, real labs, doses taken, missed and planned. */
fun chartModelFor(
    series: HormoneSeries,
    curves: Curves,
    doses: List<DoseEvent>,
    labs: List<LabResult>,
    from: Instant,
    to: Instant,
    now: Instant,
    guide: Boolean,
): ChartModel {
    val isT = series == HormoneSeries.TT
    val analyte = if (isT) Analyte.TOTAL_TESTOSTERONE else Analyte.ESTRADIOL
    val chartLabs = labs.mapNotNull { lab ->
        val at = lab.collectedAt ?: return@mapNotNull null
        val value = lab.analytes.firstOrNull { it.analyte == analyte }?.let(Analysis::canonical) ?: return@mapNotNull null
        val used = curves.calibration.labs[lab.id]?.let { if (isT) it.tt?.used else it.e2?.used } == true
        ChartLab(at, value, used)
    }
    val ticks = doses.filter { it.occurredAt in from..to }.map {
        ChartDose(it.occurredAt, if (it.status == DoseStatus.SKIPPED) TickState.MISSED else TickState.TAKEN, it.drug.isAntiandrogen)
    } + curves.planned.filter { it.occurredAt in from..to }.map { ChartDose(it.occurredAt, TickState.PLANNED, it.drug.isAntiandrogen) }
    return ChartModel(
        curve = if (isT) curves.tt else curves.e2,
        from = from, to = to, now = now,
        labs = chartLabs, doses = ticks, isTotalT = isT,
        guide = if (guide && !isT) GUIDELINE_E2 else null,
    )
}

/** The E2 range printed in clinical guidelines (UCSF), shown only as a labelled reference, never as a target. */
val GUIDELINE_E2: ClosedFloatingPointRange<Double> = 100.0..200.0
