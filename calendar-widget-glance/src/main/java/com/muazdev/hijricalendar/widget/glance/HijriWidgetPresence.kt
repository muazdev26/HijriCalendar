package com.muazdev.hijricalendar.widget.glance

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Whether the user actually has a widget from this family placed in a host.
 *
 * **Why this exists.** Every refresh mechanism the library owns is process-wide: one exact
 * midnight alarm and one daily [HijriWidgetRefreshWorker], both armed from
 * [HijriWidgetRefreshScheduler.schedule] on every `Application.onCreate`. Nothing about a widget
 * that is not on a home screen needs a midnight rollover, yet a user who installed the library
 * (or the sample app) and never placed one still ended up with a pending alarm on the device —
 * which Android surfaces to the user: the status bar shows the next-alarm indicator and Settings
 * > Apps > Special access > Alarms & reminders lists the app. That is the report this check
 * answers: "I never added the widget, so why is the alarm icon there?".
 *
 * **Where the truth comes from.** [AppWidgetManager.getAppWidgetIds] for the provider components
 * this manifest declares — the same call Glance itself uses to enumerate a receiver's instances
 * (`GlanceAppWidgetReceiver.onReceive`, `ACTION_LOCALE_CHANGED`). Bound ids are exactly "placed in
 * a host"; a provider with no instance answers an empty array. All six providers must be asked
 * because any one of them being placed has to keep the *single* shared alarm armed.
 *
 * **Fails open.** A query that throws answers `true`. The two failure directions are not
 * symmetric: wrongly arming costs one alarm that the next lifecycle event reconciles, while
 * wrongly answering `false` silently stops every placed widget from rolling over at midnight —
 * the stale-day failure WG-07/WG-08 spent their budget on. The conservative answer is the one
 * that keeps the widget working.
 */
internal object HijriWidgetPresence {

    /**
     * Every provider this library declares in its own manifest, in picker order.
     *
     * Kept as the one list of the family: a seventh widget added without adding it here means the
     * alarm is scheduled as if that widget did not exist, so it alone never rolls over at midnight
     * while the other six do.
     */
    private val PROVIDERS: List<Class<*>> = listOf(
        HijriCalendarWidgetReceiver::class.java,
        HijriTodayWidgetReceiver::class.java,
        HijriDateWidgetReceiver::class.java,
        GregorianDateWidgetReceiver::class.java,
        HijriDualDateWidgetReceiver::class.java,
        HijriCompactDateWidgetReceiver::class.java,
    )

    /** True when at least one instance of at least one family provider is bound to a host. */
    fun anyPlaced(context: Context): Boolean {
        // `getInstance` is non-null on a real device; a null here means the platform query is
        // unavailable (a stubbed `android.jar` in a JVM test), which is the fail-open case.
        val manager = AppWidgetManager.getInstance(context) ?: return true
        return runCatching {
            PROVIDERS.any { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
            }
        }.getOrElse {
            HijriWidgetRefreshLog.e("presence", "app widget lookup failed: ${it.message}", it)
            true
        }
    }

    /** Total count of all placed instances across all family providers. */
    fun totalPlacedCount(context: Context): Int {
        val manager = AppWidgetManager.getInstance(context) ?: return 1
        return runCatching {
            PROVIDERS.sumOf { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider)).size
            }
        }.getOrElse { 1 }
    }
}
