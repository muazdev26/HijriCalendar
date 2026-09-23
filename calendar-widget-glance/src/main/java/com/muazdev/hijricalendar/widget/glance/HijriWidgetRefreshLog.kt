package com.muazdev.hijricalendar.widget.glance

import android.util.Log

/**
 * Single diagnostic channel for the widget refresh pipeline.
 *
 * Ticket 04 asked for the actual "not refreshing" root cause to be confirmed from logs before
 * behaviour changed. Every refresh trigger now funnels through [HijriWidgetRefresher] and logs
 * its decision here — foreground state, same-day dedupe marker, pending catch-up and the exact
 * alarm arm time — so a device pass can tell which of the three was losing the render.
 *
 * Filter with: `adb logcat -s HijriWidgetRefresh`
 */
internal object HijriWidgetRefreshLog {

    const val TAG = "HijriWidgetRefresh"

    fun d(reason: String, message: String) {
        Log.d(TAG, "[$reason] $message")
    }
}
