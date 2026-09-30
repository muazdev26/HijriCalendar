# Issue 03: One builder for the rendered month

**Severity:** High
**Blocks:** [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** [Issue 01](UI-01-scoped-overrides-ignored.md)
**Module:** `calendar-ui` + `calendar-core`

---

## Problem

`HijriCalendarGrid` takes **both** a state and the month resolved from it, then ignores the month:

```kotlin
// calendar-ui/.../HijriCalendarGrid.kt:34-46
public fun HijriCalendarGrid(
    state: HijriCalendarState,
    calendarMonth: CalendarMonth,   // used only for initialMonth (:46) and the weekday label (:102)
    ...
) {
    val initialMonth = remember { calendarMonth.yearMonth }
```

and rebuilds the month per visible page from the state at `:126`:

```kotlin
val calMonth = remember(
    month, state.firstDayOfWeek, state.selectedDate, state.selectedPakistanDate,
    state.selectedObservedDate, state.minDate, state.maxDate, state.adjustmentDays,
    state.pakistanDates, state.weekendDays, state.overridesRevision,   // ← 11 hand-listed keys
) { month.toCalendarMonth(...) }
```

Three consequences:

**1. The visible month is generated twice per composition.** `HijriCalendar.kt:66` reads
`state.calendarMonth` (a full 42-cell build behind a `derivedStateOf`) and passes it in;
`HijriCalendarGrid.kt:126` builds the same month again. Both run on every state change.

**2. Two sources for one value.** `calendarMonth.firstDayOfWeek` feeds `DayOfWeekLabels` (`:102`)
while `state.firstDayOfWeek` feeds the page build (`:127`). Same value today; two sources
permanently.

**3. Correctness depends on hand-maintained key lists.** Any new state input must be added to
`HijriCalendarGrid.kt:113-125` *and* `HijriCalendar.kt:45-51`, and a miss is silent — the cache
just keeps serving a stale month. That is exactly how
[UI-01](UI-01-scoped-overrides-ignored.md) shipped: `state.monthLengths` is on neither list.

The comment at `HijriCalendar.kt:64-65` — *"The rendered grid. Backed by `derivedStateOf` in
`HijriCalendarState`, so repeated reads within this composition are cheap."* — is **false**. The
pager renders its own grid; `state.calendarMonth` is not what is painted. A comment that
misdescribes the code is how the duplication survived review.

## Why it matters

`CORE-01` exists because the month-range derivation was duplicated into four sites and every future
mode became a four-site edit with no compiler help. This is the same failure one layer up, in the
same module, three months later. The AGENTS.md rule already exists — *"must both build data
through the top-level helpers … so they cannot drift"* — it was just written for the widget module.

## Proposed change

**1. One builder on the state.** Add to `HijriCalendarState`:

```kotlin
/** The [yearMonth] grid in this state's space and configuration. */
public fun calendarMonthFor(yearMonth: HijrahYearMonth): CalendarMonth
```

and express `calendarMonth` as `calendarMonthFor(_currentMonth)`. Every input — including
`monthLengths` — is now read from one place that the state owns, so nothing can be forgotten.

**2. The grid consumes the builder, not the state fields.** Replace the `remember` block at
`:113-137` with `remember(month) { state.calendarMonthFor(month) }`. The 11-key list disappears
because `state` is `@Stable` and the builder reads every input internally.

**3. Delete `calendarMonth` from `HijriCalendarGrid`'s signature.** It is public API, so this is a
breaking change — do it under `apiDump` with a `CHANGELOG.md` entry. A signature that demands a
state *and* its resolved month and then discards one of them is worse than no signature.

**4. Fix the comment.** "The rendered grid" is not what `HijriCalendar.kt:66` is; say what it
actually is (the header's month identity and weekday origin) or delete it.

**5. `HijriCalendar.kt:45-51` keeps its key list** — the header's `remember` is deliberate and
correct, to avoid forcing the 42-cell build. But it should read `state.monthLengths` and
`state.overridesRevision`, or delegate to `remember(currentMonth) { … }` against
`state.calendarMonth` now that the builder is cheap to reach. Prefer whichever keeps the
"no grid build for a header" property.

## Done when

- [ ] `month.toCalendarMonth(...)` no longer appears in `calendar-ui`
- [ ] `state.calendarMonth` and the rendered page produce equal `days` for the same month
- [ ] No `remember` key list in `calendar-ui` enumerates `HijriCalendarState` properties
- [ ] `HijriCalendarGrid` no longer takes a `calendarMonth` parameter
- [ ] A test asserts header and grid agree in all three spaces and under a scoped override table
- [ ] `apiDump` run; `CHANGELOG.md` records the signature break

## Notes

- **The header's "no grid build" optimisation must survive.** `HijriCalendar.kt:52-54` is the best
  reasoning in the module. Whatever shape the builder takes, `state.calendarMonthFor(ym)` must not
  be called from the header if that would build 42 cells — or if it does, the derived grid must be
  cached per year-month so the second read is free.
- A per-year-month cache on the state is the more complete version of this ticket (it would also
  fix the "visible month built twice" half). It is not required for correctness, only for the
  duplicate build to go away. Keep it as a follow-up if [UI-11](UI-12-day-cell-decomposition.md)
  shows a profile.
- `HijriCalendarState` is in `calendar-core`, so this ticket touches a module covered by
  `CORE-03`'s BCV gate. Run `apiCheck` on the macOS job.