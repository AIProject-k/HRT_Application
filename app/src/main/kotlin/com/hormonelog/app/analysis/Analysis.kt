package com.hormonelog.app.analysis

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.evidence.EvidenceBundleV1
import com.hormonelog.core.modelengine.CalibrationEngine
import com.hormonelog.core.modelengine.CalibrationResult
import com.hormonelog.core.modelengine.CycleAnalysis
import com.hormonelog.core.modelengine.CyclePosition
import com.hormonelog.core.modelengine.CycleStats
import com.hormonelog.core.modelengine.E2CurveEngine
import com.hormonelog.core.modelengine.EstimatePoint
import com.hormonelog.core.modelengine.EstimateResult
import com.hormonelog.core.modelengine.EstimateSeries
import com.hormonelog.core.modelengine.ExcludedDose
import com.hormonelog.core.modelengine.TtSuppressionEngine
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** E2 and Total T curves for one chart window, plus what was left out and why. */
data class Curves(
    val e2: EstimateSeries?,
    val tt: EstimateSeries?,
    /** Doses the model could not describe; they stay recorded but are not on the curve. */
    val excluded: List<ExcludedDose>,
    /** True when the user chose not to say whether they have testes, so there is no T curve. */
    val ttHidden: Boolean,
    val calibration: CalibrationResult,
    /** Planned doses beyond now, for the chart's hollow ticks. */
    val planned: List<DoseEvent>,
) {
    val hasE2: Boolean get() = e2 != null
}

/** The calculations every screen shares. All pure: the same records always give the same answer. */
object Analysis {

    private val bundle = EvidenceBundleV1.bundle
    private val e2Engine = E2CurveEngine(bundle)
    private val ttEngine = TtSuppressionEngine(bundle)
    private val calibrationEngine = CalibrationEngine(bundle)
    private val cycleAnalysis = CycleAnalysis(e2Engine)

    /** The person's own pre-HRT Total T, if they recorded one: the Total T curve's starting point. */
    fun baselineT(labs: List<LabResult>): Double? =
        labs.filter { it.isBaseline }
            .sortedByDescending { it.collectedAt ?: Instant.MIN }
            .firstNotNullOfOrNull { lab ->
                lab.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE }?.let { canonical(it) }
            }

    fun canonical(v: LabAnalyteValue): Double? =
        (v.canonicalValue ?: LabAnalyteValue.canonical(v.analyte, v.reportedValue, v.reportedUnit))?.takeIf { it.isFinite() }

    fun calibrate(doses: List<DoseEvent>, labs: List<LabResult>, status: GonadalStatus): CalibrationResult =
        calibrationEngine.calibrate(doses, labs, status, baselineT(labs))

    /** Doses a plan still expects after [now], as events the model can use. */
    fun planned(regimens: List<Regimen>, doses: List<DoseEvent>, now: Instant, until: Instant, zone: ZoneId): List<DoseEvent> =
        regimens.filter { it.active }.flatMap { r ->
            Regimen.expected(r, now, until, doses, zone).map { at ->
                DoseEvent(
                    id = UUID.nameUUIDFromBytes("${r.id}@${at.toEpochMilli()}".toByteArray()),
                    occurredAt = at,
                    sourceZoneId = zone.id,
                    drug = r.drug,
                    route = r.route,
                    amountEntered = r.amountEntered,
                    enteredUnit = r.enteredUnit,
                    normalizedMilligrams = DoseEvent.normalizeMilligrams(r.amountEntered, r.enteredUnit),
                    status = DoseStatus.ADMINISTERED,
                    source = RecordSource.SCHEDULE,
                    patchCycle = r.patchCycle,
                )
            }
        }.sortedBy { it.occurredAt }

    /**
     * Both curves over [from]..[to]: real doses up to [now], planned ones after it.
     * Pass [calibration] when it has already been computed for these records.
     */
    fun curves(
        doses: List<DoseEvent>,
        regimens: List<Regimen>,
        labs: List<LabResult>,
        status: GonadalStatus,
        now: Instant,
        from: Instant,
        to: Instant,
        zone: ZoneId,
        calibration: CalibrationResult = calibrate(doses, labs, status),
        samples: Int = 160,
    ): Curves {
        val taken = doses.filter { it.status.wasTaken }
        val planned = planned(regimens, doses, now, to, zone)
        if (taken.isEmpty() && planned.isEmpty()) {
            return Curves(null, null, emptyList(), status == GonadalStatus.DECLINED, calibration, emptyList())
        }
        val all = taken + planned
        val e2 = e2Engine.curve(all, from, to, samples = samples, adjustments = calibration.adjustments)
        val tt = ttEngine.curve(
            all, from, to, status, samples = samples,
            adjustments = calibration.adjustments, baselineT = baselineT(labs), tAdjustment = calibration.ttAdjustment,
        )
        return Curves(
            e2 = (e2 as? EstimateResult.Available)?.series,
            tt = (tt as? EstimateResult.Available)?.series,
            excluded = (e2 as? EstimateResult.Available)?.excluded ?: e2Engine.exclusionsIn(taken),
            ttHidden = status == GonadalStatus.DECLINED,
            calibration = calibration,
            planned = planned,
        )
    }

    /** Median and interval of E2 right now (or at [at]), calibrated; null with no usable dose. */
    fun e2At(doses: List<DoseEvent>, regimens: List<Regimen>, calibration: CalibrationResult, at: Instant, now: Instant, zone: ZoneId): EstimatePoint? {
        val taken = doses.filter { it.status.wasTaken }
        if (taken.isEmpty()) return null
        return e2Engine.estimateAt(taken + planned(regimens, doses, now, at.plusSeconds(3600), zone), at, adjustments = calibration.adjustments)
    }

    fun ttAt(
        doses: List<DoseEvent>,
        labs: List<LabResult>,
        status: GonadalStatus,
        calibration: CalibrationResult,
        at: Instant,
    ): EstimatePoint? {
        val taken = doses.filter { it.status.wasTaken }
        if (taken.isEmpty() || status == GonadalStatus.DECLINED) return null
        return ttEngine.estimateAt(taken, at, status, calibration.adjustments, baselineT(labs), calibration.ttAdjustment)
    }

    /** Expected peak and trough of the cycle that contains [at]. */
    fun cycle(doses: List<DoseEvent>, regimens: List<Regimen>, calibration: CalibrationResult, at: Instant, zone: ZoneId): CycleStats? {
        val taken = doses.filter { it.status.wasTaken }
        if (taken.isEmpty()) return null
        val horizon = at.plusSeconds(60L * 86_400L)
        return cycleAnalysis.stats(taken + planned(regimens, doses, at, horizon, zone), at, calibration.adjustments)
    }

    /** Where [at] (a blood draw, say) sits in its dosing cycle: how long after the dose, near which extreme. */
    fun cyclePosition(doses: List<DoseEvent>, regimens: List<Regimen>, calibration: CalibrationResult, at: Instant, zone: ZoneId): CyclePosition? {
        val taken = doses.filter { it.status.wasTaken }
        if (taken.isEmpty()) return null
        val horizon = at.plusSeconds(60L * 86_400L)
        return cycleAnalysis.position(taken + planned(regimens, doses, at, horizon, zone), at, calibration.adjustments)
    }

    /** The estrogen dose before [at] that a blood draw is measured from, if any. */
    fun lastEstrogenBefore(doses: List<DoseEvent>, at: Instant): DoseEvent? =
        doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen && !it.occurredAt.isAfter(at) }.maxByOrNull { it.occurredAt }
}
