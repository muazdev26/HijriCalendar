package com.muazdev.hijricalendar.widgetdata

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.UrduCalendarNames
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.core.toCalendarMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private val DefaultHijriMonthNames = CalendarNames.englishHijriMonths
private val DefaultGregorianMonthNames = CalendarNames.englishGregorianMonths
private val DefaultWeekdayNames = CalendarNames.englishWeekdayShortNames

/**
 * Ready-to-hand localization lists for the widget builders. [urduHijriMonthNames],
 * [urduGregorianMonthNames] and [urduWeekdayNames] back the Urdu rendering option and are
 * the same single set of constants the app's own Urdu labels use (UrduCalendarNames in
 * `calendar-core`), so a widget and the in-app calendar can never disagree.
 *
 * The English lists come from [CalendarNames], which `calendar-ui` also reads — previously each
 * module held its own private copy and six of the twelve Hijri names had already drifted apart.
 */
public object WidgetLocalization {
    public val urduHijriMonthNames: List<String> = UrduCalendarNames.hijriMonths
    public val urduGregorianMonthNames: List<String> = UrduCalendarNames.gregorianMonths
    public val urduWeekdayNames: List<String> = UrduCalendarNames.weekdayShortNames

    /**
     * The Urdu weekday names as the **small widgets** render them — the three 1x1 tiles and the Today
     * strip: [urduWeekdayNames] with Monday written `سوموار` instead of `پیر`.
     *
     * Both are correct Urdu for Monday and the family uses both on purpose — `پیر` wherever the name
     * sits in a column or row of names (the grid's weekday header, the in-app calendar) and `سوموار`
     * where a widget names one single day (the three one-cell tiles, the Today strip). It is a
     * **small-widget-only** list rather than a change to [UrduCalendarNames.weekdays] because the two
     * are not a correction of one another: `پیر` is the name Urdu calendars print in a column heading,
     * and changing the shared list would move it under the in-app grid and the widget header too.
     *
     * **Only Monday differs.** The other six names are [urduWeekdayNames] unchanged — reusing them is
     * what keeps a tile from spelling a weekday in a way the grid above it would not, and it means a
     * future correction to one of those six lands in both lists at once. The one override lives here
     * rather than in a renderer for the same reason every other string does (WG-12): the language is a
     * `WidgetOptions` field, so no renderer can answer for it.
     */
    public val urduWeekdayTileNames: List<String> = urduWeekdayNames.mapIndexed { index, name ->
        if (index == WeekDay.MONDAY.index) "سوموار" else name
    }

    /**
     * The built-in English Hijri month names the projection falls back to when no localized list
     * is supplied. Exposed so UI that needs a month label without building a whole grid (e.g. the
     * widget settings screen's pinned-month stepper) shows the exact same names the widget will.
     */
    public val englishHijriMonthNames: List<String> = DefaultHijriMonthNames

    /**
     * The widget's *chrome* labels, resolved from the widget's own [language] (WG-12).
     *
     * These live here, next to the month and weekday name lists, for the same reason those do and
     * Android string resources do not: **a widget's language is a `WidgetOptions` field, not a
     * resource-configuration value.** One Urdu widget can sit on an English phone beside an English
     * widget, by design, and both platforms render from a single host `Context` / no
     * per-widget `Configuration` at all — so `getString(R.string.next_month)` can only ever answer
     * for the *device*.
     *
     * The cost of getting this wrong is highest exactly here. The arrows are the grid's **only
     * interactive controls**, so for a screen-reader user they are the entire navigation
     * experience: before this, a TalkBack user in an Urdu widget heard "Next month" to move through
     * a grid whose weekday headers were `اتوار … ہفتہ`.
     *
     * iOS never had the English literal — its chevrons carry no label, so VoiceOver derives one
     * from the SF Symbol, which the platform localizes. That is better than a hardcoded English
     * string but still answers for the *device*, so the same defect survives there in a quieter
     * form. Reading these from Kotlin lets both platforms answer for the widget.
     */
    public object ChromeLabels {
        /** The next-month arrow. */
        public fun nextMonth(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "اگلا مہینہ"
            WidgetLanguage.ENGLISH -> "Next month"
        }

        /** The previous-month arrow. */
        public fun previousMonth(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "پچھلا مہینہ"
            WidgetLanguage.ENGLISH -> "Previous month"
        }

        /**
         * The month title, which is the control that returns the grid to the current month.
         *
         * Deliberately *not* the month it displays. A sighted user infers the action from the
         * tapping affordance; a screen-reader user is told what the control is and has to infer
         * what it does, so this says what it does where the title's own text says what it is. It is
         * also what keeps the three header controls distinguishable when swiped between.
         */
        public fun goToCurrentMonth(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "موجودہ مہینہ پر جائیں"
            WidgetLanguage.ENGLISH -> "Go to current month"
        }

        /**
         * The header's refresh icon.
         *
         * Says what the control *does* ("refresh"), not what it is ("the refresh icon"), for the same
         * reason [goToCurrentMonth] does: a screen-reader user is swiping between controls and needs
         * to know what each one will do before pressing it. It is deliberately **not** "Refresh
         * date" — the button re-renders, it does not recalculate anything, and a label promising a
         * recomputation would be wrong in the one case a user checks it.
         */
        public fun refreshWidget(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "تازہ کریں"
            WidgetLanguage.ENGLISH -> "Refresh"
        }

