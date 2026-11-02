package com.hormonelog.app.analysis

import com.hormonelog.app.state.plainNumber
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.TUnit
import com.hormonelog.core.modelengine.TtSuppressionEngine

/** A Total T value that is most likely a unit slip, what to say about it, and the unit it probably was. */
data class TtUnitWarning(val text: String, val swapTo: TUnit)

/**
 * Total T has no expected value to compare with, unlike E2, so this checks against what a lab can report.
 * [preHrtWithTestes] marks a value the T model takes as its starting point, which must sit in its usable range.
 */
fun ttUnitWarning(value: Double?, unit: TUnit, preHrtWithTestes: Boolean): TtUnitWarning? {
    val v = value?.takeIf { it > 0 && it.isFinite() } ?: return null
    fun ngDl(u: TUnit) = LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, v, u.label) ?: v
    fun otherUnitIn(range: ClosedFloatingPointRange<Double>) = TUnit.entries.firstOrNull { it != unit && ngDl(it) in range }
    val typed = ngDl(unit)
    val usable = TtSuppressionEngine.MEASURED_BASELINE_MIN..TtSuppressionEngine.MEASURED_BASELINE_MAX
    val reportable = 1.0..2000.0
    return when {
        typed < reportable.start -> otherUnitIn(reportable)?.let {
            TtUnitWarning("${plainNumber(v)} ${unit.label}는 검사가 잴 수 있는 값보다 낮아요. 검사지 단위가 ${it.label}인지 확인해 주세요.", it)
        }
        preHrtWithTestes && typed < usable.start -> otherUnitIn(usable)?.let {
            TtUnitWarning("HRT 시작 전 값으로는 낮아요(보통 ${usable.start.toInt()}–${usable.endInclusive.toInt()} ng/dL). 검사지 단위가 ${it.label}인지 확인해 주세요.", it)
        }
        typed > reportable.endInclusive -> otherUnitIn(reportable)?.let {
            TtUnitWarning("${plainNumber(v)} ${unit.label}는 ${typed.toInt()} ng/dL라서 너무 높아요. 검사지 단위가 ${it.label}인지 확인해 주세요.", it)
        }
        else -> null
    }
}
