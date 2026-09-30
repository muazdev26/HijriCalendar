package com.muazdev.hijricalendar.core

/**
 * The single owner of the calendar display names that ship with the library.
 *
 * Before this existed, the **English** lists were written out twice — once as private constants in
 * `calendar-ui`'s `HijriCalendarLabels` and once in `calendar-widget-data`'s
 * `WidgetLocalization` — with nothing keeping them in step. The Urdu lists were already shared
 * correctly (see [UrduCalendarNames]); it was the English defaults that could silently drift, so
 * a widget's month header and the in-app calendar's could disagree for a new month name.
 *
 * Both modules now read from here:
 *
 * - `calendar-ui` — `HijriCalendarLabels`' default lambdas
 * - `calendar-widget-data` — `WidgetLocalization.englishHijriMonthNames` and friends
 *
 * This is *built-in* data only. Supplying your own names is done through
 * `HijriCalendarLabels` in the UI module and the `localized*MonthNames` parameters in the widget
 * projection; nothing here is a localization framework.
 */
public object CalendarNames {

    /**
     * The twelve English Hijri month names, 1-indexed (Muharram .. Dhul-Hijjah).
     *
     * Transliteration follows the ISO/ALA-LC style (`Rabi' al-awwal`, `Sha'ban`,
     * `Dhu al-Qa'dah`), which is what the published widget API already emitted. The in-app
     * calendar used a different spelling for six of these twelve names; consolidating changed
     * its output, which is the intended outcome of a single owner — see
     * `docs/issues/CORE-06-core-purity.md`.
     */
    public val englishHijriMonths: List<String> = listOf(
        "Muharram",
        "Safar",
        "Rabi' al-awwal",
        "Rabi' al-thani",
        "Jumada al-ula",
        "Jumada al-akhirah",
        "Rajab",
        "Sha'ban",
        "Ramadan",
        "Shawwal",
        "Dhu al-Qa'dah",
        "Dhu al-Hijjah",
    )

    /** The twelve English Gregorian month names, 1-indexed (January .. December). */
    public val englishGregorianMonths: List<String> = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /**
     * English weekday short names, as a 7-entry list in [WeekDay] enum order (Saturday first).
     *
     * Identical to each entry's [WeekDay.shortName]; exposed as a list because both consumers
     * need a positional 7-entry view to index by column.
     */
    public val englishWeekdayShortNames: List<String> = WeekDay.entries.map { it.shortName }

    /** English weekday names keyed by [WeekDay]. */
    public val englishWeekdays: Map<WeekDay, String> = WeekDay.entries.associateWith { it.shortName }
}
