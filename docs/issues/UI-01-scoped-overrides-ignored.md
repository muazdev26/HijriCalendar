# Issue 01: Scoped month-length overrides are ignored by every render path

**Severity:** Critical
**Blocks:** [Issue 03](UI-03-one-month-builder.md), [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** —
**Module:** `calendar-ui`
**Status:** Shipped — see "Shipped" below.
**Do this first.** Two arguments; verified correctness defect.

---

## Problem

`HijriCalendarState` owns a scoped month-length override table
([`CORE-02`](CORE-02-scope-month-overrides.md)):

```kotlin
// calendar-core/.../HijriCalendarState.kt:76
public val monthLengths: HijriMonthLengths = HijriMonthOverrides.current,
```

and uses it for its own derived month (`HijriCalendarState.kt:139-140`). **No code outside
`calendar-core` reads it.** Verified:

```bash
rg "monthLengths" --glob '*.kt' . | rg -v "^./calendar-core"    # zero hits
```

Three sites in this module build calendar data. Two omit `overrides`, so they fall back to the
process-global `HijriMonthOverrides.current`:

| Site | Builds | `overrides` |
|---|---|---|
| `HijriCalendarGrid.kt:126` | **the cells the user sees** | *omitted* → process global |
| `HijriCalendar.kt:55` | the header's Gregorian range | *omitted* → process global |
| `HijriCalendarState.kt:139` | `initialMonth`, weekday label, one `LaunchedEffect` key | `= monthLengths` ✓ |

The two `remember` key lists that cache those results omit it too — `HijriCalendar.kt:45-51`
(keys `currentMonth`, `labels`, `adjustmentDays`, `pakistanDates`, `overridesRevision`) and
`HijriCalendarGrid.kt:113-125` (11 keys). `overridesRevision` is present, so the cache *does*
invalidate when `setMonthLength` is called; it just rebuilds against the wrong table. That is why
the defect is silent rather than glitchy.

## Proof

A scratch test in `calendar-ui/src/commonTest` forcing 1447-09 (a 30-day UAQ month) to 29 days in
a **scoped** table, then comparing what the state reports against what the grid page builds:

```
UAQ 1447-09 length = 30, forced to 29
A state.calendarMonth rendered days = [1..29]                     ← scoped-aware
B grid page          rendered days = [1..30]                     ← process global
A range = 2026-02-18..2026-03-19
B range = 2026-02-18..2026-03-19
observed truth      = 2026-02-18..2026-03-18                     ← neither
```

Two defects in one trace:

1. **The grid renders 30 days when the scoped table says 29.** The consumer sees a month the
   library's own data model says does not exist.
2. **The header disagrees with the observed calendar**, reporting `..2026-03-19` where the last
   observed day is `2026-03-18`. Note that header and grid *agree with each other* — both ignore
   the table — so `CORE-01`'s "the header can never describe a different month than the grid below
   it" still holds by accident. Fix the grid alone and the divergence `CORE-01` exists to prevent
   reappears. **Both must be fixed in the same change.**

## Why nothing caught it

Both samples drive the process global, where the default makes the omission a no-op:
`sample-android-app/…/CalendarViewModel.kt:70,91` and
`sample-shared/…/SamplePreferences.kt:61` (*"Restores … into the process-global"*). The one
Compose test tears down with `HijriMonthOverrides.clearAll()`
(`CalendarScreenDeviceUiTest.kt:69`) and computes expected ranges through the default
`overrides`. And its assertions cannot see the bug anyway — see UI-02.

## Proposed change

**1. Pass `overrides` at both call sites.** In `HijriCalendarGrid.kt` add
`overrides = state.monthLengths` to the `toCalendarMonth(...)` call, and in `HijriCalendar.kt`
add `overrides = state.monthLengths` to `resolveGregorianMonthRange(...)`.

**2. Add `state.monthLengths` to both `remember` key lists.** `overridesRevision` is not a
substitute: two states can share a revision while holding different tables.

**3. Add a regression test** that forces a length in a scoped table and asserts the *rendered*
`dayOfMonth` sequence, not a range. Per the note in
[`CORE-01`](CORE-01-mode-aware-month-range.md) — *"roundtrip tests can't catch it"* — assert an
absolute anchor, and force the **opposite** of the calculated length so the assertion cannot
silently degrade into asserting nothing.

## Done when

- [x] `rg "toCalendarMonth|resolveGregorianMonthRange" calendar-ui/src/commonMain` shows an
      explicit `overrides` argument at every call site. As shipped this grep now resolves to exactly
      one file — `RenderMonth.kt` — which is the point: there is nowhere else it *can* be called
      from.
- [x] Every builder call passes the state's table. Post-fix this reads as 2 hits for
      `rg "state\.monthLengths"` (the two `remember` key lists) plus 2 for
      `rg "overrides = monthLengths"` (the two builders in `RenderMonth.kt`, where the receiver
      *is* the state so the property needs no `state.` prefix).
- [x] A test asserts the rendered `dayOfMonth` list matches a scoped override, using a month whose
      calculated length differs from the forced one
- [x] A test asserts the header's `GregorianMonthRange` matches the grid's, under a scoped override
- [x] All four test suites green; `:sample-android-app:assembleDebug` unchanged

## Shipped

Both call sites now read every input from the state through one internal seam, and the `remember`
key lists carry `state.monthLengths` alongside `state.overridesRevision`.

- **New `calendar-ui/.../RenderMonth.kt`** — two `internal` extension functions,
  `HijriCalendarState.renderMonthFor(yearMonth)` and `renderGregorianRangeFor(yearMonth)`. Both the
  grid and the header call these; neither calls `toCalendarMonth` or `resolveGregorianMonthRange`
  directly any more. This is the extra step the original "two arguments" fix needed: a test can only
  exercise the render path's *argument list* if the argument list is a named thing, otherwise the
  test has to re-type it and drifts the moment the bug returns. Its KDoc states the invariant and
  says not to inline the calls back into a composable.
- **`state.monthLengths` is a reference key, not a content key.** `HijriMonthLengths` has no
  `equals`, so identity is exactly right here: a different table is a different identity even when
  two tables happen to share a revision counter. `state.overridesRevision` is kept because it
  covers in-place mutation of a single table. The two are complementary and both are needed.
- **`RenderMonthOverridesTest` (11 cases).** Verified to fail against the defect: reintroducing the
  omission turns **8 of the 11 red**. The three that stay green are green for the right reasons —
  `setupForcesTheOppositeOfTheCalculatedLength` is a guard on the guard (if the calendar ever made
  this month 30 days, the other assertions would be testing a no-op), and
  `theStatesOwnMutatorAdvancesTheCacheRevision` tests core, not this module. The fourth interesting
  case is `renderGregorianRangeFor_matchesTheGridsFirstAndLastDay`, which **survives** the defect
  because the header and the grid both ignored the table, so they agreed with each other — the
  transcript in the Problem section predicted exactly this. `renderGregorianRangeFor_reflectsAScopedOverride`
  is the assertion that catches the header's own omission, and it goes red.
- **`baseline-calendar-ui.xml` regenerated.** Fix-then-regenerate, per
  [UI-14](UI-14-stale-detekt-baseline.md). Dropping an import changes detekt's per-file
  `ImportOrdering` ID, so the two existing entries stopped matching and the build failed until the
  baseline was refreshed. Diff confirms **only those two IDs changed** — no entry added, none
  removed, and the new file produced no findings.

No public API changed. `RenderMonth.kt` is `internal`; `apiCheck` passes without an `apiDump`.

## Notes

- **The minimal fix does not close the class.** A hand-maintained key list will drop a field again.
  [UI-03](UI-03-one-month-builder.md) deletes both lists by having one builder own the inputs.
  Ship the two-argument fix now; treat UI-03 as what makes it durable.
- **`HijriCalendarState.calendarMonth` is scoped-aware, so `UI-03` must not regress it.** The
  duplication is that the state builds the month *and* the grid builds it again; the fix is for the
  grid to consume the state's builder, not to drop the state's.
- `resolveGregorianMonthRange`'s `overrides` parameter has been **dead at every call site outside
  core** since `CORE-02` added it. This ticket is the whole of the work `CORE-01` deferred.

### Known limitation, not fixed here

`_overridesRevision` advances only through the **state's own** mutators
(`setMonthLength` / `clearMonthLength` / `clearAllMonthLengths`,
`HijriCalendarState.kt:196-212`). Mutating a `HijriMonthLengths` instance the state holds, from
outside the state, produces a correct value from `renderMonthFor` but does **not** invalidate the
grid's `remember` — the builder reads the table live, the cache does not. The documented mutation
path is via the state, and `theStatesOwnMutatorChangesWhatIsRendered` covers it. Making the
unscoped path safe means either dropping the `remember` or having the state observe the table,
which is [UI-03](UI-03-one-month-builder.md)'s decision to make, not this ticket's.