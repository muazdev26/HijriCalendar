# Issue 02: No composable test harness, and the only Compose test never runs in CI

**Severity:** High
**Blocks:** [Issue 04](UI-04-accessible-text-scaling.md), [Issue 09](UI-09-pager-effect-sync.md)
**Blocked by:** —
**Module:** `calendar-ui` + `.github/workflows/build.yml`

---

## Problem

`calendar-ui/src/commonTest` was 193 lines across three files and covered exactly four pure
functions and two integer constants:

| File | Covers |
|---|---|
| `HijriCalendarGridMathTest.kt` | `monthOffset`, `plusPageOffset`, `PAGER_PAGE_COUNT % 2 == 1` |
| `HijriCalendarRangeLabelTest.kt` | `sameMonthRangeLabel` |
| `HijriCalendarDayCellTest.kt` | `toArabicIndicNumerals` |

**No test composes a single composable.** `desktopTest` runs green and proves the arithmetic
helpers work. All four public composables — 652 of the module's 1202 `commonMain` lines — are
untested. *(Both figures predate UI-01 and UI-05, which added `RenderMonth.kt` and `PageWindow.kt`;
the module is now 1362 lines across 14 files and the suite is 674 lines / 50 tests.)*

The repository's only Compose test is
`sample-android-app/src/androidTest/kotlin/…/CalendarScreenDeviceUiTest.kt` (11 tests, 311 lines,
written by `cbd0bf8` whose message claims "20 UI tests"). It has three independent problems:

**1. It does not run in CI.** `.github/workflows/build.yml` runs
`:sample-android-app:assembleDebug`, never `connectedDebugAndroidTest`.

**2. It is not in the documented verification loop.** `AGENTS.md` names
`./gradlew :calendar-ui:desktopTest …`, which cannot reach `androidTest`. Per its own docstring it
requires `./gradlew :sample-android-app:connectedDebugAndroidTest` and a connected device.

**3. It tests the sample, not the library.** It drives `CalendarScreen`, so it only covers the one
composition path that happens to use the library — and it cannot see
[UI-01](UI-01-scoped-overrides-ignored.md), because it tears down with
`HijriMonthOverrides.clearAll()` and computes expected observed ranges through the *default*
`overrides` parameter.

## The gap that matters

The module's observable behaviour is **pairs of values that must agree**:

- header's `GregorianMonthRange` ↔ the cells in the grid below it
- `HijriCalendarState.currentMonth` ↔ `pagerState.currentPage`
- requested `Locale`/locale → Arabic-Indic numerals, RTL layout
- declared `@Immutable` ↔ the actual stability of the fields

Every existing test asserts each value **independently**. The device test is the clearest
example: it checks the grid with `assertCountEquals(42)` and `dayCell(N)` content-description
matches, and checks the header range separately with `assertHeaderRangeShown`. Nothing compares a
cell's date against the header. That single missing assertion is what [UI-01](UI-01-scoped-overrides-ignored.md)
would have been caught by.

## Proposed change

**1. Add a `runComposeUiTest` harness to `calendar-ui/commonTest`.** Compose Multiplatform's
multiplatform UI test artifact runs on desktop without a device, so it lands in the command
`AGENTS.md` already documents. Needs `implementation(compose.uiTest)` in `commonTest`.

**2. Write the agreement assertions, not just rendering assertions.** First tests:

- header `GregorianMonthRange` equals `state.calendarMonth`'s, in **all three** spaces and with a
  scoped override table set — the assertion that would have caught UI-01
- `dateDisplayMode = GREGORIAN_ONLY` renders no Hijri numeral; `BOTH` renders both
- a disabled cell has no `Role.Button` and does not invoke `onDayClick`
- `HijriWeekRow` with 7 days renders 7 cells; the `require` is exercised as a *test*, not a crash
- the pager settles on the page `state.currentMonth` names after `goToNextMonth()`

**3. Move the device test's value, then delete it.** The `gridRendersCompleteMonthWithHeader…`,
`displayModesSwitch…` and `urduLabelsLocalize…` cases become desktop tests. Keep only what genuinely
needs a device (real font scale, real ripple, real window insets) and move those to
`calendar-ui/src/androidTest` so they live with the module they test.

**4. Wire it into CI.** Add a desktop UI-test step to the `android-desktop` job. If the device
tests stay, add an emulator job — or delete them and say so, but do not leave a test file that
looks like coverage and runs nowhere.

