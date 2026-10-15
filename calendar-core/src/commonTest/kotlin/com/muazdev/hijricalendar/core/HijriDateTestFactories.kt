package com.muazdev.hijricalendar.core

/**
 * Test-only constructors for the two value types that **store** their real-world Gregorian day.
 *
 * [PakistanHijriDate] and [ObservedHijriDate] no longer derive `localDate` from the process-global
 * [HijriMonthOverrides.current] (that derivation is what made a scoped-table widget label the wrong
 * day), so a test that spells out Hijri coordinates must also name the table that resolved them.
 * These helpers resolve against the table the caller names — the process default by default —
 * exactly as [PakistanHijriCalendar.gregorianToHijri] and
 * [ObservedHijriCalendar.observedToGregorian] do, so a directly-built value carries the same
 * Gregorian day a converted one would.
 *
 * [PakistanHijriDate] equality ignores `localDate`, but [ObservedHijriDate]'s does not, and the
 * window/selection logic reads it either way, so these are the honest default rather than a
 * placeholder.
 */
internal fun pakistanDate(
    year: Int,
    month: Int,
    day: Int,
    overrides: HijriMonthLengths = HijriMonthOverrides.current,
): PakistanHijriDate = PakistanHijriDate(
    year = year,
    month = month,
    day = day,
    localDate = PakistanHijriCalendar.hijriToGregorian(year, month, day, overrides),
)

/** Observed-calendar counterpart of [pakistanDate]. */
internal fun observedDate(
    year: Int,
    month: Int,
    day: Int,
    monthLength: Int = ObservedHijriCalendar.observedLength(year, month),
    overrides: HijriMonthLengths = HijriMonthOverrides.current,
): ObservedHijriDate = ObservedHijriDate(
    year = year,
    month = month,
    day = day,
    monthLength = monthLength,
    localDate = ObservedHijriCalendar.observedToGregorian(year, month, day, overrides),
)
