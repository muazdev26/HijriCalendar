package com.muazdev.hijricalendar.ui

import androidx.compose.runtime.Immutable
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarEvent
import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.HijriEvent
import com.muazdev.hijricalendar.core.PakistanEvent
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.datetime.LocalDate

/**
 * User-visible text of [HijriCalendar]. All fields have defaults reproducing the
 * built-in English output, so passing nothing keeps current behavior. Supply your own
 * lambdas/strings to localize (e.g. Urdu, Arabic).
 *
 * The defaults come from [CalendarNames], which is the same source `calendar-widget-data` uses,
 * so an in-app header and a placed widget cannot show different month names.
 *
 * **Construct this once and hold it.** [HijriCalendar] uses `labels` as a `remember` key at three
 * sites, and the four function fields compare by reference, so a `HijriCalendarLabels(...)` built
 * inline inside a composable is a new object on every recomposition and discards all three caches.
 * Pass a `val` held by the caller, or `remember` it.
 *
 * Related: a label lambda that *captures* changing state keeps a stable identity while its capture
 * changes, so this object can compare equal and still render a stale month name. Read the capture
 * through state inside the composable that uses the label, or supply a table-driven implementation
 * instead of a lambda.
 */
@Immutable
public data class HijriCalendarLabels(
    /** Returns the display name of the given Hijri month (`month` is 1-12). */
    val hijriMonthName: (year: Int, month: Int) -> String = { _, month ->
        CalendarNames.englishHijriMonths[month - 1]
    },
    /** Returns the display name of the given Gregorian month (`monthNumber` is 1-12). */
    val gregorianMonthName: (monthNumber: Int) -> String = { monthNumber ->
        CalendarNames.englishGregorianMonths[monthNumber - 1]
    },
    /** Returns the short column label for a weekday header cell. */
    val weekdayShortName: (weekDay: WeekDay) -> String = { it.shortName },

    /**
     * The Hijri era marker, appended to a Hijri year: `AH` in English, `ھ` in Urdu.
     *
     * Empty by default, which renders a year with **no** era marker — deliberately, and this is the
     * one field where the default is not the new behaviour. `HijriCalendarLabels` is public API on a
     * published artifact with a binary-compatibility golden file, so a default that changed the
     * output would silently restyle every existing consumer's header. A locale opts in by supplying
     * the marker, and [headerTitle] is where it is composed.
     *
     * `ھ` (U+06BE) rather than `ہ`: that is the Urdu *hijri sani* letter, where `ہ` is the
     * do-chashmi he that belongs to words. Different codepoint, not a stylistic choice.
     */
    val hijriEra: String = "",

    /** The Gregorian era marker, appended to a Gregorian year: `AD` in English, `ء` in Urdu. */
    val gregorianEra: String = "",

    /**
     * A Hijri [year] with its era appended, or bare when [hijriEra] is empty.
     *
     * Exposed rather than left to [headerTitle] so a host that renders a Hijri year anywhere other
     * than the header — a selected-date card, say — gets the same treatment without reimplementing it.
     */
    val hijriYearWithEra: (year: Int) -> String = { year ->
        if (hijriEra.isEmpty()) "$year" else "$year $hijriEra"
    },

    /**
     * The name of a notable date, in the host's language.
     *
     * Takes the [HijriEvent] rather than a `String`, so a locale supplies a translation rather than
     * re-deriving one from the event's fields — [HijriEvent] carries `nameEn` and `nameUr`, and a
     * locale that wants something else entirely (a transliteration, a religious title) can use the
     * event's [HijriEvent.key] instead of its English name.
     *
     * The default renders the event's own English name, which is what the built-in labels already do
     * for month and weekday names. **Not** `null`-returning: a renderer given `null` either crashes or
     * draws a blank, and a missing translation is far more discoverable as English than as nothing.
     */
    val eventName: (HijriEvent) -> String = { it.nameEn },

    /**
     * A suffix for a date that carries an observance — the line under the grid (FD-08).
     *
     * A function rather than a string because the *placement* of it is a locale's business too: Urdu
     * may want it on its own line under the header and English inline. Returning `""` renders nothing.
     *
     * **No longer called by [HijriCalendar]'s banner**, which names the month's whole observance list
     * through [monthEventBanner]. Kept because it is published API and a host's own header or
     * selected-day card can still phrase a single observance's day this way; a locale that overrides
     * it for that purpose should override [monthEventBanner] too, since the two now answer different
     * questions.
     */
    val eventBanner: (HijriEvent, isToday: Boolean) -> String = { event, _ -> eventName(event) },

    /**
     * The line the banner shows: **every** observance the displayed month holds, in calendar order —
     * or, once a day that carries an observance has been tapped, that **one** observance.
     *
     * Takes the whole list rather than one event because the bar is permanently present: while the
     * user pages through months it is the calendar's only opportunity to say what the month *is*, and
     * one nearest event left the rest of the month's history unsaid. The tap narrows it to a list of
     * one — so an implementation needs no second entry point, and a locale that styles or joins the
     * names styles the tapped day the same way.
     *
     * It no longer attaches the day to the name ("Ashura · Day 10"): the filled cells already answer
     * *when*, while a day number in a single line made the bar read as a footnote on a date the user
     * had not asked about.
     *
     * The default joins [eventName] with a middle dot, and never returns an empty string for a
     * non-empty list. [noEventBanner] covers the month that holds none. A locale owns the separator
     * and any conjunction it wants, which is why this is a lambda rather than a `List` and a constant
     * glue string.
     */
    val monthEventBanner: (List<HijriEvent>) -> String = { events ->
        events.joinToString(" · ") { eventName(it) }
    },

    /**
     * The banner's text for a month that holds no observance at all.
     *
     * **Non-empty by default**, which is what makes the bar permanently present: the report asked for
     * an always-visible historical-events line, and a bar that disappears on most Hijri months is the
     * thing it was reporting. The line is reserved rather than removed for the same reason the
     * widget's footer reserves one — a bar that comes and goes moves the calendar under the user's
     * finger.
     *
     * Return `""` to draw nothing when the month is empty.
     */
    val noEventBanner: String = "No historical event this month",

    /** A Gregorian [year] with its era appended, or bare when [gregorianEra] is empty. */
    val gregorianYearWithEra: (year: Int) -> String = { year ->
        if (gregorianEra.isEmpty()) "$year" else "$year $gregorianEra"
    },

    /**
     * Formats the header's title line from a month name and a year.
     *
     * Return the **whole** string so ordering and numeral system are the caller's. The built-in
     * default joins them with a space in Western digits, which is what the header used to do inline
     * and which left a locale unable to reorder them or to write the year in its own digits — the
     * module's own Urdu preview showed Arabic-Indic day figures under a Western-numeral year.
     *
     * `monthName` is [hijriMonthName]'s output, so a label set that already localizes month names
     * gets a consistent title for free.
     */
    val headerTitle: (monthName: String, year: Int) -> String = { month, year ->
        "$month $year"
    },

    /**
     * Formats the header's Gregorian line for a month spanning [first] to [last] inclusive.
     *
     * Return the **whole** string so the range separator, the ordering and the year rendering are the
     * caller's. The default reproduces the built-in English output exactly, which is what the header
     * used to hardcode in three branches.
     */
    val gregorianMonthRangeLabel: (first: LocalDate, last: LocalDate) -> String =
        { first, last -> defaultGregorianMonthRangeLabel(first, last) },
    val previousMonthContentDescription: String = "Previous month",
    val nextMonthContentDescription: String = "Next month",
    /**
     * Returns the accessibility content description for a single day cell.
     *
     * The default is `"Day 15"` — **no month, no year, no weekday**, and no distinction between a
     * leading day of the previous month and the current one. A screen-reader user swiping a row
     * hears "Day 1" for a date that may be a month away.
     *
     * It is the default rather than the only option because it reproduces long-standing output, but
     * a localized calendar should almost certainly replace it: this is the string a screen reader
     * actually reads, so it carries more weight than the visual labels. Related: the cell also sets
     * `disabled()` in its own semantics, so the *state* is announced properly and does not depend on
     * the `", disabled"` suffix this lambda appends.
     */
    val dayContentDescription: (CalendarDay) -> String = { day ->
        val suffix = if (day.isDisabled) ", disabled" else ""
        "Day ${day.dayOfMonth}$suffix"
    },

    /**
     * The name of a **Pakistani national day**, in the host's language.
     *
     * Separate from [eventName] because the two tables are separate types ([HijriEvent] vs
     * [PakistanEvent]) fixed to different calendars. There is no sensible single lambda that would
     * accept both without a cast: a host translating these is translating a different list of names.
     *
     * Mirrors [eventName] otherwise: takes the event rather than a `String` so a locale can use
     * [PakistanEvent.key] instead of the English name, and never returns `null` because a missing
     * translation is far more discoverable as English than as a blank.
     *
     * **Appended rather than grouped with [eventName]**, like [monthEventBannerWithPakistan] below:
     * these are `@Immutable` data classes on a published artifact with a binary-compatibility golden
     * file, and inserting a parameter mid-list renumbers every later `componentN` — changing the
     * *return type* of `component8()`, which is a `NoSuchMethodError` in a consumer's compiled code
     * rather than a recompile. A trailing addition costs a recompile; a mid-list one costs a crash.
     */
    val pakistanEventName: (PakistanEvent) -> String = { it.nameEn },

    /**
     * The line the banner shows when the month holds **Pakistani national days as well** — the
     * Pakistan-mode counterpart of [monthEventBanner].
     *
     * Takes one ordered list of [CalendarEvent] rather than two lists because ordering is the whole
     * problem: a Gregorian-fixed national day (14 August) and a Hijri observance (Ashura) interleave by
     * day within a month, and a lambda that received two lists could not restore that order. The list
     * is already in cell order, so the names read the same way the grid does.
     *
     * **Only called when a national day is present.** A host that has customised
     * [monthEventBanner] keeps that lambda for every month without one, which is why this is an
     * additional field rather than a replacement: changing [monthEventBanner]'s parameter type would
     * break every existing label set. A host that wants its own phrasing for Pakistan months overrides
     * this one; the default joins [eventName] and [pakistanEventName] with the same middle dot
     * [monthEventBanner] uses.
     */
    val monthEventBannerWithPakistan: (List<CalendarEvent>) -> String = { events ->
        events.joinToString(" · ") { event ->
            when (event) {
                is PakistanEvent -> pakistanEventName(event)
                is HijriEvent -> eventName(event)
            }
        }
    },
)

