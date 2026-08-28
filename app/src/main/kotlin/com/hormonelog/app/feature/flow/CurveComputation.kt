package com.hormonelog.app.feature.flow

import com.hormonelog.app.feature.dashboard.HormoneSeries
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.evidence.EvidenceBundleV1
import com.hormonelog.core.modelengine.CalibrationEngine
import com.hormonelog.core.modelengine.CalibrationResult
import com.hormonelog.core.modelengine.E2CurveEngine
import com.hormonelog.core.modelengine.EstimateResult
import com.hormonelog.core.modelengine.EstimateSeries
import com.hormonelog.core.modelengine.TtSuppressionEngine
import java.time.Instant

/** E2 + TT population curves for a chart window, or nulls when no model applies. */
data class Curves(
    val e2: EstimateSeries?,
    val tt: EstimateSeries?,
    /** true when every recorded route has an evidence-backed model. */
    val canEstimate: Boolean,
    val cal: CalibrationResult = CalibrationResult.NONE,
) {
    fun forSeries(series: HormoneSeries): EstimateSeries? =
        if (series == HormoneSeries.E2) e2 else tt
}

private val bundle = EvidenceBundleV1.bundle
private val e2Engine = E2CurveEngine(bundle)
private val ttEngine = TtSuppressionEngine(bundle)
private val calEngine = CalibrationEngine(bundle)

/**
 * Compute both curves over [windowStart, windowEnd]. Past uses recorded [doses];
 * the forecast region extends active [regimens] beyond [now]. Eligible [labs]
 * drive a Level 1–2 exposure-scale calibration of the E2 (and hence TT) curve.
 */
fun computeCurves(
    doses: List<DoseEvent>,
    regimens: List<Regimen>,
    labs: List<LabResult>,
    now: Instant,
    windowStart: Instant,
    windowEnd: Instant,
): Curves {
    if (doses.isEmpty()) return Curves(null, null, canEstimate = false)

    val forecast = regimens
        .filter { it.active }
        .flatMap { Regimen.expand(it, windowEnd) }
        .filter { it.occurredAt.isAfter(now) }
    val all = doses + forecast

    val routes = doses.map { it.route }.toSet()
    val canEstimate = routes.isNotEmpty() && routes.all(bundle::supports)

    val cal = calEngine.calibrate(doses, labs)
    val e2 = (e2Engine.curve(all, windowStart, windowEnd, exposureScale = cal.exposureScale) as? EstimateResult.Available)?.series
    val tt = (ttEngine.curve(all, windowStart, windowEnd, exposureScale = cal.exposureScale) as? EstimateResult.Available)?.series
    return Curves(e2, tt, canEstimate, cal)
}

/** Single median for the home hero / scrub readout. */
fun e2MedianAt(doses: List<DoseEvent>, labs: List<LabResult>, at: Instant): Double? {
    if (doses.isEmpty()) return null
    val scale = calEngine.calibrate(doses, labs).exposureScale
    return e2Engine.medianAt(doses, at, exposureScale = scale)
}
