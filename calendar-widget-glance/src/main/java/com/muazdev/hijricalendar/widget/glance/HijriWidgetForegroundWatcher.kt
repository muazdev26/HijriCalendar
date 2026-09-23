package com.muazdev.hijricalendar.widget.glance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps [HijriWidgetRefreshGate] in sync with the real foreground state of *any* app activity
 * (the calendar and the widget settings screen), counting started activities so an activity
 * swap is not mistaken for leaving the app.
 *
 * The important part is the transition to zero started activities: that is the moment skipped
 * renders are caught up. Previously nothing re-rendered on backgrounding, so a refresh skipped
 * because the app was visible stayed stale until the next midnight alarm or daily worker.
 */
class HijriWidgetForegroundWatcher(
    private val context: Context,
) : Application.ActivityLifecycleCallbacks {

    private val startedActivities = AtomicInteger(0)

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities.incrementAndGet() == 1) {
            HijriWidgetRefreshGate.setAppForeground(true)
            HijriWidgetRefreshLog.d("foreground", "app visible (${activity.javaClass.simpleName})")
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedActivities.decrementAndGet() <= 0) {
            startedActivities.set(0)
            HijriWidgetRefreshGate.setAppForeground(false)
            HijriWidgetRefreshLog.d("foreground", "app backgrounded; scheduling catch-up")
            HijriWidgetRefresher.scheduleCatchUp(context, "app-left-foreground")
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
