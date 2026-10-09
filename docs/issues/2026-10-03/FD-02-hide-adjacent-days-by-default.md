# FD-02: Adjacent-month days are always shown — make hiding them the default

**Issue:** #8
**Severity:** Medium
**Blocks:** FD-04, FD-09
**Blocked by:** None (can start immediately)
**Module:** `calendar-widget-data`, `calendar-widget-glance`, `calendar-ui`, `iosApp`
**Status:** Ready

---

## Problem

Every grid in the project renders a fixed 42 cells and paints all of them:

```kotlin
// calendar-core/src/commonMain/.../CalendarMonthExt.kt:78
val days = (0 until CalendarMonth.TOTAL_DAYS).map { offset -> ... }
```

The cells that belong to the previous or next Hijri month are **not disabled and not hidden**. They
are clickable, and tapping one navigates the grid to that month (`HijriCalendarState.selectDate`
moves `_currentMonth` when the date is outside it). All that distinguishes them is a dimmer colour:

```kotlin
// calendar-ui/.../HijriCalendarDayCell.kt:181-186
!isCurrentMonth -> colors.outsideMonthDayContentColor
```

Three problems with that, in order of how often they are felt:

**It wastes a quarter of the grid.** A 29-day Hijri month starting on Saturday under a Saturday
week-start needs 5 rows, not 6. The 6th row is entirely next month's. The user has paid for that
row in widget height and on screen, and is looking at six dimmed numbers at the bottom instead of
nothing — or, if they were hidden, at nothing plus a smaller widget.

**The dimmed numbers are the loudest thing in the cell.** `outsideMonthDayContentColor` is applied at
full opacity to the Hijri figure while the current month's own days sit at the same weight. The eye
reads the padding as content.

**It answers a question nobody asked.** The leading days tell you what last month ended on. On a
home-screen widget whose whole job is "what is the date today?", that is a third of the pixels spent
on a fact nobody is looking for.

## Why there is no flag

There is none, anywhere. `rg 'showsAdjacentDays|hideOutOfMonth|showLeading'` over the repo finds
only tests and prose. `CalendarDay.isCurrentMonth` exists and is set correctly by all three builders
(UAQ `CalendarMonthExt.kt:86`, Pakistan `:162`, observed `:240`), so the data is already there and
already correct — the renderers simply never consult it for anything but colour.

That is the whole shape of the defect: **the flag is missing, not the capability.**

## Proposed change

### The option

One new field on `WidgetOptions`:

```kotlin
val showAdjacentDays: Boolean = false,
```

`false` is the default, deliberately. Today every consumer sees adjacent days because there was no
alternative, not because they asked for it — and the complaint is that a month grid does not look
like a month grid.

### The grid

Filter before chunking, so rows collapse:

```kotlin
// calendar-ui/.../HijriCalendarGrid.kt:217 — becomes conditional
val visible = if (showAdjacentDays) days else days.filter { it.isCurrentMonth }
val weeks = remember(visible) { visible.chunked(CalendarMonth.DAYS_IN_WEEK) }
```

`CalendarMonth.TOTAL_DAYS` stays 42 — it is the *padded* month length and the widget render cache
keys on it. Only the grid stops padding.

Three renderers need this: `MonthGrid` (in-app), `MonthGrid` in `HijriCalendarWidget.kt:684`
(widget), and the Swift `LazyVGrid` in `HijriWidgetView.swift`.

### Hiding vs disabling

Hidden days must not become silently-invisible click targets. When `showAdjacentDays` is `false` the
cells are not in the list at all, which is the correct outcome — but the **in-app
`HijriCalendarState.selectDate` navigation behaviour must be unchanged**, because a programmatic
`selectDate(nextMonthFirstDay)` still has to move the grid. The flag governs rendering, not the
state machine. Keep those two apart and document it, or the next person will "fix" the flag by
adding a guard inside `selectDate` and break programmatic navigation.

## Adding an option field: the recipe this ticket establishes

Per `AGENTS.md`, a new `WidgetOptions` field is not a one-line change. This is the checklist the
three remaining schema tickets (FD-03, FD-04, FD-05) will copy:

- [ ] Field added with a default, so `decodeOrNull` on an old blob produces today's behaviour.
- [ ] `WidgetOptionsJson` needs **no** change — `ignoreUnknownKeys` + `encodeDefaults` + booleans
      have no legacy shape to fence. (A *name-backed enum* does, and gets a `LEGACY_*_KEY` handler
      like `WeekStart` has.)
- [ ] `./gradlew apiDump` and record the diff in `CHANGELOG.md`.
- [ ] `:calendar-widget-data:iosSimulatorArm64Test` — **WD-08.** The codec is shared by both
      platforms and compiling it proves nothing. A partial JSON blob written by a 2.0.0 build must
      still decode into this build with the new field at its default.
- [ ] iOS: the schema reaches Swift under two ObjC prefixes (`CalendarWidgetOptions` in the app,
      `WidgetCalendarWidgetOptions` in the extension). Both call sites compile unchanged, but
      `WidgetCatalogView.swift` needs a control for the new option.
- [ ] No line in `calendar-widget-glance/api/public-surface.txt` — the schema is in
      `calendar-widget-data`. No line in `ConsumerResolutionCheck.kt` unless the new field names a
      type from a new dependency. A `Boolean` names nothing.

## Acceptance criteria

- [ ] `WidgetOptions.showAdjacentDays` exists, defaults to `false`, and round-trips through
      `WidgetOptionsJson`.
- [ ] A widget saved by 2.0.0 decodes into this build with `showAdjacentDays = false` — asserted in
      the `iosSimulatorArm64Test` suite, not only on desktop.
- [ ] With the option off, the in-app grid and the widget grid render only current-month days and
      have 5 or 6 rows accordingly.
- [ ] With the option on, both grids render exactly what they render today — 42 cells, adjacent days
      dimmed.
- [ ] The row count of a known 29-day month is pinned by a test, so "collapse the rows" cannot
      silently regress to "collapse the rows but keep 6".
- [ ] `selectDate` navigation across a month boundary still works when the option is off.
- [ ] iOS: the same option is honoured by the widget extension grid, and `WidgetCatalogView.swift`
      exposes it.
- [ ] The sample's widget settings screen has a control for it.
- [ ] `apiDump` regenerated; `CHANGELOG.md` records the break.

## Breaking change

This is a **major**. A consumer on 2.0.0 sees a different grid height and different cell contents
after upgrading, with no action on their part. `CHANGELOG.md` needs an explicit entry saying the
default flipped, and `WidgetOptions.DEFAULTS` — not a data-class default — is what a fresh install
and the family mirror resolve to, so both must agree.