/**
 * The built-in English rendering of a Gregorian month range, three cases: one month, a same-year
 * span, and a cross-year span.
 *
 * Extracted from the header so it can be both [HijriCalendarLabels]' default and a directly tested
 * pure function. It is intentionally *not* a locale-aware formatter — that is what
 * [HijriCalendarLabels.gregorianMonthRangeLabel] is for — but it is the documented default, so its
 * exact output is pinned by tests.
 */
internal fun defaultGregorianMonthRangeLabel(first: LocalDate, last: LocalDate): String {
    // CalendarNames is 0-indexed by Month.ordinal. HijriCalendarLabels.gregorianMonthName is
    // *1-based* by contract, which is why the original passed `ordinal + 1` into it; indexing the
    // list directly here means using the ordinal as-is. Getting that wrong shifts every month by
    // one and throws on December -- which is what the first version of this did.
    fun monthName(date: LocalDate): String = CalendarNames.englishGregorianMonths[date.month.ordinal]
    return when {
        first.month == last.month && first.year == last.year ->
            "${monthName(first)} ${first.year}"
        first.year == last.year ->
            "${monthName(first)} - ${monthName(last)} ${first.year}"
        else ->
            "${monthName(first)} ${first.year} - ${monthName(last)} ${last.year}"
    }
}
