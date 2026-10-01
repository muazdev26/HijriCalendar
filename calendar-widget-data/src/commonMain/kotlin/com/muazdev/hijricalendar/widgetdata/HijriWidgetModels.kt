package com.muazdev.hijricalendar.widgetdata

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
    val days: List<HijriDayWidgetData>,
    val adjustmentDays: Int,
)

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
)