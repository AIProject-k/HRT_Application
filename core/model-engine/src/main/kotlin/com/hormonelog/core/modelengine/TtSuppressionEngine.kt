package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.evidence.EvidenceBundle
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.expm1
import kotlin.math.pow

/** Personal correction of the Total T curve from measured T; none until enough labs exist. */
data class TtAdjustment(
    val scale: Double = 1.0,
    val shrink: Double = 1.0,
)

/**
 * Total-testosterone curve. Models a *delayed* hormonal effect: time-varying E2
 * exposure feeds a first-order effect compartment, whose concentration drives a
 * Hill suppression of LH-dependent testicular production; cyproterone adds a
 * further saturating central suppression. Peripheral AR antagonists do not lower
 * serum T (설계서 §4.3). Population estimate, not clinically validated.
 *
 * Nobody who chose not to say whether they have testes gets a curve at all.
 */
class TtSuppressionEngine(private val bundle: EvidenceBundle) {

    private val e2Engine = E2CurveEngine(bundle)
    private val pd = bundle.pdParameters()
    private val cpa = CpaModel(
        f = pd["cpaF"] ?: 0.88,
        ka = pd["cpaKaPerDay"] ?: 6.0,
        ke = pd["cpaKePerDay"] ?: 0.365,
    )

    fun curve(
        doses: List<DoseEvent>,
        from: Instant,
        to: Instant,
        status: GonadalStatus = GonadalStatus.INTACT,
        samples: Int = 160,
        /** The E2 calibration, because the T curve is driven by the (calibrated) E2 exposure. */
        adjustments: Adjustments = emptyMap(),
        /** Measured Total T before HRT; the starting point for someone with testes. */
        baselineT: Double? = null,
        tAdjustment: TtAdjustment = TtAdjustment(),
    ): EstimateResult {
        if (status == GonadalStatus.DECLINED) return EstimateResult.Unavailable(ModelUnavailableReason.HIDDEN_BY_USER)
        require(to >= from && samples > 0)

        val baseline = when (status) {
            GonadalStatus.POST_ORCHIECTOMY -> pd["ttBaselinePostOrchi"] ?: 30.0
            else -> baselineT?.coerceIn(MEASURED_BASELINE_MIN, MEASURED_BASELINE_MAX) ?: (pd["ttBaselineIntact"] ?: 600.0)
        }
        val floor = pd["ttFloor"] ?: 8.0
        val tau = pd["e2EffectTauDays"] ?: 7.0
        val emaxE2 = pd["e2SupprEmax"] ?: 0.97
        val ec50 = pd["e2SupprEC50"] ?: 45.0
        val hill = pd["e2SupprHill"] ?: 2.2
        val cpaEmax = pd["cpaSupprEmax"] ?: 0.6
        val cpaEc50 = pd["cpaSupprEC50"] ?: 8.0
        val frac = ((pd["ttIntervalFraction"] ?: 0.5) + if (status == GonadalStatus.UNKNOWN) 0.2 else 0.0) * tAdjustment.shrink

        fun point(at: Instant, raw: Double): EstimatePoint {
            val t = (raw * tAdjustment.scale).coerceIn(floor / 2, baseline * maxOf(1.0, tAdjustment.scale))
            return EstimatePoint(at, t, (t * (1 - frac)).coerceAtLeast(0.0), t * (1 + frac))
        }
        val span = to.toEpochMilli() - from.toEpochMilli()
        val times = (0..samples).map { from.plusMillis((span.toDouble() * it / samples).toLong()) }
        // No testicular production remains to suppress. This residual population
        // reference is not a fitted model of adrenal steroidogenesis.
        if (status == GonadalStatus.POST_ORCHIECTOMY) {
            return EstimateResult.Available(EstimateSeries(Hormone.TOTAL_T, times.map { point(it, baseline) }))
        }

        // Integrate exposure on an epoch-aligned hourly grid; output density must
        // not change pharmacodynamics or miss short oral/sublingual exposures.
        val warmupMillis = Math.floorDiv(from.minus(45, ChronoUnit.DAYS).toEpochMilli(), HOUR_MILLIS) * HOUR_MILLIS
        val endMillis = (Math.floorDiv(to.toEpochMilli(), HOUR_MILLIS) + 1) * HOUR_MILLIS
        val steps = ((endMillis - warmupMillis) / HOUR_MILLIS).toInt()
        val e2res = e2Engine.curve(doses, Instant.ofEpochMilli(warmupMillis), Instant.ofEpochMilli(endMillis), samples = steps, adjustments = adjustments)
        if (e2res !is EstimateResult.Available) return e2res
        val e2pts = e2res.series.points
        fun advance(effect: Double, previous: Double, current: Double, dtDays: Double): Double {
            if (dtDays == 0.0) return effect
            val a = dtDays / tau
            val absorbed = -expm1(-a)
            // Exact first-order solution for linearly changing E2 input.
            return effect + absorbed * (previous - effect) + (current - previous) * (1 - absorbed / a)
        }
        val effects = DoubleArray(e2pts.size)
        var ece = e2pts.first().median
        for (i in e2pts.indices) {
            val p = e2pts[i]
            if (i > 0) {
                val dtDays = (p.at.toEpochMilli() - e2pts[i - 1].at.toEpochMilli()) / 86_400_000.0
                ece = advance(ece, e2pts[i - 1].median, p.median, dtDays)
            }
            effects[i] = ece
        }
        val outputE2 = e2Engine.curve(doses, from, to, samples = samples, adjustments = adjustments)
        if (outputE2 !is EstimateResult.Available) return outputE2
        val out = outputE2.series.points.map { p ->
            val i = ((p.at.toEpochMilli() - warmupMillis) / HOUR_MILLIS).toInt()
            val dtDays = (p.at.toEpochMilli() - e2pts[i].at.toEpochMilli()) / 86_400_000.0
            // Advance only up to the requested instant; interpolating the next
            // hour's T would let a future administration affect an earlier value.
            val effect = advance(effects[i], e2pts[i].median, p.median, dtDays)
            val hillTerm = (effect / ec50).pow(hill)
            val supprE2 = emaxE2 * hillTerm / (1.0 + hillTerm)
            val cpaLevel = cpa.levelAt(doses, p.at.toEpochMilli())
            val supprCpa = cpaEmax * cpaLevel / (cpaLevel + cpaEc50)
            val raw = (baseline * (1 - supprE2) * (1 - supprCpa)).coerceIn(floor, baseline)
            point(p.at, raw)
        }
        return EstimateResult.Available(EstimateSeries(Hormone.TOTAL_T, out), e2res.excluded)
    }

    fun estimateAt(
        doses: List<DoseEvent>,
        instant: Instant,
        status: GonadalStatus = GonadalStatus.INTACT,
        adjustments: Adjustments = emptyMap(),
        baselineT: Double? = null,
        tAdjustment: TtAdjustment = TtAdjustment(),
    ): EstimatePoint? {
        val r = curve(doses, instant.minusSeconds(3600), instant.plusSeconds(3600), status, samples = 4, adjustments = adjustments, baselineT = baselineT, tAdjustment = tAdjustment)
        return (r as? EstimateResult.Available)?.series?.pointAt(instant)
    }

    fun medianAt(
        doses: List<DoseEvent>,
        instant: Instant,
        status: GonadalStatus = GonadalStatus.INTACT,
        adjustments: Adjustments = emptyMap(),
    ): Double? = estimateAt(doses, instant, status, adjustments)?.median

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
        /** A measured pre-HRT value outside this is a unit slip or a different condition, not a starting point. */
        const val MEASURED_BASELINE_MIN = 150.0
        const val MEASURED_BASELINE_MAX = 1200.0
    }
}
