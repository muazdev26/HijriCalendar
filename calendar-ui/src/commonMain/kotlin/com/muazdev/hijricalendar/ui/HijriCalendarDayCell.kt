package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.ui.util.clickableIfEnabled

/**
 * One day cell of the grid: a fixed-size circle carrying the Hijri day figure, the Gregorian day
 * number, or both, plus the selected / today / weekend / outside-month treatment.
 *
 * Public because it is the reusable unit: [HijriWeekRow] and [HijriCalendarGrid] are internal, so a
 * consumer building their own calendar layout has this and [HijriCalendarColors] and nothing else.
 *
 * @param day The cell to render. Its three nullable date slots are resolved by core; see
 *   [CalendarDay] for which slot is populated in which calendar space. A day carrying an observance is
 *   **filled** with [HijriCalendarColors.eventDayContainerColor] — not dotted — unless it is also
 *   selected, outside the month, or disabled.
 * @param onClick Invoked on tap. Ignored when [CalendarDay.isDisabled] — the cell renders with the
 *   disabled colours, exposes no `Role.Button`, registers no click action, and sets
 *   `SemanticsProperties.Disabled`.
 * @param dayCellSize The cell's fixed size, [HijriCalendarDefaults.SingleLineCellSize] by default.
 *   This does **not** change with the system font scale; see [HijriCalendar].
 * @param useArabicIndicNumerals Renders the Hijri day figure with Arabic-Indic digits. Only the
 *   Hijri figure — the Gregorian number and the header year are always Western. Prefer
 *   [HijriCalendarLabels] with a localized renderer once that is available.
 * @param dateDisplayMode Which of the two dates the cell draws. Does not affect size.
 * @param content Replaces the built-in text entirely. The cell still applies its own background,
 *   border, clip and semantics, so a custom content is responsible only for what it draws inside
 *   the circle.
 */
@Composable
public fun HijriCalendarDayCell(
    day: CalendarDay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayCellSize: Dp? = null,
    labels: HijriCalendarLabels = HijriCalendarDefaults.labels(),
    content: (@Composable (CalendarDay) -> Unit)? = null,
    /**
     * Paints nothing at all: no day figure, no Gregorian gloss, no background, no border, no
     * content description and no click target.
     *
     * This exists so a grid can blank a neighbouring month's day **without removing its cell** — a
     * Hijri month can start mid-week, so the first padded week holds some of the previous month's
     * days before the 1st. Removing the cells would slide the 1st into column zero and put every day
     * of the month under the wrong weekday heading. The cell keeps its width; only its ink and its
     * semantics go.
     *
     * Default `true`, so every existing caller — including a host rendering its own cells — is
     * unchanged. See [HijriCalendarState.showAdjacentDays] for the grid-level behaviour.
     */
    visible: Boolean = true,
    /**
     * Whether the surrounding grid is drawing dividers (FD-04), which changes this cell's own shape.
     *
     * A grid cell's background is a circle — the day figure is a round token. With dividers on that
     * is the wrong shape: a round fill floating between straight rules looks like a badge laid on
     * top of the grid rather than a cell of it. So a highlighted cell becomes a **block that covers
     * the whole cell**, flush to the rules on every side.
     *
     * `false` by default, which is exactly the existing circular appearance, so every current caller
     * and every existing consumer is unchanged.
     */
    cellHasDividers: Boolean = false,
) {
    val cellSize = dayCellSize ?: HijriCalendarDefaults.SingleLineCellSize

    if (!visible) {
        // Sized, so the week's columns still line up under the weekday headings, and nothing else.
        Box(modifier = modifier.size(cellSize))
        return
    }

    val style = day.cellStyle(colors)
    val clickLabel = remember(day, labels) { labels.dayContentDescription(day) }
    // Only the date the mode actually draws is built. In GREGORIAN_ONLY the Hijri string -- and its
    // Arabic-Indic allocation -- used to be built and thrown away for all 42 cells, every
    // recomposition. See UI-12.
    val hijriText = if (dateDisplayMode == DateDisplayMode.GREGORIAN_ONLY) {
        ""
    } else {
        day.dayOfMonth.toArabicIndicNumeralsOrWestern(useArabicIndicNumerals)
    }
    val gregorianText = if (dateDisplayMode == DateDisplayMode.HIJRI_ONLY) {
        ""
    } else {
        day.localDate.day.toString()
    }

    Box(
        modifier = modifier
            .size(cellSize)
            .semantics(mergeDescendants = true) {
                contentDescription = clickLabel
                if (style.enabled) {
                    role = Role.Button
                } else {
                    // Without this a disabled cell is merely a target with no action, which a screen
                    // reader announces as an unlabelled tappable rather than a disabled one. Found
                    // by the UI-02 harness: assertIsNotEnabled() on a disabled cell failed.
                    // The default dayContentDescription also appends ", disabled", so this was
                    // arriving twice; that string is kept for compatibility and because a custom
                    // label lambda is free to drop it.
                    disabled()
                }
            }
            .then(
                // A circular clip for a day token; a plain one when the grid is divided, so a
                // highlighted cell fills its rectangle instead of inscribing a circle in it.
                if (cellHasDividers) Modifier else Modifier.clip(CircleShape),
            )
            .background(style.backgroundColor)
            .then(
                if (cellHasDividers) {
                    Modifier
                } else {
                    Modifier.border(style.borderWidth, style.borderColor, CircleShape)
                },
            )
            .clickableIfEnabled(
                enabled = style.enabled,
                onClickLabel = clickLabel,
                // Role is set in the semantics block above; passing it here too made the effective
                // value depend on modifier order.
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content(day)
        } else {
            CellText(dateDisplayMode, hijriText, gregorianText, style)
        }
    }
}

