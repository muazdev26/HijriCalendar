package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The observance on the selected day, as a banner **below the grid** rather than in the header.
 *
 * It used to be a coloured line under the month title. Two things were wrong with that. The header is
 * about *where you are* — a month and a range — and an observance is about *a day you picked*, so the
 * line belonged with the grid that carries the pick. And a plain coloured line reads as more chrome
 * under the title; the day cells already fill an observance with a container wash, so the banner takes
 * the same wash to read as the same statement.
 *
 * The container is [HijriCalendarColors.eventDayContainerColor] at
 * [EventBannerContainerAlpha] rather than solid, matching the "accent with opacity" treatment the
 * widget's footer carries. The text is **not** bold: the fill is what marks the line, exactly as the
 * filled cell is marked by its fill and not by a heavier figure.
 *
 * Rendered only when there is an observance to name. Unlike the widget's footer — which reserves its
 * line for every month so the layout does not move under a finger — the in-app calendar scrolls and
 * has no such reserved geometry, so an absent observance draws nothing at all.
 */
@Composable
internal fun HijriCalendarEventBanner(
    text: String,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = EventBannerTopPadding)
            .clip(RoundedCornerShape(EventBannerCornerRadius))
            .background(colors.eventDayContainerColor.copy(alpha = EventBannerContainerAlpha))
            .padding(horizontal = EventBannerHorizontalPadding, vertical = EventBannerVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.eventDayContentColor,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

/** How much of the accent container the banner paints; enough to read as a wash, not a solid block. */
internal const val EventBannerContainerAlpha: Float = 0.5f

private val EventBannerTopPadding = 8.dp
private val EventBannerCornerRadius = 8.dp
private val EventBannerHorizontalPadding = 12.dp
private val EventBannerVerticalPadding = 6.dp