        /**
         * Shown when a date fails to resolve, which makes it the *entire body* of three of the four
         * widgets — the one case where a user most needs to understand what they are looking at.
         *
         * This was a device-localised resource before, for the same reason the arrows were a
         * problem.
         */
        public fun monthUnavailable(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "ہجری تاریخ دستیاب نہیں"
            WidgetLanguage.ENGLISH -> "Hijri date unavailable"
        }

        /**
         * The grid footer's line when there is no observance to name.
         *
         * The footer reserves a line for **every** month grid so the layout does not move under a
         * finger (FD-08), and that line used to render empty when nothing was to be named. An empty
         * line is indistinguishable from a footer that failed to load, so the line now says so
         * explicitly — and, like every other string on a widget, in the widget's own language rather
         * than the device's (WG-12).
         */
        public fun noEvent(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "کوئی تاریخی واقعہ نہیں"
            WidgetLanguage.ENGLISH -> "No event"
        }

        /**
         * The Hijri era marker, appended to a Hijri year: `1447 AH` / `١٤٤٧ ھ`.
         *
         * **Why this is here rather than in `res/values`.** A widget's language is a
         * [WidgetOptions] field, not a resource-configuration value — Glance renders from the host's
         * single `Context`, so `getString(R.string.x)` can only ever answer for the *device*. One
         * Urdu widget is designed to sit beside an English one on a phone with no Urdu locale, and
         * the era is the clearest tell of which is which.
         *
         * `ھ` (U+06BE) rather than `ہ`, which is the Urdu letter Heh-goal and is what Urdu uses when
         * writing * hijri sani* — the year. `ہ` is the do-chashmi he and belongs to words, not to a
         * numeral suffix. It is a different codepoint, so this is not a stylistic choice.
         *
         * See [gregorianEra] for the Gregorian side's marker.
         */
        public fun hijriEra(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "\u06BE"
            WidgetLanguage.ENGLISH -> "AH"
        }

        /**
         * The Gregorian era marker, appended to a Gregorian year: `2026 AD` / `٢٠٢٦ ء`.
         *
         * **`ء` (U+0621), the standalone hamza**, in Urdu — chosen deliberately over the two-character
         * `ئے` that a literal transliteration of "era" would suggest. A single hamza is the marker
         * Urdu typography uses for the Gregorian era, and it balances the one-character `ھ` opposite
         * it: both years then carry a single glyph, which is what makes the pair readable at the small
         * type a widget renders.
         *
         * Asserted on its codepoint in `EraMarkersTest`, because the neighbouring Urdu letters are
         * visually close and a wrong one still renders as plausible Urdu — this is the same reason
         * [hijriEra] is asserted rather than eyeballed.
         */
        public fun gregorianEra(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "\u0621"
            WidgetLanguage.ENGLISH -> "AD"
        }

        /**
         * A year with its era appended, in whichever direction [language] writes one.
         *
         * Both supported languages put the era *after* the year, so there is no prefix case here — and
         * adding one would be a schema flag for a formatting decision that belongs here. The era
         * marker is one glyph either way; the ordering is the locale's business.
         */
        public fun yearWithEra(year: Int, language: WidgetLanguage, gregorian: Boolean): String =
            "$year ${if (gregorian) gregorianEra(language) else hijriEra(language)}"
    }

    /**
     * The Hijri month names for [language], or `null` for [WidgetLanguage.ENGLISH] so the
     * builder falls back to its built-in English names. Callers pass the result straight into
     * `localizedHijriMonthNames`.
     */
    public fun hijriMonthNames(language: WidgetLanguage): List<String>? = when (language) {
        WidgetLanguage.URDU -> urduHijriMonthNames
        WidgetLanguage.ENGLISH -> null
    }

    /** The Gregorian month names for [language]; `null` means the builder's English defaults. */
    public fun gregorianMonthNames(language: WidgetLanguage): List<String>? = when (language) {
        WidgetLanguage.URDU -> urduGregorianMonthNames
        WidgetLanguage.ENGLISH -> null
    }

    /** The weekday short names for [language]; `null` means the builder's English defaults. */
    public fun weekdayNames(language: WidgetLanguage): List<String>? = when (language) {
        WidgetLanguage.URDU -> urduWeekdayNames
        WidgetLanguage.ENGLISH -> null
    }

    /**
     * The **small widgets'** weekday names for [language] — see [urduWeekdayTileNames] — or `null` for
     * [WidgetLanguage.ENGLISH], where a small widget and the rest of the family print the same name and
     * the builder's English defaults are already right.
     *
     * A renderer picks this up for the three one-cell tiles and the Today strip; the grid keeps
     * [weekdayNames]. Building both in the projection is what lets a caller hand each widget the right
     * list without either side re-implementing the Monday override.
     */
    public fun weekdayTileNames(language: WidgetLanguage): List<String>? = when (language) {
        WidgetLanguage.URDU -> urduWeekdayTileNames
        WidgetLanguage.ENGLISH -> null
    }

