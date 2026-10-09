package com.hormonelog.app.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.hormonelog.app.MainActivity
import com.hormonelog.app.R
import com.hormonelog.app.analysis.Reminder
import com.hormonelog.app.analysis.ReminderKind
import com.hormonelog.app.analysis.ReminderPlan
import com.hormonelog.app.analysis.ReminderText
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.state.AppState
import com.hormonelog.core.data.RecordSnapshot
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/**
 * Dose, lab and visit reminders. One alarm is kept set for the next reminder; when it fires the
 * reminders that are due are shown and the alarm is moved on. Everything that decides *what*
 * is due lives in [ReminderPlan]; this file only knows how to ask Android to wake us and to
 * show the result.
 *
 * The alarm is the inexact, Doze-friendly kind: it can arrive a few minutes late, but it needs no
 * special permission and does not drain the battery.
 */
object Reminders {
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST_NEXT = 100
    private const val SNOOZE_MINUTES = 60L

    /** How far back a firing alarm still counts a reminder as due (a Doze delay can be several minutes). */
    private const val LATE_TOLERANCE_SECONDS = 15L * 60L

    const val ACTION_FIRE = "com.hormonelog.app.action.REMINDER_FIRE"
    const val ACTION_DONE = "com.hormonelog.app.action.REMINDER_DONE"
    const val ACTION_LATER = "com.hormonelog.app.action.REMINDER_LATER"
    const val ACTION_SNOOZED = "com.hormonelog.app.action.REMINDER_SNOOZED"
    const val EXTRA_REGIMEN = "regimen"
    const val EXTRA_SUBJECT = "subject"
    const val EXTRA_KIND = "kind"
    const val EXTRA_AT = "at"

    /** Records or settings changed: line the alarm, the shown notifications and the widget up with them. */
    fun update(context: Context, state: AppState) {
        val app = context.applicationContext
        scheduleNext(app, state.snapshot, state.settings, Instant.now())
        dismissAnswered(app, state.snapshot)
        HormoneWidget.refresh(app, state.regimens, state.doses, state.settings)
    }

    /** The same from what is on disk, for a boot, a clock change or a fired alarm. */
    fun refreshFromDisk(context: Context, from: Instant = Instant.now()) {
        val app = context.applicationContext
        val (snapshot, settings) = Storage.read(app) ?: return
        scheduleNext(app, snapshot, settings, from)
        HormoneWidget.refresh(app, snapshot.regimens, snapshot.doses, settings)
    }

