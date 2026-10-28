package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public data class CalendarDay(
    val hijrahDate: HijrahDate? = null,
    val pakistanDate: PakistanHijriDate? = null,
    val observedDate: ObservedHijriDate? = null,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isDisabled: Boolean,
    val isWeekend: Boolean,
    val adjustmentDays: Int = 0,
) {
    /**
     * The notable date this cell carries, or `null` when it carries none.
     *
     * Resolved in the **same order [HijriCalendarState.selectDay] routes in** — Pakistan, then
     * observed, then Umm al-Qura — so tapping a day and looking up its event cannot disagree about
     * which coordinate was meant. A Pakistan-calendar user on 10 Muharram gets Ashura for the date
     * they are actually looking at.
     *
     * Deliberately **not** falling back to a default coordinate when all three are null. That
     * substitution is the kind of silent guess that makes a bug untraceable: a cell with no date has no
     * event, and inventing one would attach Ashura to a placeholder.
     *
     * Note the ordering does not match [dayOfMonth]'s, which puts observed first. That asymmetry is
     * pre-existing and is [dayOfMonth]'s to explain; this follows the router because the router is what
     * decides which date the user *chose*.
     */
    val event: HijriEvent?
        get() = pakistanDate?.let { HijriEvents.forDate(it.month, it.day) }
            ?: observedDate?.let { HijriEvents.forDate(it.month, it.day) }
            ?: hijrahDate?.let { HijriEvents.forDate(it.month.number, it.day) }

    /**
     * The Pakistani national day this cell carries, or `null`.
     *
     * **Only in Pakistan mode.** A cell is in that space when it carries a [pakistanDate], which is
     * exactly the condition this reads — a Gregorian-fixed national day belongs to the Ruet-e-Hilal
     * calendar the user chose, not to the Umm al-Qura grid they did not. Returning it in Calculation
     * mode would put 14 August's fill on a grid whose whole claim is "these are calculated dates".
     *
     * Resolved from the cell's **real-world Gregorian day** ([localDate], which already undoes
     * [adjustmentDays]), because a national day is a Gregorian fact. Using the stored Hijri coordinate
     * here would move Independence Day whenever the user changed the moon-sighting shift.
     *
     * Independent of [event]: a day can carry both a Hijri observance and a national day (Ashura can
     * land on 14 August), and neither is a correction of the other. See [notableEvents].
     */
    val pakistanEvent: PakistanEvent?
        get() = if (pakistanDate == null) {
            null
        } else {
            val gregorian = localDate
            PakistanEvents.forDate(gregorian.month.ordinal + 1, gregorian.day)
        }

    /**
     * Every notable date this cell carries, in the order a banner should name them: the Pakistani
     * national day first, then the Hijri observance.
     *
     * A list rather than the single [event] because the two tables are independent — a Gregorian national
     * day and a Hijri observance can share a cell — and collapsing them would silently drop one. The
     * order is deliberate: in a Pakistan-mode calendar the national day is the specific, local fact,
     * and the Hijri observance recurs the same way in every country. A renderer with room for one line
     * can take [firstOrNull]; the banner names all of them.
     *
     * Empty in Calculation mode when the day has no Hijri observance, since [pakistanEvent] is gated on
     * Pakistan mode.
     */
    val notableEvents: List<CalendarEvent>
        get() = buildList {
            pakistanEvent?.let(::add)
            event?.let(::add)
        }

    /** Whether this cell carries any notable date at all — the mark a grid fills the cell for. */
    val hasNotableEvent: Boolean
        get() = pakistanEvent != null || event != null

    /**
     * The day number of this cell in whatever date space it represents.
     *
     * Returns `0` for disabled placeholder cells where all date fields are null
     * (e.g. out-of-range Pakistan cells with `pakistanDate = null`).
     */
    val dayOfMonth: Int
        get() = observedDate?.day ?: hijrahDate?.day ?: pakistanDate?.day ?: 0

    /**
     * The weekday of the real-world day this cell represents, i.e. the grid column it
     * belongs to. With [adjustmentDays] != 0 this differs from `hijrahDate.dayOfWeek`.
     */
    val dayOfWeek: WeekDay get() = WeekDay.fromDayOfWeek(localDate.dayOfWeek)

    /**
     * The Gregorian day this cell represents in the real world.
     * With [adjustmentDays] != 0 this differs from `hijrahDate.toLocalDate()` by
     * exactly [adjustmentDays] days. In Pakistan mode it differs from
     * `pakistanDate.localDate` the same way, i.e. the observed `pakistanDate` of a
     * real-world day [G] is `gregorianToHijri(G + adjustmentDays)`.
     *
     * For disabled placeholder cells where all date fields are null, returns today's
     * Gregorian date as a safe fallback.
     */
    val localDate: LocalDate
        get() {
            val base = when {
                observedDate != null -> observedDate.localDate
                hijrahDate != null -> hijrahDate.toLocalDate()
                pakistanDate != null -> pakistanDate.localDate
                else -> return kotlin.time.Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
            return base.minus(adjustmentDays, DateTimeUnit.DAY)
        }
}
