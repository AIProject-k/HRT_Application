package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.evidence.EvidenceBundle
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

enum class CalibrationLevel { LEVEL_0, LEVEL_1, LEVEL_2 }

/** Why a lab was not used to calibrate; the app turns these into plain sentences. */
enum class LabExclusion {
    /** Drawn before HRT started: a starting point, not a result of the treatment. */
    BASELINE,
    NO_COLLECTION_TIME,
    UNKNOWN_UNIT,
    NO_PRIOR_DOSE,

    /** Too little recorded history to know what the body had already taken in. */
    TOO_EARLY,
    NO_MODEL,

    /** A route without a model (gel) sits in the weeks before the draw. */
    MIXED_UNMODELLED,

    /** Several routes contribute in similar amounts, so the lab cannot be pinned on one. */
    MIXED_ROUTES,

    /** The expected value is too small to compare against. */
    LOW_PREDICTION,

    /** Total T only: a single usable lab is not enough to correct a curve. */
    TOO_FEW_LABS,
}

/** What happened to one analyte of one lab. [expected] is the population value at the draw. */
data class AnalyteCalibration(
    val used: Boolean,
    val exclusion: LabExclusion?,
    val expected: EstimatePoint?,
    /** measured ÷ expected, on the drug's own contribution for E2. */
    val ratio: Double?,
    val group: CalibrationGroup?,
)

data class LabCalibration(val labId: UUID, val e2: AnalyteCalibration?, val tt: AnalyteCalibration?)

data class GroupCalibration(
    val group: CalibrationGroup,
    val scale: Double,
    val labCount: Int,
    /** The labs asked for more than the correction is allowed to give. */
    val atLimit: Boolean,
    /** Scatter between the labs (ln-ratio standard deviation); 0 with a single lab. */
    val spread: Double,
)

data class TtCalibration(val scale: Double, val labCount: Int, val atLimit: Boolean)

/**
 * Level 0–2 calibration: a conservative *exposure-scale* adjustment only (설계서
 * §4.4), kept per route group. Eligible E2 labs are compared to the population
 * prediction at their collection time; the geometric mean of the ratios (clamped)
 * multiplies that group's drug contribution. Curve-shape / personal PK fitting is
 * Level 3+ and not done here. Total T is corrected the same way once two labs agree.
 */
data class CalibrationResult(
    val labs: Map<UUID, LabCalibration>,
    val groups: Map<CalibrationGroup, GroupCalibration>,
    val tt: TtCalibration?,
) {
    val adjustments: Adjustments = groups.mapValues { (_, g) ->
        Adjustment(
            scale = g.scale,
            shrink = (1.0 / sqrt(1.0 + g.labCount)).coerceAtLeast(0.5),
            minFraction = g.spread,
        )
    }

    val ttAdjustment: TtAdjustment = tt?.let { TtAdjustment(it.scale, (1.0 / sqrt(1.0 + it.labCount / 2.0)).coerceAtLeast(0.6)) } ?: TtAdjustment()

    /** Labs whose E2 value shaped a curve. */
    val includedLabIds: Set<UUID> = labs.filterValues { it.e2?.used == true }.keys

    val level: CalibrationLevel = when (groups.values.maxOfOrNull { it.labCount } ?: 0) {
        0 -> CalibrationLevel.LEVEL_0
        1 -> CalibrationLevel.LEVEL_1
        else -> CalibrationLevel.LEVEL_2
    }

    companion object {
        val NONE = CalibrationResult(emptyMap(), emptyMap(), null)
    }
}

class CalibrationEngine(bundle: EvidenceBundle) {

    private val e2 = E2CurveEngine(bundle)
    private val tt = TtSuppressionEngine(bundle)

    fun calibrate(
        doses: List<DoseEvent>,
        labs: List<LabResult>,
        status: GonadalStatus = GonadalStatus.INTACT,
        baselineT: Double? = null,
    ): CalibrationResult {
        val taken = doses.filter { it.status.wasTaken }
        val estrogen = taken.filterNot { it.drug.isAntiandrogen }
        val e2Results = LinkedHashMap<UUID, AnalyteCalibration>()
        val ratiosByGroup = HashMap<CalibrationGroup, MutableList<Pair<UUID, Double>>>()

        for (lab in labs) {
            val value = lab.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL } ?: continue
            val result = e2Analysis(lab, value, taken, estrogen)
            e2Results[lab.id] = result
            if (result.used) ratiosByGroup.getOrPut(result.group!!) { ArrayList() }.add(lab.id to result.ratio!!)
        }

        val groups = ratiosByGroup.mapValues { (group, ratios) ->
            val logs = ratios.map { ln(it.second) }
            val raw = exp(logs.average())
            val scale = raw.coerceIn(SCALE_MIN, SCALE_MAX)
            val spread = if (logs.size >= 2) {
                val mean = logs.average()
                sqrt(logs.sumOf { (it - mean) * (it - mean) } / (logs.size - 1))
            } else {
                0.0
            }
            GroupCalibration(group, scale, ratios.size, atLimit = scale != raw, spread = spread)
        }
        val adjustments = CalibrationResult(emptyMap(), groups, null).adjustments

