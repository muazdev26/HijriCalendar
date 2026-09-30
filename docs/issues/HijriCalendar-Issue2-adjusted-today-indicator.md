# Issue 2: With moon-sighting adjustment, filled circle appears on the unadjusted date

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
- [ ] Library-side normalization of `initialSelectedDate` (this prompt) → next release
