# Issue 06: Four public composables and two public modifiers have no contract

**Severity:** High
**Blocks:** —
**Blocked by:** [Issue 01](UI-01-scoped-overrides-ignored.md), [Issue 03](UI-03-one-month-builder.md), [Issue 04](UI-04-accessible-text-scaling.md), [Issue 05](UI-05-pager-window-crash.md), [Issue 07](UI-07-previews-out-of-abi.md), [Issue 09](UI-09-pager-effect-sync.md)
**Module:** `calendar-ui`
**Status:** Shipped — see "Shipped" below.

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

**Disabled day cells are not announced as disabled.** `clickableIfEnabled`
(`ModifierExtensions.kt:22-29`) omits `Modifier.clickable` entirely when disabled, which removes the
`OnClick` semantics action but never sets `SemanticsProperties.Disabled`. TalkBack therefore sees a
target with *no action* rather than a *disabled* one — and `assertIsNotEnabled()` on such a node
fails. The user only learns the day is disabled because the default
`dayContentDescription` appends `", disabled"` to the string, a convention any custom label lambda
is free to drop. Found by the UI-02 harness on its first run. The fix is `disabled()` in the cell's
`semantics` block (`HijriCalendarDayCell.kt:90-93`); it belongs here because it changes an observable
accessibility surface of a published composable. `HijriCalendarInteractionTest` pins both halves
(`assertHasNoClickAction`, and the `", disabled"` string) so the mechanism that works today cannot
regress unnoticed.

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

- [x] `rg "^\s*public (fun|val)" calendar-ui/src/commonMain` shows only the intended surface
- [x] Every remaining public declaration has KDoc; the README names each one
- [x] `HijriCalendarColors`' documented defaults identify the `dayContentColor` derivation
- [x] `apiDump` run; `CHANGELOG.md` records each demotion as a breaking change

## Shipped

The public surface is now **nine declarations**, listed in a README table that says what each is for:

`HijriCalendar` · `HijriCalendarState` · `rememberHijriCalendarState` /
`rememberSaveableHijriCalendarState` · `defaultOnDayClick` · `HijriCalendarColors` ·
`HijriCalendarLabels` · `HijriCalendarDefaults` · `DateDisplayMode` · `HijriCalendarDayCell` ·
`HijriCalendarHeader`

- **`HijriCalendarGrid`, `HijriWeekRow`, `Modifier.calendarDayCell` and `Modifier.clickableIfEnabled`
  are no longer public.** The first two were the eight-parameter-bag duplication this review opened
  with; the modifiers were 16 lines of `if (enabled)` and a no-op `this.size(size)` wrapper for one
  call site. `calendarDayCell` is deleted outright rather than internalised — it was never a
  behaviour. `apiDump` shows exactly those three `*Kt` holders removed and nothing else.
- **`HijriCalendarDayCell` and `HijriCalendarHeader` stay public, deliberately.** The cell is the
  reusable *unit* for building your own grid or week row; the header is the reusable *chrome* for
  building your own header layout. The two that are internal are the ones that only make sense as
  part of this component's internals.
- **`HijriCalendar.header` is new**, for a layout that needs the calendar somewhere other than the
  top. That is the answer to "I need the arrows somewhere else", and it is easier than calling the
  public header by hand.

### Three accessibility and API fixes that came with it

- **A disabled day cell now sets `disabled()` in its semantics.** `clickableIfEnabled` removes the
  `OnClick` action when disabled, which left TalkBack with a target and no action rather than a
  disabled target. Found by the UI-02 harness on its first run and recorded in UI-02.
- **The `dayContent` slot has one shape.** It was `(@Composable () -> Unit)?` on the cell and
  `(@Composable (CalendarDay) -> Unit)?` everywhere else, bridged by `dayContent?.let { { it(day) } }`
  — so the component named after the thing it replaces showed the *less* useful signature, and the
  bridge allocated two lambdas per cell per composition.
- **`Role.Button` is set in one place.** It was set in the cell's `semantics` block *and* in the
  `clickable` call; the effective value depended on modifier order, which nobody was meant to know.

### Documentation

- **`HijriCalendar`'s KDoc now states what it does not do** — no year or month picker, no range or
  multi-select, no fixed-height mode, no bounds beyond `minDate`/`maxDate` — and how to read a
  three-slot `CalendarDay`. Those are the questions a consumer arrives with, and finding out by
  editing the library is worse than reading them.
- **`HijriCalendarColors`' one derived default is documented on the type**, because
  `gregorianDayContentColor = dayContentColor.copy(alpha = 0.6f)` means overriding only the parent
  also moves the sub-label — the most surprising thing in the file.
- **`HijriCalendarDefaults` now says why the cell is 48dp** (it holds the `BOTH` stack at the font
  scale cap), so the next contributor does not "fix" it.
- **The README gained "The whole public surface"** — a table of every entry point, plus a note that
  `HijriCalendarGrid`/`HijriWeekRow` are internal and why, and the `CalendarDay` three-slot reading
  rule. It previously mentioned 4 of 11.

### One ticket's worth of work detekt forced

Demoting the signatures changed the detekt IDs of the baselined `LongMethod` /
`CyclomaticComplexMethod` findings, so the build failed. That is UI-12's extraction, which the
ticket had argued for on its own merits and which turned out to be needed to keep `calendar-ui`
compiling, so it landed here rather than being re-baselined. `HijriCalendarDayCell` went from 109
lines to 78 via two extractions:

- `CalendarDay.cellStyle(colors)` — the six parallel `when` blocks collapsed into one resolver
  returning a `DayCellStyle`. Those blocks encode *design* (selected > disabled > outside-month >
  weekend) and nothing asserted them, which is why they were extracted rather than merely moved.
- `CellText(...)` / `CellTextLine(...)` — three near-identical `Text` calls, now one.

Also: only the date the display mode actually draws is built. In `GREGORIAN_ONLY` the Hijri string,
and its Arabic-Indic allocation, used to be built and discarded for all 42 cells on every
recomposition.

## Notes



## Notes

- **This ticket is last on purpose.** It documents the shape that Issues 1, 3, 4, 6 and 8 decide.
  Writing it earlier guarantees writing it twice — the same mistake
  [`CORE-03`](CORE-03-api-stability.md) was sequenced last to avoid.
- `explicitApi()` is already on for this module, so nothing slipped through accidentally. But
  `explicitApi()` requires *stating* `public`, not *justifying* it. That distinction is the whole
  gap.
- `apiCheck` is what surfaced [UI-07](UI-07-previews-out-of-abi.md) — 14 preview composables in
  the published dump. Keep the BCV gate; it is doing real work.