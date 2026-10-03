package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.CalendarMonth
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Enum controlling how numeric cell text is rendered. [WESTERN] maps digits to `0-9`,
 * [ARABIC_INDIC] to the Eastern Arabic-Indic digits `0-9`. Rendering in widgets is
 * driven by this value rather than the device locale so both widget stacks behave
 * identically.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public enum class NumeralStyle {
    @JsonNames("LATIN", "ARABIC_INDIC_DIGITS")
    WESTERN,

    @JsonNames("EASTERN", "ARABIC_INDIC_NUMBERS")
    ARABIC_INDIC,
}

/**
 * The display language of a widget. Unlike the device locale this is a per-widget choice:
 * a widget can be Urdu on an English phone (or the reverse). [isRtl] drives the reading
 * direction of the weekday header, the day grid and the header arrows so a widget renders
 * consistently regardless of the host's `layoutDirection`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public enum class WidgetLanguage(public val isRtl: Boolean) {
    // `@JsonNames` aliases are permanent (WD-06): a stored widget outlives every release that ever
    // wrote it, so any spelling that has ever been emitted must keep decoding forever. The aliases
    // here are the ones a renderer plausibly wrote by hand or from an older Swift/Java port.
    @JsonNames("urdu", "UR")
    URDU(isRtl = true),

    @JsonNames("english", "EN", "en_US")
    ENGLISH(isRtl = false),
}

/**
 * The Hijri source a widget follows. [CALCULATION] is Umm al-Qura, [PAKISTAN] is the
 * Ruet-e-Hilal (Pakistan) calendar; [pakistan] is the flag the projection builders consume.
 *
 * The source is chosen from the host app's settings screen and nowhere else — the grid widget has
 * **no source pill** and no on-widget toggle (WD-10c). [CALCULATION] is the name persisted in the
 * wire format and therefore frozen; `WidgetLocalization.sourceLabel` already renders it as
 * "Umm al-Qura"/"Calculation", and `@JsonNames` carries the accurate `"UAQ"`/`"UM_AL_QURA"`
 * spellings so a future rename does not invalidate a stored widget.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public enum class WidgetSource(public val pakistan: Boolean) {
    // `UAQ` and `UM_AL_QURA` are the *accurate* spellings: this source is Umm al-Qura, and
    // `WidgetLocalization.sourceLabel` already renders it as "Umm al-Qura" in Urdu contexts. If the
    // entry is ever renamed to match, these aliases keep every stored widget readable (WD-06).
    @JsonNames("UAQ", "UM_AL_QURA", "CALCULATION_SOURCE")
    CALCULATION(pakistan = false),

    @JsonNames("RUET_E_HILAL", "RUET")
    PAKISTAN(pakistan = true),
    ;

    public fun toggled(): WidgetSource = if (this == CALCULATION) PAKISTAN else CALCULATION
}

/**
 * One cell of the 42-day Hijri month grid in render-ready widget form.
 *
 * [gregorianEpochDay] is the integer local-calendar day count (days since 1970-01-01,
 * matching kotlinx-datetime's `LocalDate.toEpochDays()`, which the native renderers can
 * compare against their own "today" anchor without importing any Hijri math).
 */
@Serializable
public data class HijriDayWidgetData(
    val hijriDay: Int,
    val dayText: String,
    val gregorianDay: Int,
    val gregorianDayText: String,
    val gregorianEpochDay: Long,
    val isCurrentMonth: Boolean,
    val isWeekend: Boolean,
)

/**
 * One rendered Hijri month in widget form: a 6x7 grid plus the header data the native
 * renderers need (Hijri + Gregorian month titles on one line, weekday headers).
 *
 * [gregorianRange] is the one field with no single-month invariant: it can read
 * "December 2026 - January 2027" when a Hijri month straddles two Gregorian ones. **Only iOS renders
 * it** — the Android grid widget's header shows [gregorianMonthTitle] alone on one centred line
 * (WD-10c).
 */
