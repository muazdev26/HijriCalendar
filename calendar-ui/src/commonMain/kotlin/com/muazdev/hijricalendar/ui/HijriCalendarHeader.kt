package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * @param title The already-formatted month title. [HijriCalendar] builds it from
 *   [HijriCalendarLabels.headerTitle], so ordering and numeral system are the consumer's. This
 *   composable renders it verbatim and never joins or translates it itself.
 * @param labels Supplies the two navigation content descriptions. They used to be English parameter
 *   defaults, which put the same two strings in two public places and left a direct caller shipping
 *   English screen-reader output.
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
    title: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    gregorianMonthText: String? = null,
    contentDescription: String? = null,
    labels: HijriCalendarLabels = HijriCalendarDefaults.labels(),
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
            // pointingForward = false is the *logical* "previous" direction, so the glyph mirrors
            // with the layout exactly as Icons.AutoMirrored would.
            // IconButton merges descendants, so the label on the chevron is announced once, as the
            // action -- the same arrangement the material icons used.
            NavChevron(
                pointingForward = false,
                contentDescription = labels.previousMonthContentDescription,
                color = if (canGoToPreviousMonth) {
                    colors.navigationIconColor
                } else {
                    colors.navigationIconColor.copy(alpha = 0.38f)
                },
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
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
            NavChevron(
                pointingForward = true,
                contentDescription = labels.nextMonthContentDescription,
                color = if (canGoToNextMonth) {
                    colors.navigationIconColor
                } else {
                    colors.navigationIconColor.copy(alpha = 0.38f)
                },
            )
        }
    }
}
