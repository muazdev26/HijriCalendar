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
import com.muazdev.hijricalendar.core.DateDisplayMode

@Composable
fun HijriCalendarHeader(
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
) {
    val showGregorian = dateDisplayMode != DateDisplayMode.HIJRI_ONLY && gregorianMonthText != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = previousMonthContentDescription,
                tint = colors.navigationIconColor,
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

        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = nextMonthContentDescription,
                tint = colors.navigationIconColor,
            )
        }
    }
}
