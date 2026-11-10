package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A Hijri (AH) calendar date, owned by this library.
 *
 * **Why this exists.** The date arithmetic below this library is done by the third-party
 * `com.abdulrahman-b.hijrahdatetime` artifact, which is still `2.0.0-alpha07`. Its `HijrahDate`
 * appeared directly in this module's published signatures, so a 2.0.0 release that reshaped the
 * type would break every consumer at compile time — and there would be no workaround, because the
 * type is in *our* signature, not only in theirs. `HijriDate` is the boundary type: the alpha
 * dependency stays an implementation detail, and the public API names only this.
 *
 * It is a plain value — no time zone, no clock, no calendar system flag. Two dates are equal when
 * their year, month and day are, which is the identity a grid cell actually needs.
 *
 * [toHijrahDate] and [HijrahDate.toHijriDate] are the bridge, so a caller holding either type can
 * reach the other without re-parsing anything.
 *
 * @property year Hijri (AH) year, e.g. `1448`.
 * @property month Hijri month, `1`-`12` (`1` = Muharram).
 * @property day Day of month, `1`-`30`. A 29-day month's `30` is a value that converts to an
 *   invalid [HijrahDate]; [toHijrahDate] surfaces that by throwing, exactly as the engine does.
 */
// `@ObjCName` is what keeps these two types addressable from Swift under their own names.
// Kotlin/Native names an exported class after its *package* whenever that package's last segment is
// not unique across the framework, and `com.muazdev.hijricalendar.core` collides with
// `kotlinx.serialization.core` — without the annotation both would reach Swift as
// `Calendar_coreHijriDate` / `Calendar_coreHijriYearMonth`, and the widget code's `HijriYearMonth(…)`
// stops compiling. `exact = true` suppresses that mangling; it also requires an explicit ObjC name,
// hence the prefixed one. The pinned name is identical in every framework, which is what lets one
// Swift call site serve both the app and the widget extension.
@OptIn(ExperimentalObjCName::class)
@ObjCName(name = "CalendarHijriDate", swiftName = "HijriDate", exact = true)
@Immutable
@Serializable
public data class HijriDate(
    public val year: Int,
    public val month: Int,
    public val day: Int,
) {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
        require(day in 1..30) { "day must be 1..30, was $day" }
    }

    /** This date's month identity, without a day. */
    public val yearMonth: HijriYearMonth get() = HijriYearMonth(year, month)

    /** This date as the third-party `HijrahDate` the calendar math uses. */
    public fun toHijrahDate(): HijrahDate = HijrahDate(year, month, day)

    /** `1448-3-17`, matching how the date is written everywhere a human reads it. */
    override fun toString(): String = "$year-$month-$day"
}

/**
 * A Hijri (AH) year and month, owned by this library.
 *
 * The month-level counterpart of [HijriDate], and the same argument: the month arithmetic belongs
 * to the alpha dependency, but a published signature must not. `month` is an `Int` rather than the
 * library's own month type so the value stays comparable, printable and serializable without
 * reaching into a dependency that may reshape it.
 *
 * The constructor rejects a month outside `1..12`, because the alternative is a value that cannot
 * name a real month and produces a blank grid or a `null` from every lookup downstream.
 *
 * @property year Hijri (AH) year, e.g. `1448`.
 * @property month Hijri month, `1`-`12` (`1` = Muharram).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName(name = "CalendarHijriYearMonth", swiftName = "HijriYearMonth", exact = true)
@Immutable
@Serializable
public data class HijriYearMonth(
    public val year: Int,
    public val month: Int,
) {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
    }

    /** This month's [day] as a [HijriDate]. */
    public fun atDay(day: Int): HijriDate = HijriDate(year, month, day)

    /**
     * Moves [months] months from this one, carrying into the year.
     *
     * A negative value walks backwards, so "previous month" and "next month" are one function and
     * cannot disagree about the rollover.
     */
    public fun plusMonths(months: Int): HijriYearMonth {
        val proleptic = year * 12 + (month - 1) + months
        return HijriYearMonth(proleptic.floorDiv(12), proleptic.mod(12) + 1)
    }

    /** This month as the third-party `HijrahYearMonth` the calendar math uses. */
    public fun toHijrahYearMonth(): HijrahYearMonth = HijrahYearMonth(year, month)

    /** `1448-3`, matching how the month is written everywhere a human reads it. */
    override fun toString(): String = "$year-$month"
}

/** This date as a library-owned [HijriDate]. */
public fun HijrahDate.toHijriDate(): HijriDate =
    HijriDate(year = year, month = month.number, day = day)

/** This month as a library-owned [HijriYearMonth]. */
public fun HijrahYearMonth.toHijriYearMonth(): HijriYearMonth =
    HijriYearMonth(year = year, month = month.number)
