package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.Drug
import kotlin.math.exp

/**
 * Cyproterone acetate plasma level in arbitrary "level" units (folded into the
 * suppression EC50). One-compartment oral first-order absorption, superposed over
 * daily doses. t½ ~1.9 d, F ~88% (tertiary sources).
 */
class CpaModel(f: Double, private val ka: Double, private val ke: Double) {
    private val f = f.coerceIn(0.1, 1.0)

    /** Level contributed by all CPA doses in [doses] at [at] (epoch millis). */
    fun levelAt(doses: List<DoseEvent>, atMillis: Long): Double {
        var level = 0.0
        for (dprime in doses) {
            if (dprime.drug != Drug.CYPROTERONE) continue
            val elapsed = (atMillis - dprime.occurredAt.toEpochMilli()) / 86_400_000.0
            if (elapsed <= 0.0) continue
            val mg = dprime.normalizedMilligrams ?: dprime.amountEntered
            val v = exp(-ke * elapsed) - exp(-ka * elapsed)
            if (v > 0.0) level += f * mg * v
        }
        return level
    }
}
