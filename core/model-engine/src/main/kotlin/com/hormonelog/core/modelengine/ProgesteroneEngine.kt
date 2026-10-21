package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundle
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln

/**
 * Experimental oral P4 drug contribution (ng/mL), not total endogenous + exogenous P4.
 * Piecewise linear ANSM population means after subtracting the study's baseline.
 * Beyond 8h: extrapolate the final 6–8h log slope. Repeated doses add linearly.
 * These are explicit assumptions, not a validated PK fit or confidence interval.
 */
class ProgesteroneEngine(private val bundle: EvidenceBundle) {
    private val hours = listOf(0, 1, 2, 4, 6, 8)
    private val p = bundle.parametersFor(Route.ORAL, "P4")
    private fun supported(d: DoseEvent): Boolean = d.route == Route.ORAL && d.enteredUnit == DoseUnit.MG &&
        d.amountEntered.isFinite() && d.amountEntered in (p.getValue("minMg")..p.getValue("maxMg"))

    fun estimateAt(doses: List<DoseEvent>, at: Instant): EstimatePoint? {
        if (!ready()) return null
        val taken = doses.filter { it.drug == Drug.PROGESTERONE_MICRONIZED && it.status.wasTaken && !it.occurredAt.isAfter(at) }
        if (taken.isEmpty() || taken.any { !supported(it) }) return null
        val value = taken.sumOf { contribution((at.toEpochMilli() - it.occurredAt.toEpochMilli()) / 3_600_000.0, it.amountEntered) }
        // Equal bounds deliberately draw no uncertainty band; no statistical interval is established.
        return EstimatePoint(at, value, value, value)
    }

    private fun ready(): Boolean = bundle.supports(Route.ORAL) &&
        (listOf("referenceMg", "studyBaseline", "minMg", "maxMg") + hours.drop(1).map { "hour$it" }).all { p[it]?.isFinite() == true } &&
        p.getValue("referenceMg") > 0 && p.getValue("minMg") > 0 && p.getValue("maxMg") >= p.getValue("minMg") &&
        hours.drop(1).all { p.getValue("hour$it") > p.getValue("studyBaseline") } && p.getValue("hour6") > p.getValue("hour8")

    private fun contribution(h: Double, mg: Double): Double {
        if (h <= 0.0) return 0.0
        val baseline = p.getValue("studyBaseline")
        fun at(hour: Int) = if (hour == 0) 0.0 else p.getValue("hour$hour") - baseline
        val reference = if (h > 8) {
            val rate = ln(at(6) / at(8)) / 2
            at(8) * exp(-rate * (h - 8))
        } else {
            val hi = hours.first { it >= h }
            val lo = hours[hours.indexOf(hi) - 1]
            at(lo) + (at(hi) - at(lo)) * (h - lo) / (hi - lo)
        }
        return reference * mg / p.getValue("referenceMg")
    }

    fun curve(doses: List<DoseEvent>, from: Instant, to: Instant, samples: Int = 160): EstimateResult {
        if (!ready()) return EstimateResult.Unavailable(ModelUnavailableReason.MISSING_EVIDENCE)
        val taken = doses.filter { it.drug == Drug.PROGESTERONE_MICRONIZED && it.status.wasTaken && !it.occurredAt.isAfter(to) }
        if (taken.isEmpty()) return EstimateResult.Unavailable(ModelUnavailableReason.NO_DATA)
        if (taken.any { !supported(it) }) return EstimateResult.Unavailable(ModelUnavailableReason.ROUTE_UNSUPPORTED)
        require(to >= from && samples > 0)
        val span = to.toEpochMilli() - from.toEpochMilli()
        val times = (0..samples).map { from.plusMillis((span.toDouble() * it / samples).toLong()) }.toMutableSet()
        // Long chart windows must still contain the short oral peaks and pre-dose zeros.
        taken.forEach { d -> hours.forEach { h -> d.occurredAt.plusSeconds(h * 3600L).takeIf { it in from..to }?.let(times::add) } }
        val points = times.sorted().map { at -> estimateAt(taken, at) ?: EstimatePoint(at, 0.0, 0.0, 0.0) }
        return EstimateResult.Available(EstimateSeries(Hormone.P4, points))
    }
}
