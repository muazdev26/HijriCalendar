package com.muazdev.hijricalendar.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Makes a modifier clickable only when [enabled], registering [onClickLabel] and [role] either way.
 *
 * `Modifier.clickable` already takes an `enabled` flag; the reason this exists is the *other*
 * half — when disabled it removes the `OnClick` semantics action entirely, so a screen reader
 * offers no action on the node rather than offering a disabled one. `HijriCalendarDayCell` sets
 * `disabled()` in its own semantics block to compensate; see there.
 *
 * `indication = null` is deliberate. A month grid is 42 cells and 42 simultaneous ripples are
 * visual noise; the cell expresses state through its selected/today treatment instead. See
 * [UI-08-touch-feedback](UI-08-touch-feedback.md), which reopens that decision.
 */
@Composable
internal fun Modifier.clickableIfEnabled(
    enabled: Boolean,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = if (enabled) {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
} else {
    this
}
