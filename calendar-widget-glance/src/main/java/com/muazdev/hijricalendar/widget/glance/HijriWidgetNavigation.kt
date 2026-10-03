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

/**
 * Tapping the month title: back to today.
 *
 * Clears the **selection** as well as the viewed month (FD-09). Leaving the selection behind would
 * leave the footer naming an observance for a day in a month the user has just left — which is not an
 * answer about the month they are now looking at.
 */
public class HijriWidgetTodayResetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        HijriWidgetConfig.clearViewedMonth(context, glanceId)
        HijriWidgetConfig.clearSelectedDay(context, glanceId)
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

/**
 * The callback behind a grid day cell (FD-09).
 *
 * Records the tapped day and re-renders **that instance** in place — no app launch. Before this, every
 * tappable region on a widget opened the app, which is a launcher shortcut wearing a calendar's
 * clothes: the widget showed a month of 42 tappable cells and not one of them did anything a widget
 * cell can do.
 *
 * ## Write, then render
 *
 * The selection is persisted *before* [HijriWidgetRenderQueue.render] is called, in that order
 * deliberately. `render` may coalesce with an in-flight render, so a fast double-tap can settle on
 * either tap's selection; writing first means whichever render wins sees the newest value, because
 * `provideGlance` composes against `currentState<Preferences>()` and the store's snapshot always
 * reflects the latest write. Reversing the two would let a tap be lost rather than merely superseded.
 *
 * The same reasoning is why there is no mutex here — AGENTS records that the old `renderLock` was
 * removed on purpose, because it ordered the *enqueue* and not the async composition behind it.
 *
 * ## Navigating to an adjacent month
 *
 * A tap on a leading or trailing cell (visible when `WidgetOptions.showAdjacentDays` is on) **moves the
 * grid to that day**, exactly as the in-app calendar does — `selectDate` there moves the grid when the
 * tapped date is outside the current month, and the two surfaces are compared directly on this case.
 * Decided here rather than left to whichever implementation ran first.
 */
public class HijriWidgetSelectDayCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        selectDay(context, glanceId, parameters)
    }
}

/**
 * The action keys a cell carries.
 *
 * `ActionParameters.Key`, not a bare `String`: Glance's `parameters[key]` operator takes a typed key,
 * and a `String` key would silently read nothing.
 */
internal val ACTION_DAY = ActionParameters.Key<Int>("day")
internal val ACTION_MONTH = ActionParameters.Key<Int>("month")
internal val ACTION_YEAR = ActionParameters.Key<Int>("year")

internal suspend fun selectDay(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
) {
    // All three or nothing. A partial tap is not a date, and writing a selection without a year would
    // store a day that cannot be compared against a month — which is the half-set-pin defect WD-03
    // exists to prevent, in a new place.
    val year = parameters[ACTION_YEAR]
    val month = parameters[ACTION_MONTH]
    val day = parameters[ACTION_DAY]
    if (year == null || month == null || day == null) {
        HijriWidgetRefreshLog.d(
            "nav:select",
            "DROPPED tap: incomplete action (year=$year month=$month day=$day)",
        )
        return
    }

    val options = HijriWidgetConfig.load(context, glanceId)
    if (options.source.pakistan) {
        // The same rule as month navigation: never make a tap the thread that builds a cold century
        // table. `ensureWarm` suspends onto the app's build rather than doing the work here.
        PakistanWarmUp.ensureWarm()
    }

    HijriWidgetRefreshLog.d("nav:select", "$year-$month-$day")

    // A tap on a neighbouring month's cell moves the grid to it, matching the in-app calendar. Written
    // before the selection so a render that races this tap still sees a consistent pair.
    val todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay()
    // Only resolve "today" when it is genuinely the last fallback, for the same reason month
    // navigation does: a widget that has navigated once never touches PakistanHijriCalendar again.
    val todayHijri by lazy { todayHijriWidgetData(anchorEpochDay = todayEpochDay, options = options) }
    val viewed = HijriWidgetConfig.loadViewedMonth(context, glanceId)
    if (!isShowingMonth(options, viewed, todayHijri, year, month)) {
        HijriWidgetConfig.setViewedMonth(context, glanceId, year, month)
    }
    HijriWidgetConfig.setSelectedDay(context, glanceId, year, month, day)
    HijriWidgetRenderQueue.render(context, glanceId)
}