    /**
     * The digit style a language defaults to: Eastern Arabic-Indic for Urdu, Western for
     * English. Used to seed a fresh widget's numeral style so a new Urdu widget shows Eastern
     * digits without any extra configuration.
     */
    public fun defaultNumeralStyle(language: WidgetLanguage): NumeralStyle = when (language) {
        WidgetLanguage.URDU -> NumeralStyle.ARABIC_INDIC
        WidgetLanguage.ENGLISH -> NumeralStyle.WESTERN
    }

    /**
     * The short label for a calendar source, localized to [language].
     *
     * This is the **settings screen's source picker's row label** (`WidgetCatalogView.swift` renders
     * it). It is *not* a label drawn on the widget itself: the grid widget has no source pill, and
     * the source is chosen only from the host's settings screen (WD-10c).
     */
    public fun sourceLabel(source: WidgetSource, language: WidgetLanguage): String = when (language) {
        WidgetLanguage.URDU -> if (source.pakistan) "پاکستان" else "حساب"
        WidgetLanguage.ENGLISH -> if (source.pakistan) "Pakistan" else "Calculation"
    }

    /**
     * The Hijri months whose name does not distinguish them from a sibling, so a short form has to
     * carry the ordinal: `ربيع ١` against `ربيع ٢`, `جمادى ١` against `جمادى ٢`.
     *
     * **Only four months.** Every other Hijri month has an ordinary name that already stands alone
     * (`Muharram`, `Rajab`, `رمضان`), so its short form *is* its name — inventing a second string for
     * those would mean a caller could render a different month name depending on which widget asked,
     * and there is no width problem to solve for a name that already fits.
     *
     * This is a **list of the months that need it**, not a map of short names for all twelve, and
     * that asymmetry is the whole cost argument: four hand-picked base names per language rather than
     * twelve, with nothing to keep in step as the other eight are simply the names already shipped.
     */
    public val pairedHijriMonths: Set<Int> = setOf(RABI_AL_AWWAL, RABI_AL_THANI, JUMADA_AL_ULA, JUMADA_AL_AKHIRAH)

    /** Rabi' I — the first of the two months named [pairedHijriMonths] shares a base name with. */
    public const val RABI_AL_AWWAL: Int = 3

    /** Rabi' II. */
    public const val RABI_AL_THANI: Int = 4

    /** Jumada I. */
    public const val JUMADA_AL_ULA: Int = 5

    /** Jumada II. */
    public const val JUMADA_AL_AKHIRAH: Int = 6

    /**
     * The name [month] (1-12) **shares with its sibling**, for a month in [pairedHijriMonths];
     * `null` for every other month.
     *
     * The shared base rather than the month name: `ربیع الثانی ٢` is not a short form of anything, and
     * `ربیع الثانی` is precisely the string the header band is too narrow for. So the four paired
     * months get their base name (`ربيع`, `جمادى`, `Rabi'`, `Jumada`) and the ordinal is appended by
     * the caller — see [WidgetLocalization.hijriMonthShortName].
     *
     * **Not derived by trimming the full name.** Suffix-stripping would have to know that Urdu
     * `الثانی` and English `al-thani` both mean "second", which is a linguistic fact this file has no
     * business encoding, and it would break on the next language. The four strings are the honest
     * cost.
     */
    public fun pairedHijriMonthBase(month: Int, language: WidgetLanguage): String? = when (month) {
        RABI_AL_AWWAL, RABI_AL_THANI -> when (language) {
            // `ربیع` bare. The full name is `ربیع الاول` / `ربیع الثانی`; the shared part is the
            // prefix, and the ordinal below replaces the whole `الاول`/`الثانی` rather than adding to
            // it — which is what keeps the band narrow enough to hold.
            WidgetLanguage.URDU -> "ربیع"
            WidgetLanguage.ENGLISH -> "Rabi'"
        }

        JUMADA_AL_ULA, JUMADA_AL_AKHIRAH -> when (language) {
            WidgetLanguage.URDU -> "جمادی"
            WidgetLanguage.ENGLISH -> "Jumada"
        }

        else -> null
    }

    /**
     * [month]'s name in the form a narrow slot can hold: the shared base plus [ordinal] for the four
     * [pairedHijriMonths], and the ordinary name for the other eight.
     *
     * [ordinal] is what the caller appends after the base — this function never invents one. The
     * projection supplies the Roman numeral `I`/`II` for the English spelling of the four paired
     * months (a letter part of the month's name, not a counted number), and does not reach here for
     * Urdu at all: **Urdu month names are never shortened**, so an Urdu tile's full name passes
     * through the projection untouched. [monthName] is what a caller passes for a month with no short
     * form, so a language with no entry for a month still renders rather than rendering nothing.
     *
     * **A renderer must not implement this** (WG-12), for the same reason it must not call [hijriEra]:
     * a month name is text in the widget's own language, which is a [WidgetOptions] field and not a
     * resource-configuration value. Building it in the projection is also what lets iOS pick it up
     * without a Swift edit.
     */
    public fun hijriMonthShortName(
        month: Int,
        monthName: String,
        ordinal: String,
        language: WidgetLanguage,
    ): String = pairedHijriMonthBase(month, language)?.let { base -> "$base $ordinal" } ?: monthName
}

/**
 * One Gregorian day [G], represented as a "real-world weekday marking" helper. The
 * observed Hijri date of [G] is `(G + adjustmentDays).toHijrahDate()`; this is the same
 * adjusted-space convention used across `calendar-core` (see `toCalendarMonth`).
 */
