package com.hormonelog.app.analysis

import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.Regimen
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * What the home-screen widget shows. It never carries a lab value, and in disguise it names no
 * drug and not the app: only "할 일" and a time.
 */
data class WidgetContent(
    val label: String,
    val title: String,
    val detail: String?,
    /** The button's text; null when there is nothing that could be completed right now. */
    val action: String?,
    val regimenId: UUID? = null,
    val subjectMillis: Long? = null,
)

object WidgetLogic {
    /** A dose further ahead than this is not something to confirm yet, so the widget offers no button for it. */
    const val COMPLETE_WITHIN_HOURS = 12L

    fun of(regimens: List<Regimen>, doses: List<DoseEvent>, settings: AppSettings, now: Instant, zone: ZoneId, fmt: Fmt): WidgetContent {
        val neutral = settings.neutralNotifications || settings.disguiseLauncher
        val next = HomeLogic.nextDose(regimens, doses, now, zone)
        val label = if (neutral) "메모 · 할 일" else "호르몬로그 · 다음 투약"
        if (next == null) {
            return WidgetContent(label, if (neutral) "할 일이 없어요" else "예정된 투약이 없어요", if (neutral) null else "앱에서 반복 일정을 만들어 보세요", null)
        }
        val soon = !next.at.isAfter(now.plusSeconds(COMPLETE_WITHIN_HOURS * 3600L))
        val r = next.regimen
        return if (neutral) {
            WidgetContent(label, "${neutralDay(next.at, now, zone, fmt)} ${fmt.time(next.at)}", null, if (soon) "완료" else null, r.id, next.at.toEpochMilli())
        } else {
            val summary = doseSummary(r.drug, r.route, r.amountEntered, r.enteredUnit)
            WidgetContent(
                label, "${fmt.dateDay(next.at)} ${fmt.time(next.at)}",
                "$summary · ${if (next.overdue) "예정 시각이 지났어요" else fmt.ago(next.at)}",
                if (soon) "투약 완료" else null, r.id, next.at.toEpochMilli(),
            )
        }
    }

    private fun neutralDay(at: Instant, now: Instant, zone: ZoneId, fmt: Fmt): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), at.atZone(zone).toLocalDate())
        return when {
            days <= 0 -> "오늘"
            days == 1L -> "내일"
            else -> "${fmt.weekday(at)}요일"
        }
    }
}
