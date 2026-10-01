package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.muazdev.hijricalendar.widgetdata.offsetHijriMonth
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData

/**
 * On-widget month navigation: the header arrows step the grid one Hijri month at a time in place
 * (no app launch) and tapping the month name returns to "follow today".
 *
 * Each callback runs in the app process via Glance's `actionRunCallback` and persists the viewed
 * month through [HijriWidgetConfig] (keyed per widget) before asking [HijriCalendarWidget] to
 * re-render, so the choice survives re-renders, app restarts and device reboots.
 *
 * Navigation respects the supported calendar range: stepping with [offsetHijriMonth] returns
 * `null` outside it, and the callback then no-ops (the widget keeps showing the current month)
 * instead of crashing or showing an empty grid.
 */
internal object HijriWidgetNavigation {
    const val STEP_PREVIOUS = -1
    const val STEP_NEXT = 1
}

public class HijriWidgetPrevMonthCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        stepViewedMonth(context, glanceId, HijriWidgetNavigation.STEP_PREVIOUS)
    }
}

public class HijriWidgetNextMonthCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        stepViewedMonth(context, glanceId, HijriWidgetNavigation.STEP_NEXT)
    }
}

public class HijriWidgetTodayResetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        HijriWidgetConfig.clearViewedMonth(context, glanceId)
        HijriWidgetRenderQueue.render(context, glanceId)
    }
}

/**
 * Steps the currently displayed month by [step] and re-renders the grid. The current month is
 * resolved exactly as rendering does it: viewed (navigation) > config-pinned > today. When the
 * target month falls outside the supported range the step is a no-op.
 *
 * Navigation must never be the thread that pays for a cold Pakistan table: this callback can be
 * the first thing to run after a process restart, a moment before the app's own warm-up coroutine
 * has been scheduled. [PakistanWarmUp.ensureWarm] suspends onto the app's build instead.
 */
internal suspend fun stepViewedMonth(context: Context, glanceId: GlanceId, step: Int) {
    val options = HijriWidgetConfig.load(context, glanceId)
    HijriWidgetRefreshLog.d("nav:step($step)", "options.language=${options.language}")
    if (options.source.pakistan) {
        HijriWidgetRefreshLog.d("nav:step($step)", "sampling Pakistan warm-up before tap math")
        PakistanWarmUp.ensureWarm()
    }
    val todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay()
    // Only resolve "today" when it is genuinely the last fallback (never navigated, no pin);
    // a widget that has navigated once resolves straight from `viewed` and never touches
    // PakistanHijriCalendar on subsequent taps.
    val todayHijri by lazy {
        todayHijriWidgetData(anchorEpochDay = todayEpochDay, options = options)
    }
    val viewed = HijriWidgetConfig.loadViewedMonth(context, glanceId)
    // The same resolver the projection and the render cache use, so a tap steps from exactly the
    // month the widget is showing. Reading `pinnedYear`/`pinnedMonth` here independently is what let
    // a half-set pin step from a stored year paired with today's month (WD-03).
    val (baseYear, baseMonth) = resolveGridMonth(options, viewed, todayHijri) ?: run {
        HijriWidgetRefreshLog.d(
            "nav:step($step)",
            "DROPPED tap: no viewed month, no pin and today unresolvable (source=${options.source})",
        )
        return
    }
    val next = offsetHijriMonth(baseYear, baseMonth, step) ?: run {
        HijriWidgetRefreshLog.d("nav:step($step)", "no-op: month $baseYear-$baseMonth is at the supported edge")
        return
    }
    HijriWidgetRefreshLog.d("nav:step($step)", "base=$baseYear-$baseMonth -> next=$next")
    HijriWidgetConfig.setViewedMonth(context, glanceId, next.year, next.month)
    HijriWidgetRenderQueue.render(context, glanceId)
}
