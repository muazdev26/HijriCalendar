package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The calendar's month title with previous/next navigation arrows.
 *
 * Public because a consumer replacing the surrounding layout — a dialog header, a side panel —
 * still wants the arrows, and reimplementing them means reimplementing RTL mirroring and the
 * enabled/disabled gating. [HijriCalendar] also accepts a [HijriCalendar.header] slot, which is the
 * easier route when only the position needs to change.
 *
 * @param monthName Already-localized month name. This composable joins it to [year] with a space and
 *   does not reorder or translate; a locale that orders them differently needs its own title, which
 *   is what [HijriCalendarLabels] provides.
 * @param year Rendered with Western digits regardless of the calendar's numeral setting.
 * @param canGoToPreviousMonth / canGoToNextMonth Disable the arrows. [HijriCalendar] derives these
 *   from the state's own navigability rule, which accounts for [HijriCalendarState.minDate] and
 *   `maxDate`; a hand-rolled header must reproduce it or it will offer a month the grid cannot show.
 * @param gregorianMonthText The month's real-world extent, already formatted and localized. Shown
 *   only when [dateDisplayMode] is not [DateDisplayMode.HIJRI_ONLY].
 * @param contentDescription Merges the header into one node for screen readers. Pass the month and
 *   year; pass null and the header's children are announced separately.
 */
@Composable
public fun HijriCalendarHeader(
    monthName: String,
    year: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    gregorianMonthText: String? = null,
    contentDescription: String? = null,
    previousMonthContentDescription: String = "Previous month",
    nextMonthContentDescription: String = "Next month",
    canGoToPreviousMonth: Boolean = true,
    canGoToNextMonth: Boolean = true,
) {
    val showGregorian = dateDisplayMode != DateDisplayMode.HIJRI_ONLY && gregorianMonthText != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPreviousMonth,
            enabled = canGoToPreviousMonth,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = previousMonthContentDescription,
                tint = if (canGoToPreviousMonth) colors.navigationIconColor else colors.navigationIconColor.copy(alpha = 0.38f),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$monthName $year",
                style = MaterialTheme.typography.titleMedium,
                color = colors.headerContentColor,
                textAlign = TextAlign.Center,
            )
            if (showGregorian) {
                Text(
                    text = gregorianMonthText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.gregorianHeaderColor,
                    textAlign = TextAlign.Center,
                )
            }
        }

        IconButton(
            onClick = onNextMonth,
            enabled = canGoToNextMonth,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = nextMonthContentDescription,
                tint = if (canGoToNextMonth) colors.navigationIconColor else colors.navigationIconColor.copy(alpha = 0.38f),
            )
        }
    }
}
