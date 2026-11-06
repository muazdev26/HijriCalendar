package com.muazdev.hijricalendar.widget.glance

import android.util.Log

/**
 * Single diagnostic channel for the widget refresh pipeline.
 *
 * The actual "not refreshing" root cause has to be confirmed from logs before behaviour changes.
 * Every refresh trigger therefore funnels through [HijriWidgetRefresher] and logs
 * its decision here — foreground state, same-day dedupe marker, pending catch-up and the exact
 * alarm arm time — so a device pass can tell which of the three was losing the render.
 *
 * Filter with: `adb logcat -s HijriWidgetRefresh`
 */
public object HijriWidgetRefreshLog {

    public const val TAG: String = "HijriWidgetRefresh"

    public fun d(reason: String, message: String) {
        Log.d(TAG, "[$reason] $message")
    }

    public fun e(reason: String, message: String, t: Throwable? = null) {
        Log.e(TAG, "[$reason] $message", t)
    }
}
