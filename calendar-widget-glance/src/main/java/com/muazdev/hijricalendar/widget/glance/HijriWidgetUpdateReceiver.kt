package com.muazdev.hijricalendar.widget.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Responds to the system broadcasts that can invalidate the widget family's day/today state:
 * manual clock changes, timezone changes, locale changes and boot completion.
 *
 * These change the rendered content independently of the day marker (the local date, the
 * language direction), so they bypass the same-day dedupe marker. They still respect the
 * foreground gate: if the app is on screen the re-render is deferred and recorded as pending,
 * then applied when the app leaves the foreground.
 *
 * Note: `ACTION_DATE_CHANGED` is intentionally NOT listed here - it is not exempt from the
 * API 26+ implicit-broadcast manifest ban, so midnight rollover is instead handled by the
 * exact AlarmManager alarm.
 *
 * On boot the process starts via [BOOT_COMPLETED], so the host application's `onCreate`
 * has already re-armed the midnight alarm before this receiver runs; we only need to catch the
 * widget up.
 */
internal class HijriWidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_LOCALE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED
        ) {
            HijriWidgetRefreshScheduler.armMidnightAlarm(context)
            HijriWidgetRefresher.refreshAllAsync(
                context,
                reason = "system:$action",
                bypassDedupe = true,
            )
        }
    }
}
