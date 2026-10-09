package com.muazdev.hijricalendar.shared

import com.muazdev.hijricalendar.core.HijriEventLanguage
import com.muazdev.hijricalendar.core.UrduCalendarNames
import com.muazdev.hijricalendar.ui.HijriCalendarLabels
import kotlinx.datetime.LocalDate

/** Urdu *hijri sani*: U+06BE, the Heh-goal that suffixes a Hijri year. Not `ہ`, which is a word letter. */
private const val URDU_HIJRI_ERA = "ھ"

/**
 * Urdu for the Gregorian era: the standalone hamza, `ء` (U+0621).
 *
 * One character, which balances the one-character `ھ` above it — both years then carry a single
 * glyph. Mirrors `WidgetLocalization.ChromeLabels.gregorianEra`, which is what the widget side reads;
 * this is the in-app copy, because `calendar-ui` has no access to the widget schema.
 */
private const val URDU_GREGORIAN_ERA = "\u0621"

/**
 * The Urdu label bundle, built **once and held**.
 *
 * That is not a style preference — `HijriCalendar` uses `labels` as a `remember` key at three sites
 * and its four function fields compare by reference, so a bundle constructed inline in a composable
 * produces a new object on every recomposition and discards all three caches. This is a `val`, so it
 * is a single instance for the process.
 *
 * The era markers are the reason this bundle exists rather than plain defaults (FD-05): `AH` and `AD`
 * are Latin, and an Urdu calendar that renders its year with an English suffix is answering in the
 * wrong script. The **library's** defaults stay empty — see `HijriCalendarLabels.hijriEra` — because
 * that type is published API and a default that changed output would silently restyle every existing
 * consumer's header. Opting in is a host's decision.
 */
val UrduCalendarLabels = HijriCalendarLabels(
    hijriMonthName = { _, month -> UrduCalendarNames.hijriMonths[month - 1] },
    gregorianMonthName = { month -> UrduCalendarNames.gregorianMonths[month - 1] },
    weekdayShortName = { weekDay -> UrduCalendarNames.weekdays.getValue(weekDay) },
    previousMonthContentDescription = "پچھلا مہینہ",
    nextMonthContentDescription = "اگلا مہینہ",
    hijriEra = URDU_HIJRI_ERA,
    gregorianEra = URDU_GREGORIAN_ERA,
    // The whole title line, so the era and the year's digit system stay one locale's decision.
    headerTitle = { monthName, year -> "$monthName $year $URDU_HIJRI_ERA" },
    // The Gregorian range line, which the default formatter cannot era-mark: it takes two dates and
    // returns the whole string, so the markers have to be composed here where the era is in scope.
    gregorianMonthRangeLabel = { first, last -> urduGregorianRange(first, last) },
    // An observance in Urdu, and the line announced for it. "آج" (today) reads naturally with the
    // default banner, which renders the name alone; a host that wants "Today is Ashura" writes that
    // here rather than in the composable, because how a date is announced next to its observance is a
    // locale's decision.
    eventName = { event -> event.name(HijriEventLanguage.URDU) },
    eventBanner = { event, _ -> event.name(HijriEventLanguage.URDU) },
)

/**
 * The header's Gregorian line in Urdu, with its era.
 *
 * Same three cases as the library's default — one month name, two names in one year, two names across
 * a year boundary — with `ء` appended to the year that is actually shown.
 */
private fun urduGregorianRange(first: LocalDate, last: LocalDate): String {
    // `gregorianMonths` is listed January..December, so the index is `Month.ordinal` as-is — the same
    // contract `defaultGregorianMonthRangeLabel` and the widget's `WidgetDataApi` use. Subtracting 1
    // shifted every month name back one and threw on January (ordinal 0 -> index -1), which is a
    // header crash the first time a user navigated to the Hijri month that starts in January.
    fun name(date: LocalDate): String = UrduCalendarNames.gregorianMonths[date.month.ordinal]
    return when {
        first.month == last.month && first.year == last.year ->
            "${name(first)} ${first.year} $URDU_GREGORIAN_ERA"
        first.year == last.year ->
            "${name(first)} - ${name(last)} ${first.year} $URDU_GREGORIAN_ERA"
        else ->
            "${name(first)} ${first.year} $URDU_GREGORIAN_ERA - " +
                "${name(last)} ${last.year} $URDU_GREGORIAN_ERA"
    }
}