package com.muazdev.hijricalendar.sample

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.widget.glance.GregorianDateWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriDateWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriTodayWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriWidgetConfig
import com.muazdev.hijricalendar.widget.glance.HijriWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshScheduler
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import com.muazdev.hijricalendar.widgetdata.offsetHijriMonth
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData

/**
 * Which home-screen widget a settings screen is configuring. Drives the title, the live preview
 * and whether the grid-only pinned-month section is shown; the shared options (language, month
 * names, source, adjustment, numerals, first day of week) apply to every kind.
 */
enum class WidgetKind {
    GRID,
    TODAY,
    HIJRI_DATE,
    GREGORIAN_DATE,
}

internal fun WidgetKind.title(): String = when (this) {
    WidgetKind.GRID -> "Hijri Calendar Widget"
    WidgetKind.TODAY -> "Hijri Today Widget"
    WidgetKind.HIJRI_DATE -> "Hijri Date Tile"
    WidgetKind.GREGORIAN_DATE -> "Gregorian Date Tile"
}

internal fun WidgetKind.subtitle(): String = when (this) {
    WidgetKind.GRID -> "The month grid with today's highlight, plus a compact today card. " +
            "Every change applies and saves immediately; the widget on your home screen updates " +
            "in real time."
    WidgetKind.TODAY -> "Today's Hijri and Gregorian dates on one line, resizable from a single " +
            "cell. It follows the family options below — every change applies immediately."
    WidgetKind.HIJRI_DATE -> "A fixed 1x1 tile with today's Hijri day and month. It follows the " +
            "family options below — every change applies immediately."
    WidgetKind.GREGORIAN_DATE -> "A fixed 1x1 tile with today's Gregorian day and month. It " +
            "follows the family options below — every change applies immediately."
}

private val COMPACT_PREVIEW_SIZE = DpSize(104.dp, 110.dp)
private val GRID_PREVIEW_SIZE = DpSize(260.dp, 280.dp)
private val STRIP_PREVIEW_SIZE = DpSize(320.dp, 64.dp)
private val TILE_PREVIEW_SIZE = DpSize(120.dp, 120.dp)

/**
 * The shared widget settings form used by every per-widget configure activity. Every control
 * applies and persists immediately — there is no Save step; [onDone] is a plain dismiss. A live
 * preview at the top re-renders the actual widget's Glance layout on every touch, and each change
 * is pushed through [onApply] so the home screen updates in real time.
 */