private fun observe(date: LocalDate, adjustmentDays: Int): LocalDate =
    date.plus(adjustmentDays, DateTimeUnit.DAY)

/**
 * The supplied localization list, or [fallback] if there is none or it is too short to cover the
 * calendar it labels.
 *
 * **Validated once here rather than guarded at each of the four indexing sites** (WD-04). A
 * caller-supplied list that is `null` *or* shorter than [fallback] becomes [fallback], which then
 * indexes safely for the rest of the function.
 *
 * A short list falls back *wholesale* rather than being padded per entry, and that is the point:
 * per-entry `getOrNull` degrades to a **mixed-script header row** — some weekday names localized,
 * the rest English — which for a reader is worse than an outright fallback and has no error to
 * notice it by. All-or-nothing is also the honest reading of "here is a localization of this
 * calendar": a partial one is a bug in the caller, and silently rendering half of it hides that.
 *
 * The alternative — `require(size >= n)` — would be defensible if this function threw on bad input,
 * but its documented failure mode is `null`, and the callers are a Glance composition on a session
 * worker and a WidgetKit timeline callback, where a thrown exception is not recoverable.
 */
private fun List<String>?.orDefault(fallback: List<String>): List<String> =
    if (this == null || size < fallback.size) fallback else this

/**
 * Builds a render-ready 42-day Hijri month grid for the given [hijriYear]/[hijriMonth].
 *
 * The grid uses the same layout math as `calendar-core`'s `toCalendarMonth` so cells
 * line up with the in-app calendar: alignment follows [weekStart], and [adjustmentDays] shifts
 * the whole grid on the Gregorian timeline.
 *
 * With [pakistan] = true the grid is generated from the Pakistan (Ruet-e-Hilal) calendar
 * instead of Umm al-Qura: cells carry the observed Pakistani date of each Gregorian day,
 * exactly as the in-app calendar's Pakistan mode renders them. [weekendDays] controls
 * which weekdays are shaded; the default is the in-app default (Friday + Saturday).
 *
 * [localizedHijriMonthNames], [localizedGregorianMonthNames] and [localizedWeekdayNames]
 * localize the labels, and must be 12 / 12 / 7 entries in [WeekDay] enum order
 * (Saturday-first for weekdays). **A list that is `null` or shorter than that is ignored and the
 * built-in English names are used instead** — falling back wholesale rather than per entry, so the
 * header row can never come out half in one script (WD-04). This function does not throw: it
 * returns `null` when the month is outside the supported range (~1300-1600 AH, or 1400-1500 in
 * Pakistan mode) or when the calendar library rejects the date.
 *
 * [overrides] is the month-length table the grid is generated against — see
 * [HijriMonthLengths]. It defaults to the process-wide [HijriMonthOverrides.current] so a
 * caller that never configures overrides keeps reading the global, but a caller holding a scoped
 * table gets a grid that honours it. Omitting it while the global is mutated elsewhere is exactly
 * how an in-app calendar and its home-screen widget end up disagreeing (WD-01); the
 * `options:`-taking overload threads the table from [WidgetOptions.overridesTable] for you.
 *
 * With [rightToLeft] = true the projection is reordered for a right-to-left reading direction:
 * the weekday headers are reversed and each week's cells are reversed, so the first day of the
 * week sits on the right exactly as it does in the in-app calendar under an RTL layout. This is
 * driven by the widget's own [WidgetLanguage] rather than the host's `layoutDirection`, so an
 * Urdu widget renders RTL on an LTR device and vice versa.
 */
