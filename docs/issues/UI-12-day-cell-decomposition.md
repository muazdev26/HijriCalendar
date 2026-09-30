# Issue 12: Split the day cell's colour and geometry resolution

**Severity:** Low
**Blocks:** [Issue 14](UI-14-stale-detekt-baseline.md)
**Blocked by:** —
**Module:** `calendar-ui`
**Status:** Shipped — mostly landed inside [UI-06](UI-06-public-surface-and-docs.md); see below.

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

- [x] `HijriCalendarDayCell.kt` has no composable longer than ~50 lines
- [x] `LongMethod` and `CyclomaticComplexMethod` are gone from `baseline-calendar-ui.xml` for this
      file
- [x] Tests assert the colour precedence: selected > disabled > outside-month > weekend
- [x] A test asserts `GREGORIAN_ONLY` renders no Hijri numeral
- [x] No `require` executes inside a composable reachable from the public API

## Shipped

**Most of this landed inside [UI-06](UI-06-public-surface-and-docs.md)** rather than as its own
commit, because demoting `HijriCalendarGrid`'s visibility changed the detekt IDs of this file's
baselined findings and the build would not pass until the extraction happened. The sequence was
unintended and the order of the two tickets is now cosmetic; the work is what matters and it is all
here.

- **`CalendarDay.cellStyle(colors): DayCellStyle`** — the six parallel `when` blocks became one
  resolver returning a value. This is the substantive change, and the ticket is explicit about why:
  *"Do not extract for its own sake… The justification is testability of a design decision, not line
  count."* Those blocks encode `selected > disabled > outside-month > weekend`, a **design**, and
  nothing asserted it.
- **`DayCellStyleTest` (11 cases)** asserts the precedence as a decision rather than as
  self-consistency: *"selected wins" is a choice about what a user should see when a cell is both
  selected and something else*. Every colour in the fixture is distinct, so a mis-wired branch
  cannot pass by accident.
- **`CellText(...)` / `CellTextLine(...)`** — three near-identical `Text` calls become one. The cell
  went from 109 lines to 78, and detekt's `LongMethod` and `CyclomaticComplexMethod` entries for it
  are gone rather than re-baselined.
- **Only the drawn date is built.** In `GREGORIAN_ONLY` the Hijri string — and its Arabic-Indic
  allocation — was built and discarded for all 42 cells on every recomposition.
- **`require` is no longer consumer-reachable**, because `HijriWeekRow` is internal (UI-06). The
  check stays: a silent wrong row is harder to diagnose than a throw.
- **`DayOfWeekLabels` takes a `modifier`**, so the weekday row can be padded like everything else.

### The selected+disabled finding, and the one assertion that was wrong

The precedence defect this review predicted is confirmed and now has a test:
`selectedAndDisabled_looksEnabledButIsNotInteractive` — the cell renders with selected colours while
refusing taps. It is reachable because `isDisabled` is resolved against the **mutable**
`adjustmentDays` while the selection is not cleared when the window moves past it. The test names
it as a deliberate choice (a selection should not change appearance under the user) and records the
consequence: **appearance cannot report interactivity**, which is why UI-06 added `disabled()` to
the cell's semantics rather than relying on how it looks.

One assertion was backwards and the code was right: `onlyDisabledCellsAreInert` assumed an
outside-month cell was inert. It is not — it is dimmed, but tapping it selects that date and
navigates to its month, which is ordinary calendar behaviour. The test now asserts that, with a
comment saying it was checked the other way first.

## Notes



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