@Composable
internal fun HijriWidgetSettingsScreen(
    kind: WidgetKind,
    initial: HijriWidgetConfig.WidgetOptions,
    onApply: (HijriWidgetConfig.WidgetOptions) -> Unit,
    onRefreshNow: () -> Unit,
    onDone: () -> Unit,
) {
    var options by rememberSaveable(stateSaver = HijriWidgetConfig.widgetOptionsSaver()) { mutableStateOf(initial) }
    var compactPreview by rememberSaveable { mutableStateOf(false) }

    fun update(newOptions: HijriWidgetConfig.WidgetOptions) {
        if (newOptions == options) return
        options = newOptions
        onApply(newOptions)
    }

    // "Today" is only used to seed the optional pinned month when switching it on.
    val today = remember(options.adjustmentDays, options.source) {
        todayHijriWidgetData(
            anchorEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
            adjustmentDays = options.adjustmentDays,
            pakistan = options.source.pakistan,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = kind.title(), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = kind.subtitle(),
            style = MaterialTheme.typography.bodyMedium,
        )

        // ── Live preview ──────────────────────────────────────────────────────
        when (kind) {
            WidgetKind.GRID -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = compactPreview,
                        onClick = { compactPreview = true },
                        label = { Text("Compact") },
                    )
                    FilterChip(
                        selected = !compactPreview,
                        onClick = { compactPreview = false },
                        label = { Text("Month grid") },
                    )
                }
                val previewSize = if (compactPreview) COMPACT_PREVIEW_SIZE else GRID_PREVIEW_SIZE
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    HijriWidgetLivePreview(
                        options = options,
                        // Settings previews the configured month; transient on-widget navigation
                        // is not a setting, so it is intentionally not reflected here.
                        viewedMonth = null,
                        size = previewSize,
                        modifier = previewBorder(previewSize, MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }

            WidgetKind.TODAY -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    HijriTodayWidgetLivePreview(
                        options = options,
                        size = STRIP_PREVIEW_SIZE,
                        modifier = previewBorder(STRIP_PREVIEW_SIZE, MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }

            WidgetKind.HIJRI_DATE -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    HijriDateWidgetLivePreview(
                        options = options,
                        size = TILE_PREVIEW_SIZE,
                        modifier = previewBorder(TILE_PREVIEW_SIZE, MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }

            WidgetKind.GREGORIAN_DATE -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    GregorianDateWidgetLivePreview(
                        options = options,
                        size = TILE_PREVIEW_SIZE,
                        modifier = previewBorder(TILE_PREVIEW_SIZE, MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
        Text(
            text = "Preview of the actual widget. Dark/light follows the device.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        // ── Language ──────────────────────────────────────────────────────────
        SectionTitle("Language")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WidgetLanguage.entries.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = options.language == option,
                        onClick = { update(options.withLanguage(option)) },
                    )
                    Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
        Text(
            text = "Urdu (right-to-left, Eastern digits) or English (left-to-right).",
            style = MaterialTheme.typography.bodySmall,
        )

        // ── Month-name language ────────────────────────────────────────────────
        SectionTitle("Month names")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WidgetLanguage.entries.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = options.monthNameLanguage == option,
                        onClick = { update(options.copy(monthNameLanguage = option)) },
                    )
                    Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
        Text(
            text = "Renders the Hijri and Gregorian month names in Urdu or English, " +
                    "independently of the widget's Language.",
            style = MaterialTheme.typography.bodySmall,
        )

        // ── Hijri source ──────────────────────────────────────────────────────
        SectionTitle("Calculation")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WidgetSource.entries.forEach { source ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = options.source == source,
                        onClick = { update(options.copy(source = source)) },
                    )
                    Text(if (source.pakistan) "Pakistan (Ruet-e-Hilal)" else "Umm al-Qura")
                }
            }
        }

        // ── Moon-sighting adjustment ──────────────────────────────────────────
        SectionTitle("Moon-sighting adjustment")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (-2..2).forEach { value ->
                OutlinedButton(onClick = { update(options.copy(adjustmentDays = value)) }) {
                    Text(if (value > 0) "+$value" else value.toString())
                }
            }
        }
        Text(text = offsetLabel(options.adjustmentDays), style = MaterialTheme.typography.bodySmall)

        // ── Numerals ──────────────────────────────────────────────────────────
        SectionTitle("Numerals")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NumeralStyle.entries.forEach { style ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = options.numeralStyle == style,
                        onClick = { update(options.copy(numeralStyle = style)) },
                    )
                    Text(
                        when (style) {
                            NumeralStyle.WESTERN -> "Western (0-9)"
                            NumeralStyle.ARABIC_INDIC -> "Arabic-Indic (٠-٩)"
                        },
                    )
                }
            }
        }

        // ── First day of week ─────────────────────────────────────────────────
        SectionTitle("First day of week")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeekDay.entries.forEachIndexed { index, day ->
                OutlinedButton(onClick = { update(options.copy(firstDayOfWeekIndex = index)) }) {
                    Text(day.shortName)
                }
            }
        }

        // ── Optional pinned month (grid only) ─────────────────────────────────
        if (kind == WidgetKind.GRID) {
            SectionTitle("Fixed month (optional)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = options.pinnedYear == null,
                    onClick = {
                        update(options.copy(pinnedYear = null, pinnedMonth = null))
                    },
                    label = { Text("Auto (follow today)") },
                )
            }
            if (options.pinnedYear != null && options.pinnedMonth != null) {
                val pinnedYear = options.pinnedYear!!
                val pinnedMonth = options.pinnedMonth!!
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Year", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { update(options.stepPinned(-12)) }) { Text("−") }
                    Text(pinnedYear.toString(), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { update(options.stepPinned(12)) }) { Text("+") }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Month", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { update(options.stepPinned(-1)) }) { Text("◀") }
                    Text(
                        options.hijriMonthLabel(pinnedMonth),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(onClick = { update(options.stepPinned(1)) }) { Text("▶") }
                }
            } else if (today != null) {
                OutlinedButton(
                    onClick = {
                        update(
                            options.copy(
                                pinnedYear = today.hijriYear,
                                pinnedMonth = today.hijriMonth
                            )
                        )
                    },
                ) {
                    Text("Pin a month")
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.End),
        ) {
            OutlinedButton(onClick = onRefreshNow) { Text("Refresh now") }
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

private fun previewBorder(size: DpSize, borderColor: androidx.compose.ui.graphics.Color) = Modifier
    .size(size)
    .clip(RoundedCornerShape(16.dp))
    .border(
        1.dp,
        borderColor,
        RoundedCornerShape(16.dp)
    )

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

/** Switching language follows the new language's default digits unless a custom style was chosen. */
private fun HijriWidgetConfig.WidgetOptions.withLanguage(language: WidgetLanguage): HijriWidgetConfig.WidgetOptions {
    if (language == this.language) return this
    val wasDefault = numeralStyle == WidgetLocalization.defaultNumeralStyle(this.language)
    return copy(
        language = language,
        numeralStyle = if (wasDefault) WidgetLocalization.defaultNumeralStyle(language) else numeralStyle,
    )
}

/** Steps the pinned month by [months], wrapping the year and no-oping outside the supported range. */
private fun HijriWidgetConfig.WidgetOptions.stepPinned(months: Int): HijriWidgetConfig.WidgetOptions {
    val year = pinnedYear ?: return this
    val month = pinnedMonth ?: return this
    val next = offsetHijriMonth(year, month, months) ?: return this
    return copy(pinnedYear = next.year, pinnedMonth = next.month.number)
}

/** The localized Hijri month name for a pinned month, matching what the widget renders. */
private fun HijriWidgetConfig.WidgetOptions.hijriMonthLabel(month: Int): String {
    val names = WidgetLocalization.hijriMonthNames(monthNameLanguage)
        ?: WidgetLocalization.englishHijriMonthNames
    return names.getOrNull(month - 1) ?: "Month $month"
}

private fun offsetLabel(offset: Int): String = when (offset) {
    0 -> "Matches the calculated (Umm al-Qura) date."
    else -> if (offset > 0) "Show Hijri $offset day(s) ahead." else "Show Hijri ${-offset} day(s) behind."
}