        val ttResults = LinkedHashMap<UUID, AnalyteCalibration>()
        val ttRatios = ArrayList<Pair<UUID, Double>>()
        if (status != GonadalStatus.DECLINED) {
            for (lab in labs) {
                val value = lab.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE } ?: continue
                val result = ttAnalysis(lab, value, taken, estrogen, status, adjustments, baselineT)
                ttResults[lab.id] = result
                if (result.used) ttRatios += lab.id to result.ratio!!
            }
        }
        var ttCalibration: TtCalibration? = null
        if (ttRatios.size >= TT_MIN_LABS) {
            val raw = exp(ttRatios.map { ln(it.second) }.average())
            val scale = raw.coerceIn(SCALE_MIN, SCALE_MAX)
            ttCalibration = TtCalibration(scale, ttRatios.size, atLimit = scale != raw)
        } else {
            // One usable T lab is not enough to correct a curve; say so instead of dropping it silently.
            for ((id, _) in ttRatios) ttResults[id] = ttResults.getValue(id).copy(used = false, exclusion = LabExclusion.TOO_FEW_LABS, ratio = null)
        }

        val perLab = LinkedHashMap<UUID, LabCalibration>()
        for (lab in labs) {
            if (lab.id in e2Results || lab.id in ttResults) perLab[lab.id] = LabCalibration(lab.id, e2Results[lab.id], ttResults[lab.id])
        }
        return CalibrationResult(perLab, groups, ttCalibration)
    }

    private fun e2Analysis(lab: LabResult, value: LabAnalyteValue, taken: List<DoseEvent>, estrogen: List<DoseEvent>): AnalyteCalibration {
        val t = lab.collectedAt
        val measured = canonical(value)
        val early = earlyExclusion(lab, t, measured, estrogen, taken)
        if (early != null) return excluded(early)
        t!!
        val expected = e2.estimateAt(taken, t)
        if (expected == null || expected.median < MIN_EXPECTED) return excluded(LabExclusion.LOW_PREDICTION, expected)

        val shares = HashMap<CalibrationGroup, Double>()
        for (d in estrogen) {
            val elapsed = Duration.between(d.occurredAt, t).toMillis() / MILLIS_PER_DAY
            val g = d.route.calibrationGroup() ?: continue
            if (elapsed > 0) shares.merge(g, e2.contributionOf(d, elapsed) ?: 0.0, Double::plus)
        }
        val total = shares.values.sum()
        val dominant = shares.maxByOrNull { it.value }
        if (total <= 0.0 || dominant == null) return excluded(LabExclusion.NO_MODEL, expected)
        if (dominant.value / total < DOMINANT_SHARE) return excluded(LabExclusion.MIXED_ROUTES, expected)

        val baseline = E2CurveEngine.DEFAULT_BASELINE
        val ratio = max(measured!! - baseline, MIN_CONTRIBUTION) / max(expected.median - baseline, MIN_CONTRIBUTION)
        return AnalyteCalibration(used = true, exclusion = null, expected = expected, ratio = ratio, group = dominant.key)
    }

    private fun ttAnalysis(
        lab: LabResult,
        value: LabAnalyteValue,
        taken: List<DoseEvent>,
        estrogen: List<DoseEvent>,
        status: GonadalStatus,
        adjustments: Adjustments,
        baselineT: Double?,
    ): AnalyteCalibration {
        val t = lab.collectedAt
        val measured = canonical(value)
        val early = earlyExclusion(lab, t, measured, estrogen.ifEmpty { taken }, taken)
        if (early != null) return excluded(early)
        t!!
        val expected = tt.estimateAt(taken, t, status, adjustments, baselineT) ?: return excluded(LabExclusion.NO_MODEL)
        if (expected.median <= 0.0 || measured!! <= 0.0) return excluded(LabExclusion.LOW_PREDICTION, expected)
        return AnalyteCalibration(used = true, exclusion = null, expected = expected, ratio = measured / expected.median, group = null)
    }

    /** The exclusions shared by every analyte: when, in what unit, and how much history lies behind the draw. */
    private fun earlyExclusion(
        lab: LabResult,
        t: Instant?,
        measured: Double?,
        history: List<DoseEvent>,
        taken: List<DoseEvent>,
    ): LabExclusion? {
        if (lab.isBaseline) return LabExclusion.BASELINE
        if (t == null) return LabExclusion.NO_COLLECTION_TIME
        if (measured == null) return LabExclusion.UNKNOWN_UNIT
        val before = history.filter { it.occurredAt.isBefore(t) }
        if (before.isEmpty()) return LabExclusion.NO_PRIOR_DOSE
        if (Duration.between(before.minOf { it.occurredAt }, t) < MIN_HISTORY) return LabExclusion.TOO_EARLY
        if (before.none { e2.exclusionsIn(listOf(it)).isEmpty() }) return LabExclusion.NO_MODEL
        val lookback = t.minus(UNMODELLED_LOOKBACK)
        if (e2.exclusionsIn(taken.filter { it.occurredAt.isBefore(t) && it.occurredAt.isAfter(lookback) }).isNotEmpty()) {
            return LabExclusion.MIXED_UNMODELLED
        }
        return null
    }

    private fun canonical(v: LabAnalyteValue): Double? =
        (v.canonicalValue ?: LabAnalyteValue.canonical(v.analyte, v.reportedValue, v.reportedUnit))?.takeIf { it.isFinite() }

    private fun excluded(reason: LabExclusion, expected: EstimatePoint? = null) =
        AnalyteCalibration(used = false, exclusion = reason, expected = expected, ratio = null, group = null)

    companion object {
        /** The correction may move a curve by at most this much either way. */
        const val SCALE_MIN = 0.5
        const val SCALE_MAX = 2.0

        private const val TT_MIN_LABS = 2
        private const val MIN_EXPECTED = 15.0
        private const val MIN_CONTRIBUTION = 1.0
        private const val DOMINANT_SHARE = 0.7
        private const val MILLIS_PER_DAY = 86_400_000.0
        private val MIN_HISTORY: Duration = Duration.ofDays(14)
        private val UNMODELLED_LOOKBACK: Duration = Duration.ofDays(21)
    }
}
