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
) {
    /**
     * True when nothing in this widget responds to a tap — the settings live preview and the picker
     * previews, which render the same tree so the user sees exactly what they will get.
     */
    val isNonInteractive: Boolean
        get() = open == null && prev == null && next == null && reset == null
}
