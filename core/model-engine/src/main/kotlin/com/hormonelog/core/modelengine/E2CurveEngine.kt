package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.evidence.EvidenceBundle
import java.time.Instant

/**
 * Population E2 curve from recorded administrations. Deterministic: the same
 * doses + bundle always give the same series. Output is a median with an
 * uncertainty interval — never a bare number — and is a literature-based
 * population estimate, not a clinically validated prediction.
 */
class E2CurveEngine(private val bundle: EvidenceBundle) {

    private val models = E2RouteModels(bundle)

    fun curve(
        doses: List<DoseEvent>,
        from: Instant,
        to: Instant,
        samples: Int = 160,
        baseline: Double = 5.0,
        /** Level 1–2 exposure-scale multiplier applied to the drug contribution only. */
        exposureScale: Double = 1.0,
    ): EstimateResult {
        val administered = doses.filter {
            it.status == DoseStatus.ADMINISTERED || it.status == DoseStatus.CORRECTED
        }
        if (administered.isEmpty()) return EstimateResult.Unavailable(ModelUnavailableReason.NO_DATA)

        val routes = administered.map { it.route }.toSet()
        if (routes.any { !bundle.supports(it) }) {
            return EstimateResult.Unavailable(ModelUnavailableReason.ROUTE_UNSUPPORTED)
        }
        val blocked = administered.filter { models.contribution(it, 1.0) == null }
        if (blocked.isNotEmpty()) {
            return EstimateResult.Blocked(blocked.map { "약물·경로 조합 미지원: ${it.drug}/${it.route}" }.toSet())
        }

        val frac = models.uncertaintyFraction(routes)
        val span = (to.toEpochMilli() - from.toEpochMilli()).toDouble()
        val points = (0..samples).map { i ->
            val tMillis = from.toEpochMilli() + (span * i / samples).toLong()
            var contrib = 0.0
            for (d in administered) {
                val elapsed = (tMillis - d.occurredAt.toEpochMilli()) / 86_400_000.0
                contrib += models.contribution(d, elapsed) ?: 0.0
            }
            val sum = baseline + contrib * exposureScale
            EstimatePoint(Instant.ofEpochMilli(tMillis), sum, sum * (1 - frac), sum * (1 + frac))
        }
        return EstimateResult.Available(EstimateSeries(Hormone.E2, points))
    }

    /** Single median E2 (pg/mL) at [instant], or null when no curve can be produced. */
    fun medianAt(doses: List<DoseEvent>, instant: Instant, baseline: Double = 5.0, exposureScale: Double = 1.0): Double? {
        val r = curve(doses, instant.minusSeconds(3600), instant.plusSeconds(3600), samples = 2, baseline = baseline, exposureScale = exposureScale)
        return (r as? EstimateResult.Available)?.series?.medianAt(instant)
    }
}
