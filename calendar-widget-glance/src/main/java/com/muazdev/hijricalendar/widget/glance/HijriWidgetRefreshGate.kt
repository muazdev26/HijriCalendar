package com.muazdev.hijricalendar.widget.glance

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks whether any host-app activity is visible so background widget re-renders can be skipped
 * while the user is in the app.
 *
 * Each Glance update enqueues a [androidx.glance.session.SessionWorker]; on slow devices that
 * composition runs on the main thread and steals frames from in-app navigation (several-second
 * stalls). The state is maintained by [HijriWidgetForegroundWatcher] from activity lifecycle
 * callbacks, and the widget catches up as soon as the last activity stops.
 */
internal object HijriWidgetRefreshGate {
    private val appForeground = AtomicBoolean(false)

    fun setAppForeground(value: Boolean) {
        appForeground.set(value)
    }

    fun isAppForeground(): Boolean = appForeground.get()
}