@Serializable
public data class HijriMonthWidgetData(
    val hijriYear: Int,
    /**
     * The Hijri year **with its era marker**, in the widget's own language and digit style: `1447 AH`
     * or `١٤٤٧ ھ` (FD-05).
     *
     * Separate from [hijriYear] rather than replacing it because the bare number is what arithmetic
     * and comparisons want, while this is what a header reads. A renderer must not format the year
     * itself — the era belongs to the widget's language, which is a `WidgetOptions` field and not a
     * resource configuration (WG-12).
     */
    val hijriYearText: String = hijriYear.toString(),
    val hijriMonth: Int,
    val hijriMonthName: String,
    /**
     * The Gregorian month name + year of the Hijri month's first day (e.g. "September 2026"),
     * so a header can show both calendars on one line — `hijriMonthName hijriYear ·  title`.
     */
    val gregorianMonthTitle: String,
    /** The month's full Gregorian extent, e.g. `"September - October 2026"`. iOS only; see the class. */
    val gregorianRange: String,
    val weekdayHeaders: List<String>,
    /**
     * The month's **padded** day cells — always [com.muazdev.hijricalendar.core.CalendarMonth.TOTAL_DAYS]
     * of them, the same list `calendar-ui` builds. Renderers must not filter this themselves;
     * [visibleDays] is the one place that knows how a grid turns a padded month into rows.
     */
    val days: List<HijriDayWidgetData>,
    val adjustmentDays: Int,
) {
    /**
     * The weeks a grid should lay out, for a widget configured with [showAdjacentDays].
     *
     * **The cells stay padded even when the neighbours are hidden.** That is the whole design, and
     * it is the opposite of filtering: a Hijri month can begin on any weekday, so the first row of
     * a padded month holds some of the *previous* month's days before the 1st. Dropping those cells
     * outright would slide the 1st into column zero and put every day of the month under the wrong
     * weekday heading — a grid that looks plausible and is systematically off by [leading] columns.
     * So the cell list is left alone and [paintsDay] decides what is inked; the 1st stays under its
     * own weekday.
     *
     * What *is* removed is whole weeks that contain no day of this month at all: padding often adds
     * one, and dropping it is what turns six rows into five. A month therefore occupies
     * `ceil((leading + length) / 7)` rows rather than a constant `CalendarMonth.WEEKS_IN_MONTH` —
     * for a 29-day month that is always five.
     *
     * The trim is by emptiness rather than by "the last row", because [days] is pre-reversed for an
     * RTL widget and the padding then sits at the other end.
     *
     * Callers must not chunk this themselves, and must not reimplement the trailing-week trim: both
     * the row count and the column alignment depend on it, and three renderers (Glance, Compose,
     * Swift) would each get one subtly wrong.
     */
    public fun weeksToRender(showAdjacentDays: Boolean): List<List<HijriDayWidgetData>> {
        val padded = days.chunked(CalendarMonth.DAYS_IN_WEEK)

        // Trim by *emptiness*, not from a named end. `days` is reversed per row for an RTL widget, so
        // a month that begins at the top of the list in English begins at the bottom of it in Urdu —
        // and "drop the last N rows" would then trim the wrong ones, silently deleting the first week
        // of the month on exactly the devices whose language the projection went to the trouble of
        // reversing for. A week with no current-month day in it can only ever sit at one end, so
        // searching for the first and last such week is correct in both directions.
        val firstWeek = padded.indexOfFirst { it.any { day -> day.isCurrentMonth } }
        val lastWeek = padded.indexOfLast { it.any { day -> day.isCurrentMonth } }

        return if (showAdjacentDays || firstWeek < 0) {
            padded
        } else {
            padded.subList(firstWeek, lastWeek + 1)
        }
    }

    /**
     * Whether [day] should be inked at all, given [showAdjacentDays].
     *
     * A hidden day is **blank, not disabled**: it keeps its cell so the week's columns stay aligned,
     * but nothing is drawn in it and it is not something to tap. Pair with [weeksToRender], which
     * is what removes the rows that end up entirely blank.
     */
    public fun paintsDay(day: HijriDayWidgetData, showAdjacentDays: Boolean): Boolean =
        showAdjacentDays || day.isCurrentMonth
}

/**
 * Compact "today" projection used by the options screen of the widget family and by
 * the iOS small family size. Immutable; intended to be rebuilt on demand rather than
 * cached.
 */
@Serializable
public data class TodayHijriWidgetData(
    val hijriDay: Int,
    /**
     * [hijriDay] rendered with the projection's [NumeralStyle] (e.g. `"١٣"` for
     * Arabic-Indic), so the big day figure on a today card follows the digit style
     * without the renderer knowing anything about numerals.
     */
    val hijriDayText: String,
    val hijriMonth: Int,
    val hijriYear: Int,
    /**
     * The Hijri year **with its era marker**, in the widget's own language and digit style (FD-05):
     * `1447 AH` or `١٤٤٧ ھ`.
     *
     * Separate from [hijriYear] for the same reason as [gregorianYearText]: the bare number is what
     * arithmetic wants, this is what a renderer displays, and the era belongs to the widget's language
     * rather than to the device's resources (WG-12).
     */
    val hijriYearText: String = hijriYear.toString(),
    val hijriMonthName: String,
    val gregorianDate: String,
    val weekdayName: String,
    val adjustmentDays: Int,
    /**
     * The Gregorian day of the anchor (unshifted by [adjustmentDays]), rendered with the
     * projection's [NumeralStyle]. Lets a dedicated "Gregorian today" widget render a big
     * day figure exactly like [hijriDayText], on the same local-calendar anchor the Hijri
     * number was produced from.
     */
    val gregorianDay: Int,
    val gregorianDayText: String,
    /**
     * The 1-based Gregorian month number of the anchor, plus the localized month name and year a
     * "Gregorian today" widget shows under its big day figure.
     */
    val gregorianMonth: Int,
    val gregorianMonthName: String,
    val gregorianYear: Int,
    /**
     * The Gregorian year **with its era marker**, in the widget's own language and digit style (FD-05):
     * `2026 AD` or `٢٠٢٦ ئے`.
     *
     * The counterpart to [hijriYearText], for the same reason: the bare [gregorianYear] is what
     * arithmetic wants, this is what a renderer displays, and the marker belongs to the widget's
     * language rather than to the device's resources (WG-12).
     */
    val gregorianYearText: String = gregorianYear.toString(),
)