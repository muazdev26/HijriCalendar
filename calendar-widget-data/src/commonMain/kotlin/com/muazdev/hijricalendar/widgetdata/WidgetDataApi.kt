package com.muazdev.hijricalendar.widgetdata

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.HijriMonthOverrides
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
         * `AD` rather than `CE` for the Gregorian side: the Urdu [gregorianEra] is in the AD lineage
         * and so is the Indian convention, and mixing `CE` into a Hijri calendar's chrome reads as an
         * import.
         */
        public fun hijriEra(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "\u06BE"
            WidgetLanguage.ENGLISH -> "AH"
        }

        /** The Gregorian era marker, appended to a Gregorian year: `2026 AD` / `٢٠٢٦ ئے`. */
        public fun gregorianEra(language: WidgetLanguage): String = when (language) {
            WidgetLanguage.URDU -> "\u0626\u06D2"
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
        val hDate = try {
            shifted.toHijrahDate()
        } catch (_: Exception) {
            return null
        }
        Triple(hDate.day, hDate.month.number, hDate.year)
    }
    val weekday = WeekDay.fromDayOfWeek(anchor.dayOfWeek)
    // Same normalisation as the grid builder (WD-04), for the same reason: a short list must not
    // yield a *partly* localized card, and the two builders must agree about what a short list
    // means — otherwise a consumer passing one gets English months in the grid and Urdu weekdays
    // in the today card.
    val hijriMonthNames = localizedHijriMonthNames.orDefault(DefaultHijriMonthNames)
    val gregorianMonthNames = localizedGregorianMonthNames.orDefault(DefaultGregorianMonthNames)
    val weekdayNames = localizedWeekdayNames.orDefault(DefaultWeekdayNames)
    return TodayHijriWidgetData(
        hijriDay = hDay,
        hijriDayText = formatNumber(hDay, numeralStyle),
        hijriMonth = hMonth,
        hijriYear = hYear,
        hijriYearText = withEra(formatNumber(hYear, numeralStyle), hijriEra),
        hijriMonthName = hijriMonthNames.getOrNull(hMonth - 1) ?: "",
        gregorianDate = "${formatNumber(anchor.day, numeralStyle)} " +
            "${gregorianMonthNames[anchor.month.ordinal]} " +
            withEra(anchor.year.toString(), gregorianEra),
        weekdayName = weekdayNames.getOrNull(weekday.index) ?: weekday.shortName,
        adjustmentDays = adjustmentDays,
        gregorianDay = anchor.day,
        gregorianDayText = formatNumber(anchor.day, numeralStyle),
        gregorianMonth = anchor.month.ordinal + 1,
        gregorianMonthName = gregorianMonthNames[anchor.month.ordinal],
        gregorianYear = anchor.year,
        gregorianYearText = withEra(formatNumber(anchor.year, numeralStyle), gregorianEra),
    )
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