@Suppress("ReturnCount")
public fun buildHijriMonthWidgetData(
    hijriYear: Int,
    hijriMonth: Int,
    adjustmentDays: Int,
    weekStart: WeekStart = WeekStart.DEFAULT,
    numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    pakistan: Boolean = false,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
    rightToLeft: Boolean = false,
    localizedHijriMonthNames: List<String>? = null,
    localizedGregorianMonthNames: List<String>? = null,
    localizedWeekdayNames: List<String>? = null,
    overrides: HijriMonthLengths = HijriMonthOverrides.current,
    hijriEra: String? = null,
    gregorianEra: String? = null,
): HijriMonthWidgetData? {
    val yearMonth = try {
        if (hijriMonth in 1..12) HijrahYearMonth(hijriYear, hijriMonth) else return null
    } catch (_: Exception) {
        return null
    }
    val firstDayOfWeek = weekStart.dayOfWeek

    // Normalised once (WD-04), so the four indexing sites below cannot run off the end of a
    // caller-supplied list. See `orDefault`.
    val hijriMonthNames = localizedHijriMonthNames.orDefault(DefaultHijriMonthNames)
    val gregorianMonthNames = localizedGregorianMonthNames.orDefault(DefaultGregorianMonthNames)

    val calendarMonth = try {
        yearMonth.toCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            adjustmentDays = adjustmentDays,
            pakistan = pakistan,
            weekendDays = weekendDays,
            overrides = overrides,
        )
    } catch (_: Exception) {
        return null
    }

    val hijriMonthName = hijriMonthNames.getOrNull(hijriMonth - 1)
        ?: "Month $hijriMonth"

    val weekdayNames = localizedWeekdayNames.orDefault(DefaultWeekdayNames)
    val weekdayHeaders = (0 until CalendarMonth.DAYS_IN_WEEK).map { offset ->
        weekdayNames[(firstDayOfWeek.index + offset) % CalendarMonth.DAYS_IN_WEEK]
    }.let { if (rightToLeft) it.reversed() else it }

    // Read the range off the month that was just built, not a second time through
    // `resolveGregorianMonthRange` (WD-02). The two calls read the same inputs, but they read them
    // at two different instants, and `HijriMonthOverrides` is a CAS-published global — so an override
    // landing between the grid build and the header build produced a header naming one Gregorian
    // month above cells containing another. In a Hijri↔Gregorian bridge widget the two halves
    // contradicting each other is the worst failure available, and it was silent.
    //
    // `calendarMonth.gregorianFirstDay`/`gregorianLastDay` are resolved from the *same* space the
    // grid was built in — Pakistan months span the Ruet-e-Hilal extent, override-shifted months the
    // observed extent, plain Umm al-Qura months the calculated one — and `calendar-core` documents
    // that a caller holding a `CalendarMonth` should prefer them for exactly this reason.
    //
    // This is also what makes WD-01 complete. Once `overrides` is threaded in, a second call that
    // does *not* receive the parameter silently re-reads the global, so the header would keep
    // ignoring the caller's table no matter how correctly the grid honoured it.
    val gregorianFirst = calendarMonth.gregorianFirstDay
    val gregorianLast = calendarMonth.gregorianLastDay
    // A **range**, not the first in-month Gregorian month (FD-06). A Hijri month is 29 or 30 days
    // and a Gregorian month is 28-31, so roughly half of all Hijri months start in one Gregorian
    // month and end in another — Safar 1448 began on 30 July 2026, and the header used to say
    // "July 2026" for a month with not one day in it.
    //
    // This is the same string `formatGregorianRange` has always produced for `gregorianRange`, which
    // was iOS-only. Two fields holding the same range, one of them truncated, is how the app and the
    // widget came to disagree; the truncated one is now the only one.
    val gregorianMonthTitle =
        formatGregorianRange(gregorianFirst, gregorianLast, gregorianMonthNames, gregorianEra)
    // The narrow-header form: the dominant Gregorian month, era dropped. Read from the same
    // `calendarMonth.days` the grid cells come from, so the short title can never name a Gregorian
    // month the row of cells below it does not mostly contain.
    val gregorianShortTitle =
        formatGregorianShortTitle(calendarMonth.days, gregorianMonthNames, gregorianEra)

    val days = calendarMonth.days.map { day ->
        val local = day.localDate
        HijriDayWidgetData(
            hijriDay = day.dayOfMonth,
            dayText = formatNumber(day.dayOfMonth, numeralStyle),
            gregorianDay = local.day,
            gregorianDayText = formatNumber(local.day, numeralStyle),
            gregorianEpochDay = local.toEpochDays(),
            isCurrentMonth = day.isCurrentMonth,
            isWeekend = day.isWeekend,
            // FD-08: the observance flag rides the projection rather than being re-derived by each
            // renderer, because `CalendarDay.event` has already resolved the calendar space this
            // projection was built in.
            hasEvent = day.event != null,
        )
    }.let { cells ->
        // Reverse within each 7-cell week (not the whole list) so the rows still read as weeks
        // while the first day of the week lands on the right, matching the RTL in-app grid.
        if (rightToLeft) {
            cells.chunked(CalendarMonth.DAYS_IN_WEEK).flatMap { it.reversed() }
        } else {
            cells
        }
    }

    return HijriMonthWidgetData(
        hijriYear = hijriYear,
        hijriYearText = withEra(
            formatNumber(hijriYear, numeralStyle),
            hijriEra,
        ),
        hijriMonth = hijriMonth,
        hijriMonthName = hijriMonthName,
        gregorianMonthTitle = gregorianMonthTitle,
        gregorianShortTitle = gregorianShortTitle,
        weekdayHeaders = weekdayHeaders,
        days = days,
        adjustmentDays = adjustmentDays,
    )
}

/**
 * @param names the **already-normalised** Gregorian month names (see `orDefault`), so every index
 *   below is in range. Kept as a plain `List<String>` rather than `List<String>?` on purpose: the
 *   nullable form is exactly how this helper ended up indexing a caller-supplied list unguarded.
 */
private fun formatGregorianRange(
    first: LocalDate,
    last: LocalDate,
    names: List<String>,
    era: String? = null,
): String {
    fun monthName(date: LocalDate): String = names[date.month.ordinal]

    // `null` — a caller that passed no marker — leaves the years bare, which is what the default
    // parameter means, so nothing changes for a leaf caller that has not opted in (FD-05).
    fun year(y: Int): String = withEra(y.toString(), era)

    return when {
        first.month == last.month && first.year == last.year ->
            "${monthName(first)} ${year(first.year)}"
        first.year == last.year ->
            "${monthName(first)} - ${monthName(last)} ${year(first.year)}"
        else ->
            "${monthName(first)} ${year(first.year)} - ${monthName(last)} ${year(last.year)}"
    }
}

