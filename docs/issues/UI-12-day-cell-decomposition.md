# Issue 12: Split the day cell's colour and geometry resolution

**Severity:** Low
**Blocks:** [Issue 14](UI-14-stale-detekt-baseline.md)
**Blocked by:** —
**Module:** `calendar-ui`

---

## Problem

`HijriCalendarDayCell` is a 177-line file whose single composable resolves **six** sequential `when`
blocks (about 36 of those lines) before emitting a single node
(`HijriCalendarDayCell.kt:50-85`):

```kotlin
val contentColor      = when { isSelected / isDisabled / !isCurrentMonth / isWeekend / else }
val gregorianColor    = when { isSelected / !isCurrentMonth / else }
val backgroundColor   = when { isSelected / else }
val showTodayBorder   = day.isToday && !day.isSelected
val borderColor       = when { isSelected / showTodayBorder / else }
val borderWidth       = when { isSelected -> TodayBorderWidth / showTodayBorder / else }
```

detekt already flags it as both `LongMethod` and `CyclomaticComplexMethod`
(`config/detekt/baseline-calendar-ui.xml:6,16`).

### The precedence is not just undocumented — one branch is wrong

`isSelected` is tested **before** `isDisabled` (`:50-56`), so a cell that is both selected and
disabled renders with the *selected* treatment — filled accent container, `onPrimary` content — while
being unclickable and stripped of `Role.Button`. It looks fully enabled and silently refuses taps.

This is reachable, not theoretical. `isDisabled` comes from core's `DateWindow`, which is resolved
against **`adjustmentDays`**, and that value is *mutable* (`setAdjustmentDays`), while `isSelected`
is not cleared when the window moves past it. Call `setAdjustmentDays` so the selected day falls
outside the bounded range and the cell lands in exactly this state.

The `when` order is the whole contract, and nothing asserts it. The `DayCellStyle` extraction below
is what makes it assertable — add a case per branch *and* one for selected+disabled, or the next
reorder reintroduces this.

Every one of these is a **pure function of `(CalendarDay, HijriCalendarColors)`**, and none of it is
tested. The file's own test (`HijriCalendarDayCellTest.kt`) only covers
`toArabicIndicNumerals`.

## Two smaller costs in the same file

**1. Both strings are computed unconditionally** (`HijriCalendarDayCell.kt:42-48`):

```kotlin
val hijriText = if (useArabicIndicNumerals) day.dayOfMonth.toArabicIndicNumerals() else day.dayOfMonth.toString()
val gregorianText = day.localDate.day.toString()
```

In `DateDisplayMode.GREGORIAN_ONLY` the Hijri string — including a `toString()` + `map` +
`joinToString` allocation for Arabic-Indic — is built and thrown away for all 42 cells. Neither is
`remember`ed, so it repeats every recomposition.

**2. `require` throws from composition** on a public composable
(`HijriWeekRow.kt:23`):

```kotlin
require(days.size == 7) { "HijriWeekRow requires exactly 7 days, got ${days.size}" }
```

`HijriWeekRow` takes a caller-supplied `List`, so a consumer's malformed list crashes composition
rather than failing a unit test. Its message says what happened, not what the caller must do.

## Proposed change

**1. Extract a pure resolver.**

```kotlin
internal data class DayCellStyle(
    val contentColor: Color,
    val gregorianColor: Color,
    val backgroundColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val enabled: Boolean,
)

internal fun HijriCalendarDayCell.dayCellStyle(colors: HijriCalendarColors): DayCellStyle
```

One `when` tree returning one value instead of six parallel ones. The composable shrinks to
`Text` selection, and the colour precedence becomes testable — which matters, because that
precedence is a **design** (selected beats disabled beats outside-month beats weekend) that nothing
currently asserts.

**2. Compute only the text the mode shows.** Move `hijriText` / `gregorianText` inside the
`when (dateDisplayMode)` branches at `:108-152`, and `remember` each on its `CalendarDay`.

**3. Move the `require` out of composition.** Either drop it (the internal call site always passes
7) or, better, make `HijriWeekRow` `internal` per
[UI-06](UI-06-public-surface-and-docs.md) so a malformed list is not a consumer-reachable crash.

**4. Give `DayOfWeekLabels` a `modifier` parameter** (`HijriCalendarGrid.kt:153`) so the weekday row
can be padded and tested in isolation.

## Done when

- [ ] `HijriCalendarDayCell.kt` has no composable longer than ~50 lines
- [ ] `LongMethod` and `CyclomaticComplexMethod` are gone from `baseline-calendar-ui.xml` for this
      file (see [UI-14](UI-14-stale-detekt-baseline.md))
- [ ] Tests assert the colour precedence: selected > disabled > outside-month > weekend
- [ ] A test asserts `GREGORIAN_ONLY` renders no Hijri numeral
- [ ] No `require` executes inside a composable reachable from the public API

## Notes

- **Do not extract for its own sake.** The justification is testability of a design decision, not
  line count. A reviewer who reads this ticket as "the file is long, split it" will produce six
  one-line functions and no tests.
- `toArabicIndicNumerals` (`HijriCalendarDayCell.kt:157-176`) is already `internal` and well
  tested — five cases including negatives. It is the model for what the rest of the file's logic
  should look like.
- `DateDisplayMode.BOTH` renders the Hijri line at `bodySmall` and the Gregorian at `labelSmall` with
  `spacedBy = 0.dp` (`:129-133`). The line heights are implicit and untested; they interact with
  [UI-04](UI-04-accessible-text-scaling.md)'s font-scale cap. Coordinate if both are open.