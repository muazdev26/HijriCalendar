package com.muazdev.hijricalendar.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.calendarDayCell(size: Dp = 48.dp): Modifier =
    this.size(size)

@Composable
fun Modifier.clickableIfEnabled(
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