/**
 * The narrow-header form of a Hijri month's Gregorian extent: the Gregorian month the month mostly
 * occupies, with no era marker — `August 2026`.
 *
 * ## Why "mostly occupies"
 *
 * A Hijri month is 29 or 30 days and a Gregorian one 28-31, so about half of all Hijri months straddle
 * two. The full title names both ends of that range (`July - August 2026 AD`), which is correct but too
 * wide for a phone-width widget once the header shares its row with two nav arrows and two header
 * icons. A narrow title has to drop something; it drops the **range** rather than truncating it, so the
 * line never reads as a month the cells below it do not contain.
 *
 * Counted over the month's **own** days ([CalendarDay.isCurrentMonth]) rather than from
 * `gregorianFirst`/`gregorianLast`, so a padded neighbouring-day cell cannot put weight behind a month
 * the grid does not show. Ties resolve to the earlier month (insertion order is chronological), which
 * is the right default for a month that begins in one Gregorian month and ends in the next.
 *
 * Returns an empty string for a month with no own days at all — unreachable for a real projection, and
 * better than indexing an empty map.
 */
private fun formatGregorianShortTitle(days: List<CalendarDay>, names: List<String>, era: String?): String {
    val counts = LinkedHashMap<Pair<Int, Int>, Int>()
    for (day in days) {
        if (!day.isCurrentMonth) continue
        val date = day.localDate
        val key = date.year to date.month.ordinal
        counts[key] = (counts[key] ?: 0) + 1
    }
    val dominant = counts.maxByOrNull { it.value }?.key ?: return ""
    val suffix = era?.let { " $it" } ?: ""
    return "${names[dominant.second]} ${dominant.first}$suffix"
}

/**
 * Compact "today" projection for the widget family's option/summary display and the
 * iOS small family size.
 *
 * [anchorEpochDay] is the local-calendar day count of the real-world Gregorian day to
 * report (the widget's "today", computed by the caller via the platform calendar, never
 * by dividing an absolute time instant by 86400). The returned Hijri date is the
 * observed date `(anchor + adjustmentDays).toHijrahDate()`, matching the adjusted-space
 * convention everywhere else.
 *
 * [localizedHijriMonthNames], [localizedGregorianMonthNames] and [localizedWeekdayNames]
 * localize the month name, the Gregorian date line and the weekday label respectively, and must be
 * 12/12/7 entries in [WeekDay] enum order for weekdays. A list that is `null` or shorter is ignored
 * in favour of the built-in English names, exactly as in [buildHijriMonthWidgetData] (WD-04) — the
 * two builders must agree, or a consumer passing a short list gets English months in the grid and
 * Urdu weekdays in the today card. [numeralStyle] controls the digit rendering of
 * [TodayHijriWidgetData.hijriDayText] and the day figure inside [TodayHijriWidgetData.gregorianDate].
 * Together they make a fully Urdu today card possible.
 *
 * Returns `null` when the anchor falls outside the supported Umm al-Qura range.
 *
 * [overrides] is the month-length table the Pakistan branch resolves against; it defaults to the
 * process-wide [HijriMonthOverrides.current] for the same reason as the grid builder (WD-01), and
 * the `options:`-taking overload threads it from [WidgetOptions.overridesTable].
 *
 * [monthNameLanguage] is the language [localizedHijriMonthNames] was localized **to**, and it is a
 * parameter rather than something derived from the lists because
 * [TodayHijriWidgetData.hijriMonthShortName] needs it to pick a short month's shared base name
 * (FD-10) — the strings themselves cannot say which language they are. `null` means "assume English",
 * which is what a `null` [localizedHijriMonthNames] resolves to anyway
 * ([DefaultHijriMonthNames]); passing an Urdu list *without* the language would put an English `Rabi'`
 * beside Urdu names, so the `options:` overload always supplies both together. Read
 * [WidgetOptions.effectiveMonthNameLanguage] for the value to pass, never [WidgetOptions.language] —
 * the names follow their own option.
 *
 * [localizedWeekdayTileNames] is the same idea for the one name the small widgets spell differently:
 * it feeds [TodayHijriWidgetData.weekdayTileName] and falls back to the already-normalised
 * [localizedWeekdayNames] when absent, so a caller that does not localize it gets a small widget that
 * agrees with the grid rather than an English name. Read [WidgetLocalization.weekdayTileNames] for the
 * value to pass — never hand-roll the Monday override in a renderer (WG-12). It is the **last**
 * parameter so that adding it cannot shift a positional call. It is deliberately a separate list
 * rather than a language flag: the projection does not know [WidgetOptions.language] here (only the
 * month-name language crosses this boundary), and a flag would make it guess.
 */
