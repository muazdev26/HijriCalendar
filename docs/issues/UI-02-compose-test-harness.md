# Issue 02: No composable test harness, and the only Compose test never runs in CI

**Severity:** High
**Blocks:** [Issue 04](UI-04-accessible-text-scaling.md), [Issue 09](UI-09-pager-effect-sync.md)
**Blocked by:** —
**Module:** `calendar-ui` + `.github/workflows/build.yml`

---

## Problem

`calendar-ui/src/commonTest` is 195 lines across three files and covers exactly four pure
functions and two integer constants:

| File | Covers |
|---|---|
| `HijriCalendarGridMathTest.kt` | `monthOffset`, `plusPageOffset`, `PAGER_PAGE_COUNT % 2 == 1` |
| `HijriCalendarRangeLabelTest.kt` | `sameMonthRangeLabel` |
| `HijriCalendarDayCellTest.kt` | `toArabicIndicNumerals` |

**No test composes a single composable.** `desktopTest` runs green and proves the arithmetic
helpers work. All four public composables — 652 of the module's 892 lines — are untested.

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

- [ ] `./gradlew :calendar-ui:desktopTest` composes real UI and fails on a broken grid
- [ ] A test compares the header's `GregorianMonthRange` to the grid's cells in all three spaces
      **and under a scoped override table**
- [ ] A test asserts the pager settles on `state.currentMonth` after `goToNextMonth()`
- [ ] CI runs the new tests; a deliberately broken assertion fails the workflow
- [ ] No Compose test remains outside `calendar-ui`, or the ones that do are documented as
      device-only and wired into a job

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