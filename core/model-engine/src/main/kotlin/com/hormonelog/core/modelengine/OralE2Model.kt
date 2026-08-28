package com.hormonelog.core.modelengine

import kotlin.math.exp

/** One-compartment first-order absorption: C(t) = scale·dose·(e^-ke·t − e^-ka·t). */
object OralE2Model {
    fun concentration(t: Double, dose: Double, scale: Double, ka: Double, ke: Double): Double {
        if (t <= 0.0 || dose <= 0.0) return 0.0
        val v = exp(-ke * t) - exp(-ka * t)
        return if (v <= 0.0) 0.0 else scale * dose * v
    }
}

/** Sublingual bolus (direct mucosal fraction) plus a swallowed fraction routed through the oral model. */
object SublingualE2Model {
    fun concentration(
        t: Double,
        dose: Double,
        slScale: Double,
        slKa: Double,
        slKe: Double,
        swallowed: Double,
        oralScale: Double,
        oralKa: Double,
        oralKe: Double,
    ): Double {
        val s = swallowed.coerceIn(0.0, 0.9)
        val direct = OralE2Model.concentration(t, dose * (1 - s), slScale, slKa, slKe)
        val gut = OralE2Model.concentration(t, dose * s, oralScale, oralKa, oralKe)
        return direct + gut
    }
}