@Suppress("ReturnCount")
public fun todayHijriWidgetData(
    anchorEpochDay: Long,
    adjustmentDays: Int,
    localizedHijriMonthNames: List<String>? = null,
    localizedGregorianMonthNames: List<String>? = null,
    localizedWeekdayNames: List<String>? = null,
    numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    pakistan: Boolean = false,
    overrides: HijriMonthLengths = HijriMonthOverrides.current,
    hijriEra: String? = null,
    gregorianEra: String? = null,
    monthNameLanguage: WidgetLanguage? = null,
    localizedWeekdayTileNames: List<String>? = null,
): TodayHijriWidgetData? {
    // `fromEpochDays` throws for an epoch day outside the representable range, and `plus` throws
    // when the *shifted* date leaves it — which a large `adjustmentDays` alone can do. Both were
    // outside a `try` at some point; the shift is inside one now (WD-10a), so this function
    // cannot throw for any input, which is what its KDoc promises.
    val anchor = try {
        LocalDate.fromEpochDays(anchorEpochDay)
    } catch (_: Exception) {
        return null
    }
    val shifted = try {
        observe(anchor, adjustmentDays)
    } catch (_: Exception) {
        return null
    }
    val (hDay, hMonth, hYear) = if (pakistan) {
        val pDate = PakistanHijriCalendar.gregorianToHijri(shifted, overrides)
        if (pDate != null && pDate.year in PakistanHijriCalendar.MIN_YEAR..PakistanHijriCalendar.MAX_YEAR) {
            Triple(pDate.day, pDate.month, pDate.year)
        } else {
            return null
        }
    } else {
        if (overrides.all().isEmpty()) {
            val hDate = try {
                shifted.toHijrahDate()
            } catch (_: Exception) {
                return null
            }
            Triple(hDate.day, hDate.month.number, hDate.year)
        } else {
            val oDate = ObservedHijriCalendar.observedDateAt(shifted.toEpochDays(), overrides) ?: return null
            Triple(oDate.day, oDate.month, oDate.year)
        }
    }
    val weekday = WeekDay.fromDayOfWeek(anchor.dayOfWeek)
    // Same normalisation as the grid builder (WD-04), for the same reason: a short list must not
    // yield a *partly* localized card, and the two builders must agree about what a short list
    // means — otherwise a consumer passing one gets English months in the grid and Urdu weekdays
    // in the today card.
    val hijriMonthNames = localizedHijriMonthNames.orDefault(DefaultHijriMonthNames)
    val gregorianMonthNames = localizedGregorianMonthNames.orDefault(DefaultGregorianMonthNames)
    val weekdayNames = localizedWeekdayNames.orDefault(DefaultWeekdayNames)
    // The 1x1 tiles' own list, defaulting to the normalised weekday names rather than to the English
    // defaults: a caller that passes an Urdu [localizedWeekdayNames] but no tile list must get Urdu
    // tiles, not an English Monday beside six Urdu names (WD-04's all-or-nothing rule, one level down).
    val weekdayTileNames = localizedWeekdayTileNames.orDefault(weekdayNames)
    val hijriMonthName = hijriMonthNames.getOrNull(hMonth - 1) ?: ""
    return TodayHijriWidgetData(
        hijriDay = hDay,
        hijriDayText = formatNumber(hDay, numeralStyle),
        hijriMonth = hMonth,
        hijriYear = hYear,
        hijriYearText = withEra(formatNumber(hYear, numeralStyle), hijriEra),
        hijriMonthName = hijriMonthName,
        hijriMonthShortName = shortHijriMonthName(
            month = hMonth,
            monthName = hijriMonthName,
            language = monthNameLanguage,
        ),
        gregorianDate = "${formatNumber(anchor.day, numeralStyle)} " +
            "${gregorianMonthNames[anchor.month.ordinal]} " +
            withEra(anchor.year.toString(), gregorianEra),
        weekdayName = weekdayNames.getOrNull(weekday.index) ?: weekday.shortName,
        weekdayTileName = weekdayTileNames.getOrNull(weekday.index) ?: weekdayNames.getOrNull(weekday.index)
            ?: weekday.shortName,
        adjustmentDays = adjustmentDays,
        gregorianDay = anchor.day,
        gregorianDayText = formatNumber(anchor.day, numeralStyle),
        gregorianMonth = anchor.month.ordinal + 1,
        gregorianMonthName = gregorianMonthNames[anchor.month.ordinal],
        gregorianMonthShortName = gregorianMonthShortName(
            monthNameLanguage = monthNameLanguage,
            gregorianMonthName = gregorianMonthNames[anchor.month.ordinal],
            monthIndex = anchor.month.ordinal,
        ),
        gregorianYear = anchor.year,
        gregorianYearText = withEra(formatNumber(anchor.year, numeralStyle), gregorianEra),
    )
}

/**
 * [TodayHijriWidgetData.hijriMonthShortName] for the month the projection resolved (FD-10).
 *
 * **Only English month names are ever shortened.** English: the four months whose name does not
 * distinguish them from a sibling carry a Roman ordinal — `Rabi' I` / `Jumada II`, the convention an
 * English calendar actually shows — and the other eight are [monthName] unchanged. Every other
 * month-name language — Urdu — returns [monthName] byte for byte: **Urdu month names are not
 * shortened**, the same rule [gregorianMonthShortName] follows, so a 1x1 tile renders `ربیع الاول`
 * in full rather than `ربیع ١`.
 *
 * The Roman ordinal ignores the widget's [NumeralStyle] on purpose: it is a letter part of the
 * month's English name, not a counted quantity, and `I`/`II` are Latin letters no numeral style
 * translates — a `Rabi' 1` beside English month names reads as "the first Rabi' in a list" rather
 * than as the month's name. (Urdu, which would have wanted the figure in its own numeral style, no
 * longer receives an ordinal at all.)
 *
 * Empty in, empty out — a month with no name yields no short form, because a header band reading
 * just `٢` is a number with nothing saying which month it is.
 */
