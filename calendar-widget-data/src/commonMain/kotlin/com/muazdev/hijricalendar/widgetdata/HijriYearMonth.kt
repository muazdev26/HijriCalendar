package com.muazdev.hijricalendar.widgetdata

/**
 * A Hijri year and month, as a type.
 *
 * **Why a named type instead of `Pair<Int, Int>`** (WD-07). A pair of ints has no type-level
 * distinction between a Hijri year-month and any other pair, so a renderer can swap the components
 * and get a grid for "month 1447" — which is out of range, returns `null`, and leaves a silently
 * blank widget. `.first` being the year is a convention, not a constraint, and the whole point of
 * this module is that a renderer *cannot forget to thread a value through correctly*.
 *
 * **It now lives in `calendar-core`.** The type was introduced here to keep the third-party alpha
 * `HijrahYearMonth` out of the widget ABI; the same argument applies to every module, so the value
 * moved to the data module and this name stays as an alias. Source compatibility is preserved
 * (`import com.muazdev.hijricalendar.widgetdata.HijriYearMonth` still compiles); the class is now
 * `com.muazdev.hijricalendar.core.HijriYearMonth` in the binary, which is why 2.4.0 notes it under
 * migration.
 */
public typealias HijriYearMonth = com.muazdev.hijricalendar.core.HijriYearMonth

/** A [HijriYearMonth] or null — the shape "this widget's grid month, if we can determine it". */
public typealias HijriYearMonthOrNull = HijriYearMonth?
