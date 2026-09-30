# Issue 06: Four public composables and two public modifiers have no contract

**Severity:** High
**Blocks:** —
**Blocked by:** [Issue 01](UI-01-scoped-overrides-ignored.md), [Issue 03](UI-03-one-month-builder.md), [Issue 04](UI-04-accessible-text-scaling.md), [Issue 05](UI-05-pager-window-crash.md), [Issue 07](UI-07-previews-out-of-abi.md), [Issue 09](UI-09-pager-effect-sync.md)
**Module:** `calendar-ui`

---

## Problem

Ten of fourteen `commonMain` files have **zero** KDoc blocks:

| File | KDoc | Lines |
|---|---|---|
| `DateDisplayMode.kt` | 4 | 23 |
| `HijriCalendarLabels.kt` | 5 | 50 |
| `HijriCalendar.kt` | **0** | 160 |
| `HijriCalendarDayCell.kt` | **0** | 177 |
| `HijriCalendarGrid.kt` | **0** | 205 |
| `HijriCalendarHeader.kt` | **0** | 95 |
| `HijriCalendarColors.kt` | **0** | 23 |
| `HijriCalendarDefaults.kt` | **0** | 51 |
| `HijriWeekRow.kt` | **0** | 44 |
| `util/ModifierExtensions.kt` | **0** | 32 |
| `preview/Previews.kt` | **0** | 317 | *(see UI-07)* |

`README.md` does not mention `HijriCalendarGrid`, `HijriWeekRow`, `HijriCalendarHeader`,
`HijriCalendarDayCell`, `calendarDayCell`, or `clickableIfEnabled`. So four public composables and
two public modifiers are documented in **neither** KDoc nor README. Their only description is the
ABI dump.

## Why the surface is too wide

Four of these are not components; they are this component's internals that happen to be `public`:

| Declaration | Why it should not be public API |
|---|---|
| `HijriCalendarGrid(state, calendarMonth, …)` | Demands a state *and* its resolved month, then discards the month. A caller who passes a stale `calendarMonth` gets a pager anchored to the wrong month with no compiler help. See [UI-03](UI-03-one-month-builder.md). |
| `HijriWeekRow` | A layout primitive that `require`s exactly 7 days (`HijriWeekRow.kt:23`) and throws **from composition**. Its contract is an assertion, not documentation. |
| `Modifier.calendarDayCell(size)` | Literally `this.size(size)`. One call site (`HijriCalendarDayCell.kt:89`). A public API obligation for a no-op wrapper. |
| `Modifier.clickableIfEnabled(…)` | Internal helper with a `@Composable` receiver-ish signature and an undocumented `indication = null`. See [UI-08](UI-08-touch-feedback.md). |
| `HijriCalendarHeader` | Has real content, but its `canGoToPreviousMonth` / `canGoToNextMonth` mirror core's `isNavigableWithin` rule (`HijriCalendarState.kt:581-585`) with no way for a consumer to learn that. |

## Specific gaps

**`HijriCalendarColors`** — 14 fields, no KDoc. One undocumented *derivation* matters:
`gregorianDayContentColor = dayContentColor.copy(alpha = 0.6f)` (`HijriCalendarDefaults.kt:31`)
means the two are not independent, which a consumer overriding one field would not expect.

**`HijriCalendar.kt`** — three public entry points (`HijriCalendar`,
`rememberHijriCalendarState`, `rememberSaveableHijriCalendarState`) with no doc at all, including
the 30-passing-argument wrappers that duplicate core's signatures.

**`HijriWeekRow.kt:23`** — a `require` with no stated contract. Its message says what happened,
not what the caller must do.

**One public signature is self-contradictory in shape.** `dayContent` is
`(@Composable () -> Unit)?` on `HijriCalendarDayCell:38` but `(@Composable (CalendarDay) -> Unit)?` on
`HijriWeekRow:21` and on `HijriCalendar`. `HijriWeekRow:39` bridges the two with
`dayContent?.let { { it(day) } }`. So a consumer reading the *cell's* signature — the one named
after the thing it replaces — sees the less useful of the two forms, with no `CalendarDay` to work
from, while the slot's own name and the type at the call site disagree. One shape, carrying the
day, is the answer; the bridge allocates two lambdas per cell per composition as a side effect.

**`DayOfWeekLabels`** (`HijriCalendarGrid.kt:153`) — takes no `modifier`, so the weekday row cannot be padded or reused.

## Proposed change

**1. Demote the internals to `internal`.** `Modifier.calendarDayCell`, `Modifier.clickableIfEnabled`,
`HijriWeekRow`, and `HijriCalendarGrid` all become `internal`. For a `1.0.0-alpha` published
artifact this is free; after 1.0 it is not, which is the argument for doing it now.

**2. Keep `HijriCalendar`, `HijriCalendarHeader`, `HijriCalendarDayCell`, `HijriCalendarColors`,
`HijriCalendarLabels`, `HijriCalendarDefaults`, `DateDisplayMode`** — those are a real component
API. Document them to the standard `DateDisplayMode` and `HijriCalendarLabels` already set.

**3. The benchmark is in this module, not core's.** `DateDisplayMode`'s KDoc documents *why a
decision was made and what would break it* and names the hazard by name. That is the bar. Concretely:

- `HijriCalendar`'s KDoc must say what the module **cannot** do (no fixed-height grid at unbounded
  font scale — see [UI-04](UI-04-accessible-text-scaling.md)) and what it deliberately does not
  build (no year picker, no range selection UI).
- `HijriCalendarHeader`'s must say `canGoTo*` mirrors a core navigability rule and that a
  hand-rolled header must reproduce it.
- `HijriCalendarColors`'s must say which defaults are derived from which.

**4. README: document the eight entry points it currently omits**, or say explicitly that the
supported surface is `HijriCalendar` + the colours/labels objects and the rest is internal.

## Done when

- [ ] `rg "^\s*public (fun|val)" calendar-ui/src/commonMain` shows only the intended surface
- [ ] Every remaining public declaration has KDoc; a `README.md` section names each one
- [ ] `HijriCalendarColors`' documented defaults identify the `dayContentColor` derivation
- [ ] `apiDump` run; `CHANGELOG.md` records each demotion as a breaking change

## Notes

- **This ticket is last on purpose.** It documents the shape that Issues 1, 3, 4, 6 and 8 decide.
  Writing it earlier guarantees writing it twice — the same mistake
  [`CORE-03`](CORE-03-api-stability.md) was sequenced last to avoid.
- `explicitApi()` is already on for this module, so nothing slipped through accidentally. But
  `explicitApi()` requires *stating* `public`, not *justifying* it. That distinction is the whole
  gap.
- `apiCheck` is what surfaced [UI-07](UI-07-previews-out-of-abi.md) — 14 preview composables in
  the published dump. Keep the BCV gate; it is doing real work.