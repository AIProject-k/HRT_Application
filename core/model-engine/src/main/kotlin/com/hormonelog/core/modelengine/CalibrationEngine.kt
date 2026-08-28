package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.evidence.EvidenceBundle
import java.util.UUID
import kotlin.math.exp
import kotlin.math.ln

enum class CalibrationLevel { LEVEL_0, LEVEL_1, LEVEL_2 }

/**
 * Level 0–2 calibration: a conservative *exposure-scale* adjustment only (설계서
 * §4.4). Eligible E2 labs are compared to the population prediction at their
 * collection time; the geometric-mean ratio (clamped) multiplies the E2 curve's
 * drug contribution. Curve-shape / personal PK fitting is Level 3+ and not done here.
 */
data class CalibrationResult(
    val level: CalibrationLevel,
    val exposureScale: Double,
    val includedLabIds: Set<UUID>,
    val excluded: Map<UUID, String>,
) {
    companion object {
        val NONE = CalibrationResult(CalibrationLevel.LEVEL_0, 1.0, emptySet(), emptyMap())
    }
}

class CalibrationEngine(private val bundle: EvidenceBundle) {

    private val e2 = E2CurveEngine(bundle)

    fun calibrate(doses: List<DoseEvent>, labs: List<LabResult>): CalibrationResult {
        val estrogenDoses = doses.filter {
            (it.status == DoseStatus.ADMINISTERED || it.status == DoseStatus.CORRECTED) &&
                it.drug != Drug.SPIRONOLACTONE && it.drug != Drug.CYPROTERONE
        }
        val supportedRoute = estrogenDoses.map { it.route }.any(bundle::supports)

        val ratios = LinkedHashMap<UUID, Double>()
        val excluded = LinkedHashMap<UUID, String>()
        for (lab in labs) {
            val t = lab.collectedAt
            val measured = lab.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }
                ?.let { it.canonicalValue ?: it.reportedValue }
            when {
                t == null -> excluded[lab.id] = "수집 시각 없음"
                measured == null -> excluded[lab.id] = "E2 값 없음"
                estrogenDoses.none { it.occurredAt.isBefore(t) } -> excluded[lab.id] = "이전 투약 기록 없음"
                !supportedRoute -> excluded[lab.id] = "지원 모델 없음"
                else -> {
                    val predicted = e2.medianAt(doses, t)
                    if (predicted == null || predicted < 15.0) {
                        excluded[lab.id] = "예측값 산출 불가"
                    } else {
                        ratios[lab.id] = measured / predicted
                    }
                }
            }
        }
        if (ratios.isEmpty()) return CalibrationResult(CalibrationLevel.LEVEL_0, 1.0, emptySet(), excluded)

        val geomean = exp(ratios.values.sumOf { ln(it) } / ratios.size)
        val scale = geomean.coerceIn(0.5, 2.0)
        val level = if (ratios.size >= 2) CalibrationLevel.LEVEL_2 else CalibrationLevel.LEVEL_1
        return CalibrationResult(level, scale, ratios.keys, excluded)
    }
}