    private fun scheduleNext(context: Context, snapshot: RecordSnapshot, settings: AppSettings, from: Instant) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = firePending(context)
        val next = ReminderPlan.next(snapshot.regimens, snapshot.doses, snapshot.labs, snapshot.nextVisitMillis, settings, from, ZoneId.systemDefault())
        if (next == null) {
            alarm.cancel(pending)
            return
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at.toEpochMilli(), pending)
    }

    private fun firePending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_NEXT,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The alarm went off: show what is due and move the alarm to the next reminder. */
    internal fun onFire(context: Context) {
        val (snapshot, settings) = Storage.read(context) ?: return
        val now = Instant.now()
        val due = ReminderPlan.between(
            snapshot.regimens, snapshot.doses, snapshot.labs, snapshot.nextVisitMillis, settings,
            now.minusSeconds(LATE_TOLERANCE_SECONDS), now.plusSeconds(30), ZoneId.systemDefault(),
        )
        due.forEach { post(context, it, settings, now) }
        // Start looking after the window just handled, or the same reminder would be set again seconds later.
        scheduleNext(context, snapshot, settings, now.plusSeconds(30))
        HormoneWidget.refresh(context, snapshot.regimens, snapshot.doses, settings)
    }

    /** "나중에": the same reminder again in an hour. */
    internal fun onLater(context: Context, intent: Intent) {
        cancel(context, intent.getStringExtra(EXTRA_KIND), intent)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val again = Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZED).putExtras(intent.extras ?: Bundle())
        val code = (intent.getStringExtra(EXTRA_KIND).orEmpty() + intent.getLongExtra(EXTRA_SUBJECT, 0)).hashCode()
        val pending = PendingIntent.getBroadcast(context, code, again, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, pending)
    }

    /** A snoozed reminder is due again. */
    internal fun onSnoozed(context: Context, intent: Intent) {
        val (snapshot, settings) = Storage.read(context) ?: return
        val reminder = rebuild(snapshot, intent) ?: return
        // Taken in the meantime: nothing to remind about.
        if (reminder.regimen != null && alreadyTaken(snapshot, reminder.regimen, reminder.subject)) return
        post(context, reminder, settings, Instant.now())
    }

    /** "투약 완료" from a notification or the widget: record the dose at its planned time, without opening the app. */
    internal fun onDone(context: Context, intent: Intent) {
        val regimenId = runCatching { UUID.fromString(intent.getStringExtra(EXTRA_REGIMEN)) }.getOrNull() ?: return
        val subject = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_SUBJECT, 0L))
        val (snapshot, settings) = Storage.read(context) ?: return
        val regimen = snapshot.regimens.firstOrNull { it.id == regimenId } ?: return
        val now = Instant.now()
        // A dose is something that has happened: if its planned time is still ahead, record it now.
        val at = if (subject.isAfter(now)) now else subject
        if (!alreadyTaken(snapshot, regimen, at)) {
            val dose = DoseEvent(
                id = UUID.randomUUID(),
                occurredAt = at,
                sourceZoneId = ZoneId.systemDefault().id,
                drug = regimen.drug,
                route = regimen.route,
                amountEntered = regimen.amountEntered,
                enteredUnit = regimen.enteredUnit,
                normalizedMilligrams = DoseEvent.normalizeMilligrams(regimen.amountEntered, regimen.enteredUnit),
                status = DoseStatus.ADMINISTERED,
                source = RecordSource.SCHEDULE,
                patchCycle = regimen.patchCycle,
            )
            val vm = ViewModelHolder.current
            if (vm != null) {
                // The open app owns the records; it writes them itself so nothing is overwritten from behind its back.
                Handler(Looper.getMainLooper()).post { vm.recordExternalDose(dose) }
            } else {
                Storage.appendDose(context, dose)
            }
        }
        cancel(context, intent.getStringExtra(EXTRA_KIND), intent)
        if (ViewModelHolder.current == null) refreshFromDisk(context)
    }

    // ── notifications ─────────────────────────────────────────

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True when the system would show a reminder; the settings screen warns when it would not. */
    fun canNotify(context: Context): Boolean {
        if (!canPost(context)) return false
        return context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != false
    }

    private fun ensureChannel(context: Context, neutral: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // The channel's name is visible in the phone's settings, so it follows the disguise too.
        val name = if (neutral) context.getString(R.string.notification_channel_name) else "투약·일정 알림"
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH))
    }

    private fun tag(r: Reminder): String = r.key

    private fun post(context: Context, r: Reminder, settings: AppSettings, now: Instant) {
        if (!canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val neutral = settings.neutralNotifications || settings.disguiseLauncher
        val text = ReminderText.of(r, neutral, Fmt(ZoneId.systemDefault(), settings.clock24, now))
        ensureChannel(context, neutral)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(text.title)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setWhen(r.at.toEpochMilli())
        text.body?.let { builder.setContentText(it) }
        if (settings.hideOnLockScreen) {
            // On a locked screen only the neutral line shows, whatever the full text is.
            builder.setVisibility(Notification.VISIBILITY_PRIVATE)
            builder.setPublicVersion(
                Notification.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_reminder).setContentTitle(ReminderText.LOCK_SCREEN_TITLE).build(),
            )
        }
        // The header's app name is the manifest label and cannot be changed per notification: the permission that
        // would allow a substitute name is for system apps only. The wording and the icon are what disguise can change.
        if (r.canComplete && text.done != null && text.later != null) {
            builder.addAction(action(context, text.done, ACTION_DONE, r))
            builder.addAction(action(context, text.later, ACTION_LATER, r))
        }
        nm.notify(tag(r), NOTIFICATION_ID, builder.build())
    }

    private fun action(context: Context, label: String, action: String, r: Reminder): Notification.Action {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action).apply {
            putExtra(EXTRA_REGIMEN, r.regimen?.id?.toString())
            putExtra(EXTRA_SUBJECT, r.subject.toEpochMilli())
            putExtra(EXTRA_KIND, r.kind.name)
            putExtra(EXTRA_AT, r.at.toEpochMilli())
        }
        val code = (action + r.key).hashCode()
        val pending = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_reminder), label, pending).build()
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Takes a notification down by the key it was shown with (rebuilt from the intent's extras). */
    private fun cancel(context: Context, kind: String?, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val kindName = kind ?: return
        val key = "$kindName:${intent.getStringExtra(EXTRA_REGIMEN)}:${intent.getLongExtra(EXTRA_SUBJECT, 0L)}"
        nm.cancel(key, NOTIFICATION_ID)
    }

    /** A dose logged by hand takes down the reminders that were waiting for it. */
    private fun dismissAnswered(context: Context, snapshot: RecordSnapshot) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        for (n in nm.activeNotifications) {
            if (n.id != NOTIFICATION_ID) continue
            val parts = n.tag?.split(':') ?: continue
            if (parts.size != 3 || parts[0] !in DOSE_KINDS) continue
            val regimen = snapshot.regimens.firstOrNull { it.id.toString() == parts[1] } ?: continue
            val subject = parts[2].toLongOrNull()?.let(Instant::ofEpochMilli) ?: continue
            if (alreadyTaken(snapshot, regimen, subject)) nm.cancel(n.tag, NOTIFICATION_ID)
        }
    }

    private val DOSE_KINDS = setOf(ReminderKind.INJECTION_DAY, ReminderKind.INJECTION_EVE, ReminderKind.DAILY, ReminderKind.DAILY_REPEAT).map { it.name }.toSet()

    /** Whether a real dose of this plan already covers [at] (the same reach the forecast uses). */
    private fun alreadyTaken(snapshot: RecordSnapshot, regimen: Regimen, at: Instant): Boolean {
        val reach = (regimen.intervalDays * 86_400_000.0 / 2).toLong()
        return snapshot.doses.any { regimen.covers(it) && abs(it.occurredAt.toEpochMilli() - at.toEpochMilli()) < reach }
    }

    private fun rebuild(snapshot: RecordSnapshot, intent: Intent): Reminder? {
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { k -> ReminderKind.entries.firstOrNull { it.name == k } } ?: return null
        val subject = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_SUBJECT, 0L))
        val at = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_AT, subject.toEpochMilli()))
        val regimen = intent.getStringExtra(EXTRA_REGIMEN)?.let { id -> snapshot.regimens.firstOrNull { it.id.toString() == id } }
        if (kind in listOf(ReminderKind.INJECTION_DAY, ReminderKind.DAILY, ReminderKind.DAILY_REPEAT) && regimen == null) return null
        return Reminder(at, kind, subject, regimen)
    }

    /** Opens the app's notification settings, for the user who has turned reminders off in the system. */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (resolveActivity(context.packageManager) == null) data = Uri.fromParts("package", context.packageName, null) }
}

/**
 * Receives the alarm, the notification buttons, and the system broadcasts after which the alarm
 * has to be set again (a reboot, an update, the clock or the time zone changing).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    Reminders.ACTION_FIRE -> Reminders.onFire(app)
                    Reminders.ACTION_DONE -> Reminders.onDone(app, intent)
                    Reminders.ACTION_LATER -> Reminders.onLater(app, intent)
                    Reminders.ACTION_SNOOZED -> Reminders.onSnoozed(app, intent)
                    else -> Reminders.refreshFromDisk(app)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
