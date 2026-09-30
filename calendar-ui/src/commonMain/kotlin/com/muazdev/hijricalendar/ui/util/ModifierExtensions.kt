package com.muazdev.hijricalendar.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Makes a modifier clickable only when [enabled], and only *visibly* so when [enabled].
 *
 * **Why this exists rather than `Modifier.clickable(enabled = enabled)`.** That was the obvious
 * simplification, and it is wrong: with `enabled = false`, this Compose version still registers an
 * `OnClick` semantics action on the node. `aDisabledCellIsNotClickable` in
 * `HijriCalendarInteractionTest` asserts the opposite and goes red. So the flag alone leaves a
 * disabled day cell offering an action to a screen reader that does nothing when invoked.
 * Omitting the modifier entirely removes the action, which is what a screen reader needs.
 *
 * **Why `indication` is not null.** It used to be, with a comment arguing that *"42 simultaneous
 * ripples are visual noise"* — which is not a real concern, because a ripple only draws on the cell
 * being pressed and only one cell is pressed at a time. Suppressing it left every day cell with no
 * press feedback whatsoever: a tap on a 48dp target produced nothing until the selection state
 * changed, which is fast enough to read as "the tap did nothing". Material 3 date pickers give every
 * day cell a bounded ripple, and that is what this is.
 *
 * The ripple is bounded to the circle for free: `clickable` is applied **after**
 * `Modifier.clip(CircleShape)` in `HijriCalendarDayCell`'s chain, so the indication draws inside the
 * clip. No `ripple(bounded = true)` is needed, and the default indication is used rather than a
 * constructed one so it follows `LocalIndication.current` and therefore the ambient theme.
 *
 * @see UI-08-touch-feedback.md
 */
internal fun Modifier.clickableIfEnabled(
    enabled: Boolean,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = if (enabled) {
    this.clickable(onClickLabel = onClickLabel, role = role, onClick = onClick)
} else {
    this
}