internal fun Int.toArabicIndicNumerals(): String {
    val arabicIndicDigits = charArrayOf(
        '\u0660', // ٠
        '\u0661', // ١
        '\u0662', // ٢
        '\u0663', // ٣
        '\u0664', // ٤
        '\u0665', // ٥
        '\u0666', // ٦
        '\u0667', // ٧
        '\u0668', // ٨
        '\u0669', // ٩
    )
    return toString().map { char ->
        if (char.isDigit()) {
            arabicIndicDigits[char - '0']
        } else {
            char
        }
    }.joinToString("")
}

/**
 * How one day cell is drawn: the resolved colours, the today border, and whether it is interactive.
 *
 * Extracted from the composable body because the rules it encodes are **design**, not plumbing, and
 * nothing asserted them. They are also order-dependent — see [dayCellStyle] — so leaving six
 * parallel `when` blocks inline meant a reorder would silently change which state wins.
 */
internal data class DayCellStyle(
    val contentColor: Color,
    val gregorianColor: Color,
    val backgroundColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val enabled: Boolean,
)

/**
 * Resolves the visual treatment for [day] from [colors].
 *
 * The branch order **is** the precedence, and it is asserted by `DayCellStyleTest`:
 *
 * 1. **selected** wins over everything. A selected cell is drawn as selected even when it is also
 *    disabled, outside the month, or an observance, so the user's selection never changes appearance
 *    under them.
 * 2. **disabled**, then **outside the month**, then **observance** (FD-08), then **weekend** — in that
 *    order, for content colour.
 * 3. **observance** also fills the cell, below the selection container and above [dayBackgroundColor].
 * 4. **today** draws a border unless the cell is already selected **or an observance**, because
 *    either one's filled container would hide it.
 *
 * Named `cellStyle` rather than `dayCellStyle` so it reads as `day.cellStyle(colors)` at the call
 * site.
 *
 * One consequence worth stating: a cell that is *both* selected and disabled renders as fully
 * enabled while refusing taps. That combination is reachable — `isDisabled` is resolved against the
 * mutable `adjustmentDays` — and it is why the cell sets `disabled()` in semantics rather than
 * relying on its appearance.
 */
