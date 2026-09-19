package com.muazdev.hijricalendar.sample.widget

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks whether the sample activity is visible so widget re-renders can be skipped while the
 * user is actively in the calendar.
 *
 * Each Glance update enqueues a [androidx.glance.session.SessionWorker]; on slow devices that
 * composition runs on the main thread and steals frames from in-app navigation (several-second
 * stalls). The in-app calendar is the source of truth while it is on screen, so the widget can
 * wait for the next background update (midnight alarm, broadcast, or daily worker).
 */
internal object HijriWidgetRefreshGate {
    private val appForeground = AtomicBoolean(false)

    fun setAppForeground(value: Boolean) {
        appForeground.set(value)
    }

    fun isAppForeground(): Boolean = appForeground.get()
}