private fun shortHijriMonthName(
    month: Int,
    monthName: String,
    language: WidgetLanguage?,
): String {
    // `ENGLISH` rather than anything derived: `null` means the caller passed no localized list, which
    // `orDefault` has already turned into the built-in English names. Guessing from the *strings*
    // would mean inspecting codepoints to infer a language, which is exactly the kind of guess that
    // puts a Latin base name in an Urdu band.
    // Only English shortens: an Urdu month name is handed back byte for byte, so `ربیع الاول` reaches
    // a 1x1 tile in full rather than as `ربیع ١`.
    val effectiveLanguage = language ?: WidgetLanguage.ENGLISH
    return when {
        monthName.isEmpty() -> ""
        effectiveLanguage != WidgetLanguage.ENGLISH -> monthName
        else -> WidgetLocalization.hijriMonthShortName(
            month = month,
            monthName = monthName,
            ordinal = pairedMonthRomanOrdinal(month),
            language = effectiveLanguage,
        )
    }
}

/**
 * [TodayHijriWidgetData.gregorianMonthShortName] for the month the projection resolved.
 *
 * The three-letter English abbreviation when the month names render in English, and the full name
 * when they render in Urdu — the host's decision was "English abbreviates, Urdu stays full", because
 * Arabic script has no three-letter `اکتوبر`: trimming it cuts the word rather than abbreviating it.
 *
 * `monthIndex` is the 0-based month the anchor resolved to, indexed against
 * [CalendarNames.englishGregorianMonthAbbreviations] positionally. The `null` language means the
 * caller passed no localized list, which `orDefault` has already turned into the built-in English
 * names — so `null` is English here, for the same reason [shortHijriMonthName] reads it that way.
 */
private fun gregorianMonthShortName(
    monthNameLanguage: WidgetLanguage?,
    gregorianMonthName: String,
    monthIndex: Int,
): String {
    val isEnglish = monthNameLanguage == null || monthNameLanguage == WidgetLanguage.ENGLISH
    if (!isEnglish) return gregorianMonthName
    return CalendarNames.englishGregorianMonthAbbreviations.getOrNull(monthIndex) ?: gregorianMonthName
}

/**
 * [month]'s member of its pair as the Roman numeral English spells it with — `I` for Rabi'
 * al-awwal and Jumada al-ula, `II` for the seconds.
 *
 * Roman regardless of [NumeralStyle]: it is the letter part of the month's English name, not a
 * counted quantity, and `I`/`II` are Latin letters that neither numeral style translates. The only
 * caller is [shortHijriMonthName], and the only month-name language that reaches it is English —
 * Urdu month names are never shortened, so they are handed back whole and never get an ordinal.
 */
private fun pairedMonthRomanOrdinal(month: Int): String = if (pairedMonthOrdinal(month) == 2) "II" else "I"

/**
 * Which member of its pair [month] (1-12) is: `1` for Rabi' I and Jumada I, `2` for the seconds.
 *
 * `1` for every month with no pair, so a caller cannot accidentally read a short form off a month
 * that has none — [WidgetLocalization.pairedHijriMonthBase] is what decides whether the ordinal is
 * used at all, and it answers `null` for those eight months.
 */
private fun pairedMonthOrdinal(month: Int): Int = when (month) {
    WidgetLocalization.RABI_AL_AWWAL, WidgetLocalization.JUMADA_AL_ULA -> 1
    WidgetLocalization.RABI_AL_THANI, WidgetLocalization.JUMADA_AL_AKHIRAH -> 2
    else -> 1
}

/**
 * Returns the Hijri year-month that is [offset] months away from [hijriYear]/[hijriMonth],
 * stepping forward for a positive [offset] and backward for a negative one and wrapping
 * across the year boundary (e.g. Dhul-Hijjah +1 yields Muharram of the following year,
 * Muharram -1 yields Dhul-Hijjah of the previous year).
 *
 * Returns `null` instead of throwing when [hijriMonth] is not a valid month (1-12) or the
 * result falls outside the supported Umm al-Qura range (~1300-1600 AH). Widget navigation
 * can treat `null` as "reached the supported edge" and no-op or hide the arrows.
 */
public fun offsetHijriMonth(hijriYear: Int, hijriMonth: Int, offset: Int): HijriYearMonth? {
    if (hijriMonth !in 1..12) return null
    // The alpha type stops here (WD-07): it is an implementation detail of the arithmetic, not part
    // of this module's contract.
    val result = try {
        HijrahYearMonth(hijriYear, hijriMonth).plusMonth(offset)
    } catch (_: Exception) {
        null
    }
    return result?.let { HijriYearMonth(year = it.year, month = it.month.number) }
}

/**
 * Appends a space and [era] to [year] when there is one.
 *
 * The one place era marking happens for a year, so the grid header, the today strip and the tiles all
 * produce the same string. Both supported languages write the era *after* the year, so there is no
 * prefix case to get wrong here; see [WidgetLocalization.ChromeLabels.yearWithEra].
 */
private fun withEra(year: String, era: String?): String = if (era == null) year else "$year $era"

private fun formatNumber(value: Int, numeralStyle: NumeralStyle): String =
    if (numeralStyle == NumeralStyle.ARABIC_INDIC) {
        value.toString().map { digit ->
            ARABIC_INDIC_DIGITS[digit.code - '0'.code]
        }.joinToString("")
    } else {
        value.toString()
    }

private val ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩".toCharArray()