internal fun CalendarDay.cellStyle(colors: HijriCalendarColors): DayCellStyle {
    // An observance **fills** the cell (FD-08). It used to be a 4dp dot above the day figure, which
    // was too small to survive on a busy grid and carried no information beyond "something is here":
    // the header and the summary card already name the observance, so a glance needed the day's
    // *status*, not a second copy of its name.
    //
    // The fill is [eventDayContainerColor] rather than the selection container because it answers a
    // different question — a fact about the date versus a fact about the session — and a day that is
    // both selected and an observance must read as selected.
    //
    // An out-of-month or disabled observance day is **not** filled. Both states are already
    // communicated by a dimmed figure, and a fill on a day the month does not contain reads as a
    // second selection — worse than the dot it replaces. The observance is still named in the header
    // once such a day is selected.
    val observance = event != null && isCurrentMonth && !isDisabled
    val filled = observance && !isSelected
    val showTodayBorder = isToday && !isSelected && !observance
    return DayCellStyle(
        contentColor = contentColor(colors, observance),
        gregorianColor = gregorianColor(colors, observance),
        backgroundColor = when {
            isSelected -> colors.selectedDayContainerColor
            filled -> colors.eventDayContainerColor
            else -> colors.dayBackgroundColor
        },
        borderColor = when {
            isSelected -> colors.selectedDayContainerColor
            showTodayBorder -> colors.todayBorderColor
            else -> Color.Transparent
        },
        borderWidth = when {
            isSelected -> HijriCalendarDefaults.TodayBorderWidth
            showTodayBorder -> colors.todayBorderWidth
            else -> 0.dp
        },
        enabled = !isDisabled,
    )
}

/**
 * The day's figure colour, given whether it carries a filled observance.
 *
 * Split out of [cellStyle] for complexity, not for taste: six branches plus the two container branches
 * and the border pair exceeded detekt's threshold in one function. The **order** is the precedence and
 * is what `DayCellStyleTest` asserts — selected, disabled, outside the month, observance, weekend,
 * ordinary.
 */
private fun CalendarDay.contentColor(colors: HijriCalendarColors, observance: Boolean): Color =
    when {
        isSelected -> colors.selectedDayContentColor
        isDisabled -> colors.disabledDayContentColor
        !isCurrentMonth -> colors.outsideMonthDayContentColor
        observance -> colors.eventDayContentColor
        isWeekend -> colors.weekendDayContentColor
        else -> colors.dayContentColor
    }

/** The Gregorian sub-label: the figure's colour, dimmed, for the states that dim the figure too. */
private fun CalendarDay.gregorianColor(colors: HijriCalendarColors, observance: Boolean): Color =
    when {
        isSelected -> colors.selectedDayContentColor.copy(alpha = 0.7f)
        !isCurrentMonth -> colors.outsideMonthDayContentColor.copy(alpha = 0.7f)
        observance -> colors.eventDayContentColor.copy(alpha = 0.7f)
        else -> colors.gregorianDayContentColor
    }

/** Renders [value] with Arabic-Indic digits when [useArabicIndicNumerals], else Western. */
private fun Int.toArabicIndicNumeralsOrWestern(useArabicIndicNumerals: Boolean): String =
    if (useArabicIndicNumerals) toArabicIndicNumerals() else toString()

/**
 * The built-in cell contents for one [DateDisplayMode].
 *
 * Split out of [HijriCalendarDayCell] so the composable is about *layout and interaction* and this
 * is about *which dates are drawn*. The three `Text` calls were near-identical and the duplication
 * is where a style change would have been applied to two of the three.
 *
 * `HIJRI_ONLY` uses `bodyMedium` at the full content colour; `BOTH` uses `bodySmall` over
 * `labelSmall`, because two lines have to share one fixed-size cell — see
 * [HijriCalendarDefaults.SingleLineCellSize] for why that cell is 48dp.
 */
@Composable
private fun CellText(
    dateDisplayMode: DateDisplayMode,
    hijriText: String,
    gregorianText: String,
    style: DayCellStyle,
) {
    when (dateDisplayMode) {
        DateDisplayMode.HIJRI_ONLY -> CellTextLine(
            text = hijriText,
            color = style.contentColor,
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        DateDisplayMode.GREGORIAN_ONLY -> CellTextLine(
            text = gregorianText,
            color = style.contentColor,
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        DateDisplayMode.BOTH -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            CellTextLine(
                text = hijriText,
                color = style.contentColor,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            CellTextLine(
                text = gregorianText,
                color = style.gregorianColor,
                textStyle = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * One line of cell text.
 *
 * `maxLines = 1` and `softWrap = false` throughout: a month grid is a fixed-pitch matrix, so a long
 * value clips rather than reflowing the row. Combined with the font-scale cap in [HijriCalendar],
 * that is what bounds the cell's height at every accessibility setting.
 */
@Composable
private fun CellTextLine(
    text: String,
    color: Color,
    textStyle: TextStyle,
) {
    Text(
        text = text,
        style = textStyle,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
}
