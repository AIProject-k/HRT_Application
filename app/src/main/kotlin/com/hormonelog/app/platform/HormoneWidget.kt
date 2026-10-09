package com.hormonelog.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import com.hormonelog.app.MainActivity
import com.hormonelog.app.R
import com.hormonelog.app.analysis.WidgetContent
import com.hormonelog.app.analysis.WidgetLogic
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.ThemeMode
import java.time.Instant
import java.time.ZoneId

/**
 * The home-screen widget: the next dose and, when one is due, a button that records it without
 * opening the app. It shows a time and a drug at most — never a lab value — and in disguise only
 * "할 일" and a time.
 */
class HormoneWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val read = Storage.read(context)
        val views = if (read == null) unreadable(context) else build(context, read.first.regimens, read.first.doses, read.second)
        manager.updateAppWidget(appWidgetIds, views)
    }

    companion object {
        /** Redraws every placed widget from the records the caller already has in memory. */
        fun refresh(context: Context, regimens: List<Regimen>, doses: List<DoseEvent>, settings: AppSettings) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HormoneWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, build(context, regimens, doses, settings))
        }

        private fun isDark(context: Context, mode: ThemeMode): Boolean = when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        private fun build(context: Context, regimens: List<Regimen>, doses: List<DoseEvent>, settings: AppSettings): RemoteViews {
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val content = WidgetLogic.of(regimens, doses, settings, now, zone, Fmt(zone, settings.clock24, now))
            return render(context, content, isDark(context, settings.themeMode))
        }

        private fun unreadable(context: Context): RemoteViews =
            render(context, WidgetContent("할 일", "앱을 열어 확인해 주세요", null, null), dark = true)

        private fun render(context: Context, content: WidgetContent, dark: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_dose)
            val text = if (dark) 0xFFE9ECF3.toInt() else 0xFF151820.toInt()
            val muted = if (dark) 0xFFB0B6C4.toInt() else 0xFF454C5C.toInt()
            val onAccent = if (dark) 0xFF0A2622.toInt() else 0xFFFFFFFF.toInt()
            views.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
            views.setInt(R.id.widget_action, "setBackgroundResource", if (dark) R.drawable.widget_button_dark else R.drawable.widget_button_light)
            views.setTextViewText(R.id.widget_label, content.label)
            views.setTextColor(R.id.widget_label, muted)
            views.setTextViewText(R.id.widget_title, content.title)
            views.setTextColor(R.id.widget_title, text)
            views.setTextViewText(R.id.widget_detail, content.detail.orEmpty())
            views.setTextColor(R.id.widget_detail, muted)
            views.setViewVisibility(R.id.widget_detail, if (content.detail == null) View.GONE else View.VISIBLE)

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            if (content.action != null && content.regimenId != null && content.subjectMillis != null) {
                views.setViewVisibility(R.id.widget_action, View.VISIBLE)
                views.setTextViewText(R.id.widget_action, content.action)
                views.setTextColor(R.id.widget_action, onAccent)
                val done = Intent(context, ReminderReceiver::class.java).setAction(Reminders.ACTION_DONE)
                    .putExtra(Reminders.EXTRA_REGIMEN, content.regimenId.toString())
                    .putExtra(Reminders.EXTRA_SUBJECT, content.subjectMillis)
                views.setOnClickPendingIntent(
                    R.id.widget_action,
                    PendingIntent.getBroadcast(context, 1, done, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                views.setViewVisibility(R.id.widget_action, View.GONE)
            }
            return views
        }
    }
}
