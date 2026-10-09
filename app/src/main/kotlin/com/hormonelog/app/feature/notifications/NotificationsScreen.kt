package com.hormonelog.app.feature.notifications

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hormonelog.app.R
import com.hormonelog.app.analysis.Reminder
import com.hormonelog.app.analysis.ReminderKind
import com.hormonelog.app.analysis.ReminderPlan
import com.hormonelog.app.analysis.ReminderText
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.platform.Reminders
import com.hormonelog.app.state.AppState
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlBanner
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.HlNotice
import com.hormonelog.app.ui.kit.HlSectionLabel
import com.hormonelog.app.ui.kit.HlSegmented
import com.hormonelog.app.ui.kit.HlSwitchRow
import com.hormonelog.app.ui.kit.HlTextAction
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.kit.SegItem
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import java.time.Instant
import java.util.UUID

class NotificationActions(
    val back: () -> Unit,
    val injection: (Boolean) -> Unit,
    val daily: (Boolean) -> Unit,
    val lab: (Boolean) -> Unit,
    val appointment: (Boolean) -> Unit,
    val hideOnLockScreen: (Boolean) -> Unit,
    val neutral: (Boolean) -> Unit,
)

@Composable
fun NotificationsScreen(s: AppState, fmt: Fmt, actions: NotificationActions) {
    val c = Hl.colors
    val context = LocalContext.current
    val settings = s.settings
    // Re-read the permission each time the screen comes back to the front (the user may have just changed it in the system).
    var resumed by remember { mutableIntStateOf(0) }
    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumed++ }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    val canNotify = remember(resumed, settings.notificationsOn) { Reminders.canNotify(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumed++ }

    /** Turning a reminder on is also when the system is asked for permission to show it. */
    fun toggle(on: Boolean, set: (Boolean) -> Unit) {
        set(on)
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(context)) {
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val running = s.regimens.filter { it.isRunningAt(fmt.now) }
    val dayTimes = running.filter { ReminderPlan.isDayType(it) }.mapNotNull { it.timeMinutes }.distinct()
    val dailyTimes = running.filter { !ReminderPlan.isDayType(it) }.mapNotNull { it.timeMinutes }.distinct()
    val nextLab = s.labs.filter { !it.isBaseline }.mapNotNull { it.collectedAt }.maxOrNull()
        ?.atZone(fmt.zone)?.toLocalDate()?.plusWeeks(ReminderPlan.LAB_EVERY_WEEKS)
    val eveText = fmt.minutes(ReminderPlan.EVE_HOUR * 60)
    val neutralNow = settings.neutralNotifications || settings.disguiseLauncher

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        HlTopBar("알림", actions.back)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 12.dp, bottomInset = true) {
            if (settings.notificationsOn > 0 && !canNotify) {
                HlBanner(HlNotice.Warn, icon = HlIcon.Alert) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        HlText("알림이 꺼져 있어서 울리지 않아요", size = HlSize.t13, weight = FontWeight.Bold, color = c.orange)
                        HlText("휴대폰 설정에서 이 앱의 알림을 켜 주세요.", size = HlSize.t13, lineHeight = 1.5f)
                        HlTextAction("설정 열기", { context.startActivity(Reminders.notificationSettingsIntent(context)) })
                    }
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)) {
                HlSwitchRow(
                    "주사일", settings.notifyInjection, { toggle(it, actions.injection) },
                    subtitle = "당일 ${if (dayTimes.size == 1) fmt.minutes(dayTimes.first()) else "예정 시각"} · 전날 $eveText 미리 알림", minHeight = 64.dp,
                )
                HlDivider()
                HlSwitchRow(
                    "매일 복용", settings.notifyDaily, { toggle(it, actions.daily) },
                    subtitle = "${if (dailyTimes.size == 1) fmt.minutes(dailyTimes.first()) else "예정 시각"} · ${ReminderPlan.REPEAT_AFTER_MINUTES}분 뒤 한 번 더", minHeight = 64.dp,
                )
                HlDivider()
                HlSwitchRow(
                    "검사 주기", settings.notifyLab, { toggle(it, actions.lab) },
                    subtitle = "${ReminderPlan.LAB_EVERY_WEEKS}주마다 · " + (nextLab?.let { "다음 ${it.monthValue}월 ${it.dayOfMonth}일 (${it.dayOfWeek.shortLabel})" } ?: "검사를 기록하면 날짜를 알려드려요"), minHeight = 64.dp,
                )
                HlDivider()
                HlSwitchRow(
                    "진료 예약", settings.notifyAppointment, { toggle(it, actions.appointment) },
                    subtitle = if (s.nextVisitMillis != null) "하루 전 ${fmt.minutes(ReminderPlan.APPOINTMENT_HOUR * 60)}" else "다음 진료 날짜를 정하면 하루 전에 알려드려요", minHeight = 64.dp,
                )
                HlDivider()
                HlSwitchRow(
                    "잠금 화면에서 내용 숨기기", settings.hideOnLockScreen, actions.hideOnLockScreen,
                    subtitle = if (settings.hideOnLockScreen) "잠금 상태에서는 항상 중립 문구" else "잠금 화면에도 알림 내용이 그대로 보여요", minHeight = 64.dp,
                )
            }

            HlSectionLabel("알림 문구", Modifier.padding(top = 4.dp))
            HlSegmented(
                listOf(SegItem(false, "기본 문구"), SegItem(true, "중립 문구")),
                neutralNow, actions.neutral,
            )
            if (settings.disguiseLauncher) {
                HlText("위장 모드가 켜져 있어서 알림은 항상 중립 문구로 보여요. 알림 머리글의 앱 이름은 그대로예요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
            }

            val sample = running.firstOrNull { ReminderPlan.isDayType(it) } ?: sampleRegimen(fmt.now)
            val nine = fmt.now.atZone(fmt.zone).toLocalDate().atTime(9, 0).atZone(fmt.zone).toInstant()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Preview(Reminder(nine, ReminderKind.INJECTION_DAY, nine, sample), neutralNow, "오전 9:00", fmt)
                val due = nextLab ?: fmt.now.atZone(fmt.zone).toLocalDate().plusWeeks(ReminderPlan.LAB_EVERY_WEEKS)
                val at = due.atTime(ReminderPlan.LAB_HOUR, 0).atZone(fmt.zone).toInstant()
                Preview(Reminder(at, ReminderKind.LAB, at), neutralNow, "${due.monthValue}월 ${due.dayOfMonth}일", fmt)
            }
            HlText(
                "‘투약 완료’를 누르면 앱을 열지 않고 예정 시각으로 기록돼요. " +
                    if (settings.hideOnLockScreen) "잠금 화면에는 항상 중립 문구로만 보여요." else "지금은 잠금 화면에도 알림 내용이 보여요.",
                size = HlSize.t12, color = c.muted, lineHeight = 1.5f,
            )
        }
    }
}

private fun sampleRegimen(now: Instant) = Regimen(
    id = UUID.randomUUID(), drug = Drug.ESTRADIOL_VALERATE, route = Route.IM_INJECTION, amountEntered = 5.0, enteredUnit = DoseUnit.MG,
    everyDays = 7, startAt = now, endAt = null,
)

/** How one reminder would look in the shade, with the current wording choice; the header is the app's real name. */
@Composable
private fun Preview(reminder: Reminder, neutral: Boolean, whenText: String, fmt: Fmt) {
    val c = Hl.colors
    val text = ReminderText.of(reminder, neutral, fmt)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.input).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(c.teal))
            HlText("${stringResource(R.string.app_name)} · $whenText", size = HlSize.t12, color = c.muted)
        }
        HlText(text.title, size = HlSize.t14, weight = FontWeight.Bold)
        if (text.body != null) HlText(text.body, size = HlSize.t13, color = c.muted, lineHeight = 1.45f)
        if (text.done != null && text.later != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HlText(text.done, size = HlSize.t13, weight = FontWeight.Bold, color = c.teal, modifier = Modifier.padding(vertical = 8.dp))
                HlText(text.later, size = HlSize.t13, weight = FontWeight.Bold, color = c.teal, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
