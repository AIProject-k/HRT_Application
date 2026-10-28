package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundle
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/** The chart prints two decimals, so a contribution under half of 0.01 ng/mL cannot be seen. */
const val P4_DISPLAY_PRECISION_NG_ML = 0.005

/**
 * Experimental oral P4 drug contribution (ng/mL), not total endogenous + exogenous P4.
 * Piecewise linear ANSM population means after subtracting the study's baseline.
 * Beyond 8h: extrapolate the final 6–8h log slope. Repeated doses add linearly.
 * These are explicit assumptions, not a validated PK fit or confidence interval.
 *
 * A recorded dose outside the model cannot be summed, so it blanks the moments its tail still reaches.
 * Once that tail is below display precision it stops blanking. It stays on record either way.
 */
class ProgesteroneEngine(private val bundle: EvidenceBundle) {
    private val hours = listOf(0, 1, 2, 4, 6, 8)
    private val p = bundle.parametersFor(Route.ORAL, "P4")
    private fun supported(d: DoseEvent): Boolean = d.route == Route.ORAL && d.enteredUnit == DoseUnit.MG &&
        d.amountEntered.isFinite() && d.amountEntered in (p.getValue("minMg")..p.getValue("maxMg"))

    /** The number at [at], or null when there is none. [readingAt] says why. */
    fun estimateAt(doses: List<DoseEvent>, at: Instant): EstimatePoint? = readingAt(doses, at).point

    /** The number at [at] when there is one, otherwise the reason. Records are read, never removed. */
    fun readingAt(doses: List<DoseEvent>, at: Instant): P4Reading {
        if (!ready()) return P4Reading(P4Status.MISSING_EVIDENCE)
        val p4 = doses.filter { it.drug == Drug.PROGESTERONE_MICRONIZED }
        val taken = p4.filter { it.status.wasTaken }
        val before = taken.filter { !it.occurredAt.isAfter(at) }
        if (before.isEmpty()) {
            return P4Reading(
                when {
                    p4.isEmpty() -> P4Status.NO_RECORDED_DOSE
                    taken.isEmpty() -> P4Status.NO_TAKEN_DOSE
                    else -> P4Status.DOSE_OUTSIDE_VIEW
                },
            )
        }
        val blocking = before.filter { !supported(it) && reaches(it, at) }
        if (blocking.isNotEmpty()) return P4Reading(blockedStatus(blocking), blockers = blocking)
        val usable = before.filter { supported(it) }
        val value = usable.sumOf { contribution(hoursAfter(it, at), it.amountEntered) }
        if (!value.isFinite()) return P4Reading(P4Status.NUMERIC_ERROR)
        // Tiny counts as decay only after the peak: a dose taken a moment ago is still being absorbed.
        val decayed = value < P4_DISPLAY_PRECISION_NG_ML && usable.all { hoursAfter(it, at) > 8.0 }
        // Equal bounds deliberately draw no uncertainty band; no statistical interval is established.
        return P4Reading(
            if (decayed) P4Status.DECAYED_TO_NEAR_ZERO else P4Status.AVAILABLE,
            EstimatePoint(at, value, value, value),
        )
    }

    /** Oral P4 doses up to [to] that the curve cannot use and that still reach the window from [from]. Any one of them blanks the window. */
    fun blockers(doses: List<DoseEvent>, from: Instant, to: Instant): List<DoseEvent> {
        if (!ready()) return emptyList()
        return doses.filter {
            it.drug == Drug.PROGESTERONE_MICRONIZED && it.status.wasTaken && !it.occurredAt.isAfter(to) && !supported(it) && reaches(it, from)
        }
    }

    /** The reason for records that blank a moment or a window: a route the model does not cover, or an amount it cannot scale. */
    fun blockedStatus(blockers: List<DoseEvent>): P4Status =
        if (blockers.any { it.route != Route.ORAL }) P4Status.UNSUPPORTED_ROUTE else P4Status.UNSUPPORTED_DOSE

    fun curve(doses: List<DoseEvent>, from: Instant, to: Instant, samples: Int = 160): EstimateResult {
        if (!ready()) return EstimateResult.Unavailable(ModelUnavailableReason.MISSING_EVIDENCE)
        val taken = doses.filter { it.drug == Drug.PROGESTERONE_MICRONIZED && it.status.wasTaken && !it.occurredAt.isAfter(to) }
        if (taken.isEmpty()) return EstimateResult.Unavailable(ModelUnavailableReason.NO_DATA)
        val blocking = blockers(doses, from, to)
        if (blocking.isNotEmpty()) {
            return EstimateResult.Unavailable(
                if (blocking.any { it.route != Route.ORAL }) ModelUnavailableReason.ROUTE_UNSUPPORTED else ModelUnavailableReason.UNSUPPORTED_DOSE,
            )
        }
        require(to >= from && samples > 0)
        val span = to.toEpochMilli() - from.toEpochMilli()
        val times = (0..samples).map { from.plusMillis((span.toDouble() * it / samples).toLong()) }.toMutableSet()
        // Long chart windows must still contain the short oral peaks and pre-dose zeros.
        taken.forEach { d -> hours.forEach { h -> d.occurredAt.plusSeconds(h * 3600L).takeIf { it in from..to }?.let(times::add) } }
        val points = times.sorted().map { at -> readingAt(taken, at).point ?: EstimatePoint(at, 0.0, 0.0, 0.0) }
        // Out-of-range records that cannot reach this window stay on record and are reported, not hidden.
        val excluded = taken.filter { !supported(it) }.map { ExcludedDose(it, DoseExclusion.OUTSIDE_INFLUENCE) }
        return EstimateResult.Available(EstimateSeries(Hormone.P4, points), excluded)
    }

