package com.hormonelog.core.modelengine

import kotlin.math.abs
import kotlin.math.exp

/**
 * Closed-form 3-compartment first-order absorption/elimination model, ported from
 * estrannaise.js `e2Curve3C` (MIT, © 2025 alix). Time in days, dose in mg
 * (µg/day for patch), concentration in pg/mL:
 *
 * `C(t) = dose·d·k1·k2·[ e^-k1t/((k1-k2)(k1-k3)) − e^-k2t/((k1-k2)(k2-k3)) + e^-k3t/((k1-k3)(k2-k3)) ]`
 *
 * Distinct k1/k2/k3 are required; near-equal rates are nudged apart so the closed
 * form stays finite (the bundled estrannaise parameters are already distinct).
 */
object E2ThreeCompartmentModel {

    fun concentration(t: Double, dose: Double, d: Double, k1In: Double, k2In: Double, k3In: Double): Double {
        if (t <= 0.0 || dose <= 0.0 || d <= 0.0) return 0.0
        val (k1, k2, k3) = separate(k1In, k2In, k3In)
        val bracket =
            exp(-k1 * t) / (k1 - k2) / (k1 - k3) -
                exp(-k2 * t) / (k1 - k2) / (k2 - k3) +
                exp(-k3 * t) / (k1 - k3) / (k2 - k3)
        val ret = dose * d * k1 * k2 * bracket
        return if (ret.isNaN() || ret < 0.0) 0.0 else ret
    }

    private fun separate(a: Double, b: Double, c: Double): Triple<Double, Double, Double> {
        val eps = 1e-4
        var y = b
        var z = c
        if (abs(a - y) < eps) y += eps
        if (abs(a - z) < eps) z += 2 * eps
        if (abs(y - z) < eps) z += 3 * eps
        return Triple(a, y, z)
    }
}
