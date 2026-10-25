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
 * The observances the calendar speaks about, as a banner **below the grid** rather than in the header.
 *
 * It used to be a coloured line under the month title. Two things were wrong with that. The header is
 * about *where you are* — a month and a range — and an observance is about *a day you picked*, so the
 * line belonged with the grid that carries the pick. And a plain coloured line reads as more chrome
 * under the title; the day cells fill an observance with a container wash, so the banner takes the
 * same wash to read as the same statement.
 *
 * The container is [HijriCalendarColors.eventDayContainerColor] at [EventBannerContainerAlpha], and
 * that pair is deliberately **not red**: red is the weekend figure's colour
 * ([HijriCalendarColors.weekendDayContentColor]), which marks the days the user chose as days off. An
 * observance here is background information — the month's history — so it gets the quiet container. It
 * was briefly painted red, which was reported as wrong for exactly that reason.
 *
 * The text is **not** bold: the fill is what marks the line, exactly as the filled cell is marked by
 * its fill and not by a heavier figure.
 *
 * Rendered for **every** month — the caller resolves what to say, including the empty-month label —
 * because a bar that comes and goes moves the grid under the user's finger. A caller that wants no bar
 * at all passes `showEventBanner = false` to [HijriCalendar]; a `null` or empty [text] draws nothing.
 */
@Composable
internal fun HijriCalendarEventBanner(
    text: String?,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
) {
    if (text.isNullOrEmpty()) return
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

/** How much of the observance container the banner paints: a wash over the surface, not a solid bar. */
internal const val EventBannerContainerAlpha: Float = 0.5f

private val EventBannerTopPadding = 8.dp
private val EventBannerCornerRadius = 8.dp
private val EventBannerHorizontalPadding = 12.dp
private val EventBannerVerticalPadding = 6.dp