    private fun ready(): Boolean = bundle.supports(Route.ORAL) &&
        (listOf("referenceMg", "studyBaseline", "minMg", "maxMg") + hours.drop(1).map { "hour$it" }).all { p[it]?.isFinite() == true } &&
        p.getValue("referenceMg") > 0 && p.getValue("minMg") > 0 && p.getValue("maxMg") >= p.getValue("minMg") &&
        hours.drop(1).all { p.getValue("hour$it") > p.getValue("studyBaseline") } && p.getValue("hour6") > p.getValue("hour8")

    /** Whether the dose's modelled contribution can still be at or above display precision at [instant]. */
    private fun reaches(d: DoseEvent, instant: Instant): Boolean =
        instant.toEpochMilli() < d.occurredAt.toEpochMilli() + horizonHours(d) * 3_600_000.0

    /**
     * Hours after a dose until its modelled tail falls below display precision. Exact when the tail is still above
     * precision at 8 h. When it is already below at 8 h, the answer is 8 h, which is an upper bound on the true
     * crossing. The amount scales linearly, as the model does, so this holds only where that rule holds.
     * A dose the model cannot scale never expires.
     */
    private fun horizonHours(d: DoseEvent): Double {
        if (d.route != Route.ORAL || d.enteredUnit != DoseUnit.MG || !d.amountEntered.isFinite() || d.amountEntered <= 0.0) {
            return Double.POSITIVE_INFINITY
        }
        val tailAtEightHours = atHour(8) * d.amountEntered / p.getValue("referenceMg")
        return 8.0 + max(0.0, ln(tailAtEightHours / P4_DISPLAY_PRECISION_NG_ML) / tailRate())
    }

    private fun hoursAfter(d: DoseEvent, at: Instant): Double = (at.toEpochMilli() - d.occurredAt.toEpochMilli()) / 3_600_000.0

    /** Mean observed concentration above the study baseline, [hour] hours after a dose (ng/mL). */
    private fun atHour(hour: Int): Double = if (hour == 0) 0.0 else p.getValue("hour$hour") - p.getValue("studyBaseline")

    /** Per-hour decay of the tail beyond 8 h, from the 6 h and 8 h points. */
    private fun tailRate(): Double = ln(atHour(6) / atHour(8)) / 2

    private fun contribution(h: Double, mg: Double): Double {
        if (h <= 0.0) return 0.0
        val reference = if (h > 8) {
            atHour(8) * exp(-tailRate() * (h - 8))
        } else {
            val hi = hours.first { it >= h }
            val lo = hours[hours.indexOf(hi) - 1]
            atHour(lo) + (atHour(hi) - atHour(lo)) * (h - lo) / (hi - lo)
        }
        return reference * mg / p.getValue("referenceMg")
    }
}

/** Why the oral P4 estimate has, or has no, a number at one moment. Recorded doses are never removed. */
enum class P4Status {
    /** A number is shown: the modelled contribution is at or above display precision, or a dose was just taken. */
    AVAILABLE,

    /** Records are on file, but their modelled contribution is below display precision. This is not the patient's baseline P4. */
    DECAYED_TO_NEAR_ZERO,

    /** No oral P4 record exists. */
    NO_RECORDED_DOSE,

    /** P4 records exist, but every one of them was skipped. */
    NO_TAKEN_DOSE,

    /** Taken doses exist, but every one of them comes after this moment. */
    DOSE_OUTSIDE_VIEW,

    /** A recorded dose by a route the model does not cover still reaches this moment. */
    UNSUPPORTED_ROUTE,

    /** A recorded oral dose outside the model's amount or unit still reaches this moment. */
    UNSUPPORTED_DOSE,

    /** The P4 evidence is incomplete, so the model is not run. */
    MISSING_EVIDENCE,

    /** The arithmetic gave a non-finite number. Defensive: validated inputs cannot reach it. */
    NUMERIC_ERROR,

    /** The app is still building the curves. The engine never returns this. */
    PENDING_CALCULATION,
}

/** One moment's reading. Only AVAILABLE and DECAYED_TO_NEAR_ZERO carry a [point]; [blockers] name the records behind an UNSUPPORTED status. */
data class P4Reading(val status: P4Status, val point: EstimatePoint? = null, val blockers: List<DoseEvent> = emptyList())
