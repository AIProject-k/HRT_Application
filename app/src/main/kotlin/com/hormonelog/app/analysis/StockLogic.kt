package com.hormonelog.app.analysis

import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.StockItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How long what is left will last, judged by the running plan that uses the same product. */
object StockLogic {
    /** An hour of stock that runs out within this many days is shown as running low. */
    const val LOW_DAYS = 14

    /** The running plan that uses [item]: same drug, route and amount. Null when none does. */
    fun planFor(item: StockItem, regimens: List<Regimen>, now: Instant): Regimen? =
        regimens
            .filter { it.isRunningAt(now) && it.drug == item.drug && it.route == item.route && it.amountEntered == item.amountEntered && it.enteredUnit == item.enteredUnit }
            .minByOrNull { it.intervalDays }

    /** Whole days the stock covers, or null when no plan says how fast it is used. */
    fun daysLeft(item: StockItem, regimens: List<Regimen>, now: Instant): Int? =
        planFor(item, regimens, now)?.let { (item.count * it.intervalDays).toInt() }

    fun runsOutOn(daysLeft: Int, now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate().plusDays(daysLeft.toLong())

    /** "하루에 1개 사용", "7일에 1개 사용". */
    fun usageText(plan: Regimen?, unit: String): String? {
        val days = plan?.intervalDays ?: return null
        val text = if (days <= 1.0) "하루" else "${if (days % 1.0 == 0.0) days.toInt().toString() else "%.1f".format(days)}일"
        return "${text}에 1$unit 사용"
    }
}
