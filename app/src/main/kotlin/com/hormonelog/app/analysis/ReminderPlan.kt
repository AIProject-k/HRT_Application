package com.hormonelog.app.analysis

import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import java.time.Instant
import java.time.ZoneId

enum class ReminderKind { INJECTION_DAY, INJECTION_EVE, DAILY, DAILY_REPEAT, LAB, APPOINTMENT }

/**
 * One notification the app means to show. [subject] is what it is about: the planned dose time
 * (a "투약 완료" records the dose at that instant), the day a lab is due, or the visit itself.
 */
data class Reminder(
    val at: Instant,
    val kind: ReminderKind,
    val subject: Instant,
    val regimen: Regimen? = null,
) {
    /** Same for the same occurrence however often the plan is recomputed, so a repeat replaces instead of stacking. */
    val key: String get() = "${kind.name}:${regimen?.id}:${subject.toEpochMilli()}"

    /** Only a reminder for a dose can be answered with "투약 완료". */
    val canComplete: Boolean get() = regimen != null && kind != ReminderKind.INJECTION_EVE
}

/**
 * Works out which reminders fall in a stretch of time from the records and the settings. It is a
 * pure function: the same inputs give the same list, and nothing here knows about Android. The
 * scheduler asks for what comes next; a firing alarm asks for what is due right now.
 */
object ReminderPlan {
    const val EVE_HOUR = 21
    const val APPOINTMENT_HOUR = 19
    const val LAB_HOUR = 9
    const val LAB_EVERY_WEEKS = 8L
    const val REPEAT_AFTER_MINUTES = 30L

    /** Injections and patches are changed on a day; tablets, sublingual doses and gel are taken at a time. */
    fun isDayType(r: Regimen): Boolean = r.route == Route.IM_INJECTION || r.route == Route.SC_INJECTION || r.route == Route.PATCH

    /** Reminders with `from < at <= until`, earliest first. */
    fun between(
        regimens: List<Regimen>,
        doses: List<DoseEvent>,
        labs: List<LabResult>,
        nextVisitMillis: Long?,
        settings: AppSettings,
        from: Instant,
        until: Instant,
        zone: ZoneId,
    ): List<Reminder> {
        val out = ArrayList<Reminder>()
        for (r in regimens) {
            if (!r.active) continue
            val day = isDayType(r)
            if (if (day) !settings.notifyInjection else !settings.notifyDaily) continue
            // An occurrence can be reminded about up to a day before it and half an hour after it.
            val occurrences = Regimen.expected(r, from.minusSeconds(3600), until.plusSeconds(2 * 86_400L), doses, zone)
            for (occ in occurrences) {
                if (day) {
                    out += Reminder(occ, ReminderKind.INJECTION_DAY, occ, r)
                    if (r.intervalDays > 1.5) {
                        val eve = occ.atZone(zone).toLocalDate().minusDays(1).atTime(EVE_HOUR, 0).atZone(zone).toInstant()
                        out += Reminder(eve, ReminderKind.INJECTION_EVE, occ, r)
                    }
                } else {
                    out += Reminder(occ, ReminderKind.DAILY, occ, r)
                    out += Reminder(occ.plusSeconds(REPEAT_AFTER_MINUTES * 60), ReminderKind.DAILY_REPEAT, occ, r)
                }
            }
        }
        if (settings.notifyLab) {
            labs.filter { !it.isBaseline }.mapNotNull { it.collectedAt }.maxOrNull()?.let { last ->
                val due = last.atZone(zone).toLocalDate().plusWeeks(LAB_EVERY_WEEKS).atTime(LAB_HOUR, 0).atZone(zone).toInstant()
                out += Reminder(due, ReminderKind.LAB, due)
            }
        }
        if (settings.notifyAppointment && nextVisitMillis != null) {
            val visit = Instant.ofEpochMilli(nextVisitMillis)
            val eve = visit.atZone(zone).toLocalDate().minusDays(1).atTime(APPOINTMENT_HOUR, 0).atZone(zone).toInstant()
            out += Reminder(eve, ReminderKind.APPOINTMENT, visit)
        }
        return out.filter { it.at.isAfter(from) && !it.at.isAfter(until) }.sortedBy { it.at }
    }

    /** The next reminder after [now], looking [horizonDays] ahead. */
    fun next(
        regimens: List<Regimen>, doses: List<DoseEvent>, labs: List<LabResult>, nextVisitMillis: Long?,
        settings: AppSettings, now: Instant, zone: ZoneId, horizonDays: Long = 60,
    ): Reminder? = between(regimens, doses, labs, nextVisitMillis, settings, now, now.plusSeconds(horizonDays * 86_400L), zone).firstOrNull()
}

/** What a reminder says, in the words the user chose: full, or neutral enough to leave on a lock screen. */
data class ReminderText(val title: String, val body: String?, val done: String?, val later: String?) {
    companion object {
        /** The neutral line shown on a locked screen whatever the full text is. */
        const val LOCK_SCREEN_TITLE = "확인할 일정이 있어요"

        fun of(r: Reminder, neutral: Boolean, fmt: Fmt): ReminderText {
            val reg = r.regimen
            val summary = reg?.let { doseSummary(it.drug, it.route, it.amountEntered, it.enteredUnit) }
            return if (neutral) {
                when (r.kind) {
                    ReminderKind.INJECTION_DAY, ReminderKind.DAILY -> ReminderText("오늘 기록할 항목이 있어요", null, "완료", "나중에")
                    ReminderKind.DAILY_REPEAT -> ReminderText("기록할 항목이 남아 있어요", null, "완료", "나중에")
                    ReminderKind.INJECTION_EVE -> ReminderText("내일 기록할 항목이 있어요", null, null, null)
                    ReminderKind.LAB, ReminderKind.APPOINTMENT -> ReminderText(LOCK_SCREEN_TITLE, null, null, null)
                }
            } else {
                when (r.kind) {
                    ReminderKind.INJECTION_DAY -> ReminderText(
                        if (reg?.route == Route.PATCH) "오늘 패치 교체일이에요" else "오늘 주사일이에요", summary, "투약 완료", "나중에",
                    )
                    ReminderKind.INJECTION_EVE -> ReminderText(
                        if (reg?.route == Route.PATCH) "내일 패치 교체일이에요" else "내일 주사일이에요", summary, null, null,
                    )
                    ReminderKind.DAILY -> ReminderText("복용할 시간이에요", summary, "투약 완료", "나중에")
                    ReminderKind.DAILY_REPEAT -> ReminderText("아직 기록이 없어요", summary, "투약 완료", "나중에")
                    ReminderKind.LAB -> ReminderText("검사할 때가 됐어요", "마지막 검사 후 ${ReminderPlan.LAB_EVERY_WEEKS}주 · 다음 주사 직전 채혈이 트로프 값이에요", null, null)
                    ReminderKind.APPOINTMENT -> ReminderText("내일 진료 예약이 있어요", "${fmt.dateDay(r.subject)} ${fmt.time(r.subject)}", null, null)
                }
            }
        }
    }
}