## Done when

- [x] `./gradlew :calendar-ui:desktopTest` composes real UI and fails on a broken grid
- [x] A test compares the header's `GregorianMonthRange` to the grid's cells in all three spaces
      **and under a scoped override table**
- [x] A test asserts the pager settles on `state.currentMonth` after `goToNextMonth()`
- [x] CI runs the new tests; a deliberately broken assertion fails the workflow
- [~] No Compose test remains outside `calendar-ui` — **deferred, documented.** See "Not done".

## Shipped

`calendar-ui` went from **50 tests, none of which composed anything**, to **77 tests across 4
composable suites**. No production code was touched — `git diff` on `commonMain` is empty, which is
the ticket's own instruction ("a harness commit that also changes behaviour is a harness commit
that cannot be bisected").

### The harness

- **`org.jetbrains.compose.ui:ui-test` added to the catalog**, wired into `commonTest`. It lands
  in `./gradlew :calendar-ui:desktopTest`, the command AGENTS.md already names.
- **Skiko's platform natives were missing.** `compose.uiTest` contributes `skiko-awt` (the JVM API)
  but not the natives, so the first run died with `LibraryLoadException: Cannot find
  libskiko-macos-arm64.dylib.sha256`. Fixed with `compose.desktop.currentOs` in **`desktopTest`
  only** — the plugin notation for the per-OS runtime variant, so it resolves to
  `skiko-awt-runtime-macos-arm64` here and `-linux-x64` on the ubuntu CI runner without hardcoding an
  OS. Desktop-only, hence not in `commonTest`.
- **`java.awt.headless=true` is set on the test task.** AWT probes for a display and the CI runner
  has none. Without this, the suite passes on a developer Mac and fails in CI for a reason
  unrelated to the calendar. **Caveat: the headless-Linux path could not be verified from a macOS
  host.** The first CI run is the real test of it; if it fails, the fallback is
  `xvfb-run -a` around the test step in `build.yml`, which ubuntu-latest has preinstalled.
- **No CI workflow change was needed.** `build.yml:49` already runs `:calendar-ui:desktopTest`, so
  every new composable test is in CI the moment this landed.

### Reading the grid back out

The default `dayContentDescription` is `"Day 15"` — no date, so useless for "is this cell the right
*day*". Rather than add an instrumentation hook to the product, the tests supply a
`HijriCalendarLabels` whose `dayContentDescription` encodes `date|isCurrentMonth`, and parse it back
out of the semantics tree. A test that needs a hook in the shipped API is a test that will not get
written.

The `isCurrentMonth` flag is carried because **a 42-cell grid is 42 *consecutive* real days**:
leading cells are the real days before the month starts and trailing ones the days after it ends, so
date contiguity cannot identify the month's own cells. A first attempt used the longest contiguous
run and selected all 42.

### The assertions, and three that were wrong before they were right

The ticket's central point is that the module's behaviour is *pairs of values that must agree*, and
that every pre-existing test checked each value alone. Three of the new tests were written wrong
first, and each was caught only by deliberately breaking the code:

1. **`headerAlwaysNamesTheMonthBelowIt_acrossAllThreeSpaces` was tautological.** It computed the
   expected header text from `renderGregorianMonthRangeFor` — the *same function the header calls* —
   and compared it to the header. It stayed green when the resolver was broken out from under the
   grid. Now split into one test per space, and every expectation is derived from the **painted
   cells** instead. With that fix, breaking the header's resolver turns
   `headerAndPaintedCellsAgreeUnderAScopedOverrideTable` red; before the fix, nothing composable
   went red at all.
