package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.evidence.EvidenceBundle
import java.time.Instant
import kotlin.math.max

/**
 * What personal calibration changes about the curve for one group of routes: a scale on
 * the drug's contribution, and how much narrower than the literature's interval it may be.
 */
data class Adjustment(
    val scale: Double = 1.0,
    /** Multiplier on the route's interval half-width; 1.0 = as wide as the literature says. */
    val shrink: Double = 1.0,
    /** The interval never gets narrower than this, whatever [shrink] says (observed scatter). */
    val minFraction: Double = 0.0,
)

typealias Adjustments = Map<CalibrationGroup, Adjustment>

/**
 * Population E2 curve from recorded administrations. Deterministic: the same
 * doses + bundle always give the same series. Output is a median with an
 * uncertainty interval — never a bare number — and is a literature-based
 * population estimate, not a clinically validated prediction.
 *
 * A dose the model cannot describe (gel, a patch of unknown strength) is left out and
 * reported in [EstimateResult.Available.excluded]; it never silently counts as zero and
 * never hides the doses that can be modelled.
 */
class E2CurveEngine(private val bundle: EvidenceBundle) {

    private val models = E2RouteModels(bundle)

    fun curve(
        doses: List<DoseEvent>,
        from: Instant,
        to: Instant,
        samples: Int = 160,
        baseline: Double = DEFAULT_BASELINE,
        /** Personal calibration per route group; empty = the population curve. */
        adjustments: Adjustments = emptyMap(),
    ): EstimateResult {
        val administered = doses.filter { it.status.wasTaken }
        if (administered.isEmpty()) return EstimateResult.Unavailable(ModelUnavailableReason.NO_DATA)

        val excluded = ArrayList<ExcludedDose>()
        val modelled = ArrayList<DoseEvent>()
        for (d in administered) {
            val why = models.exclusionOf(d)
            if (why != null) excluded += ExcludedDose(d, why) else modelled += d
        }
        val estrogens = administered.filterNot { it.drug.isAntiandrogen }
        if (estrogens.isNotEmpty() && modelled.none { !it.drug.isAntiandrogen }) {
            return EstimateResult.Unavailable(ModelUnavailableReason.ROUTE_UNSUPPORTED)
        }

        val estrogenRoutes = modelled.filterNot { it.drug.isAntiandrogen }.map { it.route }.toSet()
        val frac = estrogenRoutes.maxOfOrNull { fractionFor(it, adjustments) } ?: DEFAULT_FRACTION
        val scaleOf = modelled.associateWith { adjustments[it.route.calibrationGroup()]?.scale ?: 1.0 }
        val span = (to.toEpochMilli() - from.toEpochMilli()).toDouble()
        val points = (0..samples).map { i ->
            val tMillis = from.toEpochMilli() + (span * i / samples).toLong()
            var contrib = 0.0
            for (d in modelled) {
                val elapsed = (tMillis - d.occurredAt.toEpochMilli()) / MILLIS_PER_DAY
                if (elapsed <= 0.0 || elapsed > MAX_TAIL_DAYS) continue
                contrib += (models.contribution(d, elapsed) ?: 0.0) * scaleOf.getValue(d)
            }
            val sum = baseline + contrib
            EstimatePoint(Instant.ofEpochMilli(tMillis), sum, sum * (1 - frac), sum * (1 + frac))
        }
        return EstimateResult.Available(EstimateSeries(Hormone.E2, points), excluded)
    }

    /** Median and interval at [instant], or null when no curve can be produced. */
    fun estimateAt(
        doses: List<DoseEvent>,
        instant: Instant,
        baseline: Double = DEFAULT_BASELINE,
        adjustments: Adjustments = emptyMap(),
    ): EstimatePoint? {
        val r = curve(doses, instant.minusSeconds(3600), instant.plusSeconds(3600), samples = 2, baseline = baseline, adjustments = adjustments)
        return (r as? EstimateResult.Available)?.series?.pointAt(instant)
    }

    /** Single median E2 (pg/mL) at [instant], or null when no curve can be produced. */
    fun medianAt(doses: List<DoseEvent>, instant: Instant, baseline: Double = DEFAULT_BASELINE, adjustments: Adjustments = emptyMap()): Double? =
        estimateAt(doses, instant, baseline, adjustments)?.median

    /** The taken estrogen doses among [doses] that the model leaves out, each with its reason. */
    fun exclusionsIn(doses: List<DoseEvent>): List<ExcludedDose> =
        doses.filter { it.status.wasTaken }.mapNotNull { d -> models.exclusionOf(d)?.let { ExcludedDose(d, it) } }

    /** How much of a dose's E2 at [elapsedDays] the model puts on the curve (pg/mL), or null if it cannot. */
    fun contributionOf(dose: DoseEvent, elapsedDays: Double): Double? = models.contribution(dose, elapsedDays)

    private fun fractionFor(route: com.hormonelog.core.domain.Route, adjustments: Adjustments): Double {
        val base = models.routeUncertainty(route)
        val adj = adjustments[route.calibrationGroup()] ?: return base
        return max(adj.minFraction, base * adj.shrink).coerceAtMost(base)
    }

    companion object {
        const val DEFAULT_BASELINE = 5.0
        private const val DEFAULT_FRACTION = 0.3
        private const val MILLIS_PER_DAY = 86_400_000.0

        /** Past this the slowest modelled tail is below a fraction of a pg/mL. */
        private const val MAX_TAIL_DAYS = 400.0
    }
}
