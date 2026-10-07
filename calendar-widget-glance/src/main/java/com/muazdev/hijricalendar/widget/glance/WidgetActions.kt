package com.muazdev.hijricalendar.widget.glance

import androidx.glance.action.Action

/**
 * The four actions a rendered widget can respond to, grouped.
 *
 * Grouping them is not cosmetic (WG-12). `HijriWidgetRoot` took them as four separate nullable
 * parameters and was already over detekt's threshold; threading a fifth value through (the widget's
 * language, for the chrome's accessibility labels) pushed it further. It also makes the
 * non-interactive case *explicit*: every preview constructs this with all four `null`, and
 * `GlanceModifier.clickableWhen(action)` skips the click when the action is null — so "this preview
 * cannot be tapped" is one value rather than a property four call sites have to agree on.
 */
internal data class WidgetActions(
    /**
     * Tapping the widget's body **outside a day cell** opens the app (the deep link in
     * [HIJRI_DEEP_LINK_TODAY]).
     *
     * FD-09 gave each day cell its own action, so this is no longer "tapping anywhere" — the cells
     * shadow it, and the header, the arrows, the weekday row and the padding still reach it. That is
     * deliberate: a widget that can no longer launch its own app is a regression for anyone using it
     * as a shortcut, and Glance has no long-press, so the background is where "open the app" has to
     * live once single-tap means something.
     */
    val open: Action? = null,
    /** The previous-month arrow. */
    val prev: Action? = null,
    /** The next-month arrow. */
    val next: Action? = null,
    /** Tapping the month title returns the grid to the current month. */
    val reset: Action? = null,
    /**
     * Tapping the header's refresh icon re-renders the family now.
     *
     * A separate field from [reset] rather than a reuse: the two answer different questions ("show
     * me today" vs "this looks stale"), and collapsing them would mean every call site that wants one
     * silently gets the other.
     */
    val refresh: Action? = null,
) {
    /**
     * True when nothing in this widget responds to a tap — the settings live preview and the picker
     * previews, which render the same tree so the user sees exactly what they will get.
     *
     * A widget that is **loading** a month answers true as well, and that is deliberate: every
     * action is suppressed for the duration, so "this widget cannot be tapped right now" is the
     * honest answer, and it is one value that keeps each of the five call sites from having to
     * remember a second condition.
     */
    val isNonInteractive: Boolean
        get() = open == null && prev == null && next == null && reset == null && refresh == null
}

/**
 * The action set to render **while a month is loading**.
 *
 * One function rather than five `if (isLoading) null else …` at the call sites: the whole point is
 * that *nothing* is tappable mid-step, and a per-field version is one forgotten field away from a
 * double-step landing on top of the step already running.
 */
internal fun WidgetActions.whileLoading(): WidgetActions = WidgetActions()
