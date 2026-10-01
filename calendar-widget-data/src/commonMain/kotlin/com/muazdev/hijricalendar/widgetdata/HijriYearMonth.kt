package com.muazdev.hijricalendar.widgetdata

import kotlinx.serialization.Serializable

/**
 * A Hijri year and month, as a type.
 *
 * **Why this exists instead of `Pair<Int, Int>`** (WD-07). A pair of ints has no type-level
 * distinction between a Hijri year-month and any other pair, so a renderer can swap the components
 * and get a grid for "month 1447" — which is out of range, returns `null`, and leaves a silently
 * blank widget. `.first` being the year is a convention, not a constraint, and the whole point of
 * this module is that a renderer *cannot forget to thread a value through correctly*.
 *
 * It also removes a third-party **alpha** type from the published ABI. `offsetHijriMonth` used to
 * return `com.abdulrahman_b.hijrahdatetime.HijrahYearMonth?` — a type from a `2.0.0-alpha07`
 * dependency, in `.klib.api` and `.api`. When that library reaches 2.0.0 final and reshapes the type,
 * every consumer of this one breaks at compile time and cannot work around it, because the type is
 * in *our* signature, not theirs. This module still uses the alpha type internally, where it is a
 * dependency rather than a contract; the boundary now stops here.
 *
 * Swift gets the most out of it: `resolveGridMonth` can take a nullable "today" and answer `null`
 * when it does not know one, so the iOS timeline stopped needing a hardcoded fallback month (see
 * `HijriTimelineProvider.swift`).
 */
@Serializable
public data class HijriYearMonth(
    /** The Hijri (AH) year. */
    public val year: Int,
    /** The Hijri month, 1-12. */
    public val month: Int,
) {
    init {
        // Folds in WD-09's month validation: a value that cannot exist should not be constructible,
        // because the alternative is a builder that returns `null` for an out-of-range month and a
        // blank widget for everything downstream.
        require(month in 1..12) { "month must be 1..12, was $month" }
    }

    /** `1447-9`, matching how the month is written everywhere a human reads it. */
    override fun toString(): String = "$year-$month"
}

/** A [HijriYearMonth] or null — the shape "this widget's grid month, if we can determine it". */
public typealias HijriYearMonthOrNull = HijriYearMonth?