2. **`goToPreviousMonth_returnsToTheStartingMonth` hardcoded `Dhu al-Qadah`.** CORE-06 chose the
   widget's transliteration, `Dhu al-Qa'dah`. All such assertions now go through
   `CalendarNames.englishHijriMonths`, so a name change is one edit.
3. **The pager tests drove navigation from the state side only.** Every one called
   `state.goToNextMonth()`, which exercises only the state → page effect. **Deleting the swipe
   collector's `state.goToMonth` call left the entire suite green**, because nothing ever moved the
   pager. Added three tests that swipe, targeting the *scrollable* node — the first attempt swiped
   a 48dp cell, so the gesture was far too short to page and the pager snapped back. With the
   pager node, deleting the state update turns `swipingTheGridAdvancesTheState` and
   `swipingBackwardsReturnsToTheStartingMonth` red.

### A real accessibility gap, found by the harness on its first run

`assertIsNotEnabled()` on a disabled day cell **fails**, and that is a finding rather than a test
bug. `clickableIfEnabled` simply omits `Modifier.clickable` when disabled, which removes the
`OnClick` semantics action but never sets `SemanticsProperties.Disabled`. TalkBack therefore sees a
target with no action, not a disabled one. The information only reaches the user because the
default `dayContentDescription` appends `", disabled"` — a string convention any custom label lambda
is free to drop. The tests pin both halves (`assertHasNoClickAction`, and the `", disabled"`
string), and the fix — adding `disabled()` to the cell's `semantics` block — is folded into
[UI-06](UI-06-public-surface-and-docs.md) because it changes an observable accessibility surface of
a published composable.

### The four suites

| Suite | Cases | What it is for |
|---|---|---|
| `ComposeHarnessSmokeTest` | 1 | The harness itself. If this fails, the others are unrunnable. |
| `HijriCalendarRenderAgreementTest` | 12 | Header ↔ painted cells, in UAQ / Pakistan / observed / adjusted, plus a scoped override table. |
| `HijriCalendarInteractionTest` | 14 | Pager ↔ state in **both** directions, tapping, disabled cells, the week-row contract. |
| *(existing 4 suites)* | 50 | Pure functions, page window, override plumbing. |

## Not done, deliberately

**The sample's `CalendarScreenDeviceUiTest` still does not run in CI.** It was left in place rather
than deleted, because what it covers is genuinely not duplicated here: it drives `CalendarScreen`
end to end through Koin, a real `Activity` and `rememberSaveable`/`SavedStateHandle`, which no
desktop composable test can reach. Its value that *was* duplicated — grid rendering, month
navigation, day selection, the three display modes, adjustment, Pakistan mode, overrides,
jump-to-today, Urdu labels — is now covered in `calendar-ui` and runs on every push.

So the honest state is: a test file that looks like automated coverage and is not. It is now
documented as manual in AGENTS.md, with its exact command:

```bash
./gradlew :sample-android-app:connectedDebugAndroidTest   # needs a connected device
```

**The real fix is an emulator CI job**, which is an infrastructure decision with a real cost (a
realtime emulator step adds several minutes and is a known flake source) and was not taken
unilaterally. If you want it, add a `reactivecircus/android-emulator-runner` step to `build.yml`
and the criterion is then genuinely closed.

One thing to watch: that test hardcodes `"Day N"` content descriptions and re-implements
`sameMonthRangeLabel` locally. [UI-10](UI-10-localization-completion.md) will change both, and it
will fail until those literals are pointed at `HijriCalendarLabels` — which the UI-10 ticket already
notes.

## Tests added

28 composable cases. Each was verified to go red against a deliberately introduced break:

| Break | Goes red |
|---|---|
| grid stops passing `overrides` | 8 unit + `headerAndPaintedCellsAgreeUnderAScopedOverrideTable` |
| header resolver stops passing `overrides` | 8 unit + `headerAndPaintedCellsAgreeUnderAScopedOverrideTable` |
| swipe collector stops updating the state | `swipingTheGridAdvancesTheState`, `swipingBackwardsReturnsToTheStartingMonth` |
| a string that is not rendered | `composesTheCalendarAndShowsTheMonthName` |




## Notes

- **Do not enable UI tests and immediately fix every bug they surface.** Land the harness with a
  small green suite first, then let [UI-02](UI-02-compose-test-harness.md)'s tests grow alongside
  the behavioural tickets they belong to. A harness commit that also changes behaviour is a harness
  commit that cannot be bisected.
- `runComposeUiTest` on desktop needs a Skiko-backed AWT runtime; if `desktopTest` cannot host it,
  add the harness to `calendar-ui/src/desktopTest` instead of `commonTest` and say so in the
  ticket — but do not fall back to "the device tests cover it".
- The commit `cbd0bf8` message claiming "20 UI tests" is worth fixing while this is open. The
  inaccuracy is how [UI-02](UI-02-compose-test-harness.md) stays invisible: the repo *looks* like
  it has UI coverage.