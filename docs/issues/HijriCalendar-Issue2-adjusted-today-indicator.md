# Issue 2: With moon-sighting adjustment, filled circle appears on the unadjusted date

**Status:** Resolved — both parts. Shipped; the library half as a construction-time normalization.
**Filed against:** `1.0.0-alpha03`

**Library:** https://github.com/muazdev26/HijriCalendar
**Consumer app:** AlkhairJantari
**Library version:** `1.0.0-alpha03`

---

## Root cause found — two parts

### Part A: Consumer bug (already fixed in the app)

The consumer passed `initialSelectedDate` converted from today **without** applying the adjustment:

```kotlin
// BUGGY (app-side):
val todayHijri = Clock.System.now()
    .toLocalDateTime(TimeZone.currentSystemDefault()).date
    .toHijrahDate()                       // <-- Umm al-Qura conversion, no +adjustmentDays

rememberHijriCalendarState(
    initialSelectedDate = todayHijri,     // lands on the WRONG cell when adjustment != 0
    adjustmentDays = adjustmentDays,
)
```

Result with `adjustmentDays = +1`: the **selected fill** renders on the calculated date, while the library's internally-correct `isToday` ring renders on the adjusted date — exactly the "two circles" confusion.

This is what the user saw; it is fixed in the app by shifting before converting:

```kotlin
// FIXED:
.date.plus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate()
```

### Part B: Library footgun worth fixing (`calendar-core`)

> The library's README says `state.selectedDate` and `isToday` live in *adjusted space*, and `goToToday()` correctly applies `adjustmentDays`. But `HijriCalendarState(initialSelectedDate = ...)` and `rememberHijriCalendarState(initialSelectedDate = ...)` accept a raw HijrahDate and store it as-is. Any caller converting "today" themselves will silently get a selection on the unadjusted cell.
>
> **Fix in `HijriCalendarState.kt`:** normalize `initialSelectedDate` into adjusted space at construction time, e.g.
>
> ```kotlin
> // In HijriCalendarState init (or a secondary constructor/helper):
> private fun adjust(date: HijrahDate?): HijrahDate? =
>     date?.toLocalDate()?.plus(adjustmentDays, DateTimeUnit.DAY)?.toHijrahDateOrNull() ?: date
>
> private var _selectedDate by mutableStateOf(adjust(initialSelectedDate))
> ```
>
> Also consider exposing `selectToday()` or making the KDoc on `initialSelectedDate` explicit: *"Expected to be an already-adjusted Hijri date; use `goToToday()` to select today in adjusted space."*
>
> Add unit tests: given `adjustmentDays = 1`, constructing state with an unadjusted initial selected date must highlight the same Gregorian day that `isToday` highlights. Then release as `1.0.0-alpha04`.

---

## Status

- [x] App-side fix applied in `HijriCalendarScreen.kt` (initial month + selected date now computed in adjusted space, keyed on `adjustmentDays`)
- [x] Library-side normalization of `initialSelectedDate`

## Resolution

Part A was the consumer's bug and is fixed in the app. Part B was real, and the fix landed in a
stronger form than the one proposed here.

**`HijriCalendarState` now normalizes at construction.** `HijriCalendarState.kt:101` reads
`mutableStateOf(initialSelectedDate.adjustToAdjustedSpace())` — the proposed `adjust()` helper, as a
named extension, applied in the property initializer rather than a secondary constructor. The
[KDoc on `initialSelectedDate`](
../../calendar-core/src/commonMain/kotlin/com/muazdev/hijricalendar/core/HijriCalendarState.kt)
now states the contract in the second sentence: *"May be given as an unadjusted (Umm al-Qura)
`HijrahDate`; it is normalized into adjusted space at construction, so passing
`today.toHijrahDate()` with `adjustmentDays != 0` still highlights the same real-world day that
`isToday` highlights."* That is the documentation half of the suggestion, and it makes the
footgun unreachable rather than merely discouraged.

**`isToday` cannot disagree with the selection, because the UI never computes it.** This is the part
worth recording, since it is why the fix is structural rather than cosmetic. The filled circle
landed on the wrong day because two pieces of the library were answering "today" in two different
spaces — the selection from a caller-supplied `HijrahDate`, the highlight from the adjusted clock.
There is now a single owner: `CalendarDay.isToday` is set in `calendar-core`
(`CalendarMonthExt.kt:70` for Umm al-Qura, and independently in the Pakistan and observed
builders), each from `todayHijriDate(adjustmentDays)` in its own space. `calendar-ui` reads the flag
and never derives it, so there is no longer any code path in which a UI caller can put the two out
of step.

**One asymmetry remains, deliberately.** `initialSelectedPakistanDate` and
`initialSelectedObservedDate` are *not* normalized — their KDoc says so explicitly ("expected to be
an already-shifted date"). Those spaces carry month-length overrides, so a day beyond a month's
calculated length has no unadjusted counterpart to normalize *from*; guessing one would silently
move the selection. Leaving them caller-supplied is the honest contract.

**Released:** unreleased at time of writing — Part B is listed in `CHANGELOG.md` under Unreleased.
It post-dates the `1.0.0` tag, so it ships in the next minor/major bump rather than a re-cut.
