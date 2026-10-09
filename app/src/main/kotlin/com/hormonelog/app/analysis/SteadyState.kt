package com.hormonelog.app.analysis

import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.state.plainNumber
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.modelengine.CalibrationGroup
import com.hormonelog.core.modelengine.calibrationGroup
import java.time.Instant

/**
 * After a change of dose or of route the blood level needs a while to settle before a lab shows
 * the new regimen. This says so while that wait is still going on. Information, not advice.
 */
data class SteadyStateNotice(val changedAt: Instant, val until: Instant, val text: String)

object SteadyState {

    /** About how long each route takes to reach its new steady level. */
    private fun waitDays(group: CalibrationGroup?): Long = when (group) {
        CalibrationGroup.INJECTION -> 21
        CalibrationGroup.PATCH -> 14
        CalibrationGroup.ORAL -> 7
        null -> 0
    }

    fun find(doses: List<DoseEvent>, now: Instant, fmt: Fmt): SteadyStateNotice? {
        val taken = doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen && it.route.calibrationGroup() != null && !it.occurredAt.isAfter(now) }
            .sortedBy { it.occurredAt }
        val latest = taken.lastOrNull() ?: return null

        // A switch of product (tablet → injection) counts from the first dose of the new one.
        val previousOther = taken.lastOrNull { it.drug != latest.drug || it.route != latest.route }
        if (previousOther != null) {
            val firstOfNew = taken.first { it.occurredAt.isAfter(previousOther.occurredAt) }
            if (firstOfNew.drug == latest.drug && firstOfNew.route == latest.route) {
                notice(firstOfNew, firstOfNew.route.calibrationGroup(), now, fmt,
                    "${doseSummary(previousOther.drug, previousOther.route, previousOther.amountEntered, previousOther.enteredUnit)} → ${doseSummary(latest.drug, latest.route, latest.amountEntered, latest.enteredUnit)}로 바꿨어요.")
                    ?.let { return it }
            }
        }

        // A change of amount of the same product.
        val same = taken.filter { it.drug == latest.drug && it.route == latest.route }
        val lastChangeIndex = (same.size - 1 downTo 1).firstOrNull { same[it].amountEntered != same[it - 1].amountEntered } ?: return null
        val changed = same[lastChangeIndex]
        val before = same[lastChangeIndex - 1]
        return notice(
            changed, changed.route.calibrationGroup(), now, fmt,
            "${product(latest)} ${plainNumber(before.amountEntered)} → ${plainNumber(changed.amountEntered)} ${changed.enteredUnit.label}로 바꿨어요.",
        )
    }

    private fun product(d: DoseEvent): String = if (d.route == com.hormonelog.core.domain.Route.PATCH) "패치" else d.drug.shortLabel

    private fun notice(changed: DoseEvent, group: CalibrationGroup?, now: Instant, fmt: Fmt, what: String): SteadyStateNotice? {
        val days = waitDays(group)
        val until = changed.occurredAt.plusSeconds(days * 86_400)
        if (!until.isAfter(now)) return null
        val weeks = (days / 7).coerceAtLeast(1)
        return SteadyStateNotice(
            changed.occurredAt, until,
            "${fmt.date(changed.occurredAt)}에 $what 새 용량이 검사에 반영되려면 약 ${weeks}주가 걸려요 (${fmt.date(until)}경부터).",
        )
    }
}
