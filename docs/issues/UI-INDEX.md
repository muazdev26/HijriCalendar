# `calendar-ui` Architecture Review

**Date:** 2026-09-30
**Scope:** `calendar-ui/src/commonMain` (1362 lines, 14 files) and its test suite (~1400 lines, 77 tests)
**Status:** In progress — 7 open tickets, 6 shipped, 1 withdrawn
**Supersedes:** nothing. Companion to [`CORE-INDEX`](./INDEX.md), which reviews `calendar-core`.

> This is an **architecture** review of the UI module. `docs/issues/INDEX.md` reviews `calendar-core`
> and closed seven tickets; three of its findings land directly in this module's code. Read both —
> the boundary between them is where the worst defect here lives.

---

## Verdict

**5 / 10.** The module's *reasoning* is much better than its *engineering*. The comments
frequently name a risk correctly and then the code does the risky thing anyway — two of the three
best comments in the module describe problems the module does not solve. It ships with no
composable under test, one third of its public surface undocumented in both KDoc and README, and
a silent, verified correctness bug that renders the headline feature of the core review
unreachable.

| Dimension | Score | Note |
|---|---|---|
| Layering / boundary discipline | 8/10 | **Best dimension.** The three-way calendar branch is genuinely gone; `DateDisplayMode` correctly lives here. CORE-01/06 landed on this side. |
| Performance craft | 7/10 | Real `remember`s, header decoupled from the grid build. The duplicated month build is gone (UI-03); per-cell string work remains (UI-12). |
| Dependency hygiene | 6/10 | `material.icons.extended` for two core-set arrows, on a release train 5 minors behind the rest of Compose. |
| Documentation | 5/10 | 10 of 14 files have zero KDoc blocks; README covers 4 of 8 public entry points. |
| API surface | 5/10 | 4 public composables + 2 public modifiers with no contract; one public signature is self-contradictory. |
| Compose correctness | 6/10 | The pager's two effects are now one collector with a stated invariant (UI-09); no touch feedback (UI-08). |
| Correctness vs. `calendar-core` | 9/10 | **Was 2/10.** The render path and the state now build months through one definition on the state (UI-03), so they cannot disagree. |
| Test discipline | 4/10 | **Was 2/10.** Composable harness now in `desktopTest` (UI-02); still no coverage of the two `LaunchedEffect`s' restore behaviour, and the sample's device test is still not in CI. |

---

## The one finding that matters

**`HijriCalendarState.monthLengths` is never read outside `calendar-core`.** The scoped
month-length override table that [CORE-02](CORE-02-scope-month-overrides.md) shipped — the
headline fix of the core review — is unreachable from the UI. Verified, not inferred:

```
rg "monthLengths" --glob '*.kt' . | rg -v "^./calendar-core"    # → zero hits
```

Three call sites in this module build calendar data and **none of them pass `overrides`**:

| Site | Builds | `overrides` argument |
|---|---|---|
| `HijriCalendarGrid.kt:126` | the cells the user actually sees | *omitted* → process global |
| `HijriCalendar.kt:55` | the header's Gregorian range | *omitted* → process global |
| `HijriCalendarState.kt:139` (`calendarMonth`) | `initialMonth`, weekday label, one `LaunchedEffect` key | `= monthLengths` ✓ |

So with a scoped table forcing 1447-09 to 29 days, the state reports 29 days and the grid paints
30. See [UI-01](UI-01-scoped-overrides-ignored.md) for the transcript.

It has survived because both samples and the only Compose test all drive the **process global**,
where the default `monthLengths = HijriMonthOverrides.current` makes the omission invisible. See
["Why nothing caught this"](#why-nothing-caught-this).

---

## What is genuinely excellent

Preserve these. Each is a deliberate, documented decision.

**The header refuses to force the grid.** `HijriCalendar.kt:52-54`: *"Resolved from the
year/month alone rather than from `state.calendarMonth`, which would force the 42-cell grid to
be built for a header."* That is the exact instinct [CORE-01](CORE-01-mode-aware-month-range.md)
asked for, applied correctly.

**CORE-01 actually landed here.** The three-way space branch is gone from `calendar-ui`; the
module no longer imports `HijriMonthOverrides` or `ObservedHijriCalendar` at all. The ticket's
own success criterion (`rg "observedToGregorian|observedLength" calendar-ui` → nothing) holds.

**`DateDisplayMode`'s KDoc is the sharpest thinking in the module.** It states the
ordinal-vs-name persistence hazard explicitly and names `WeekDay.index` as the counterexample.
That is exactly the reasoning [CORE-06](CORE-06-core-purity.md) needed when it moved the enum out
of core.

**RTL is real, not an afterthought.** `AutoMirrored` arrows plus platform `Row`/`HorizontalPager`
mirroring give correct RTL for free — and `calendar-ui` correctly does *not* reimplement the
`language.isRtl XOR deviceRtl` rule that the Glance widgets need, because here the platform
already does it. Knowing the difference between the two problems is the skill.

**A pure function extracted solely so it could be unit-tested.** `sameMonthRangeLabel`
(`HijriCalendar.kt:153`) is `internal` and has four tests. This is why
[UI-02](#2--testing-nothing-composes-anything) can say "no composable is tested" rather than "nothing is tested".

---

## The 14 findings

Full tickets live in this directory. Summary and blocking edges:

```
                    ┌──────────────────────────────────────────┐
                    │ 1. Scoped overrides ignored  [Critical]  │──▶ fix first, 2 lines
                    └──────────────────────────────────────────┘
                                     ┆ durable fix
                                     ▼
        ┌──────────────────────┐  ┌───────────────────────────────┐
        │ 2. Compose test      │──▶│ 3. One builder for the       │
        │    harness + CI      │  │    rendered month  [High]     │
        └──────────┬───────────┘  └───────────────┬───────────────┘
                   │                              │
      ┌────────────┼──────────────┬───────────────┼──────────────┐
      ▼            ▼              ▼               ▼              ▼
  4. fontScale  9. Pager     6. Public      5. Pager       7. Previews
     forced 1   effect sync     surface +      window          out of ABI
      [High]     [Medium]       KDoc [High]   crash [High]     [Medium]
                   │               ▲              │              ▲
                   ▼               │              └──────────────┘
              11. Stability        └──────────────┘
                                        (6 blocked by 1,3,4,5,9)

   8. No touch feedback [Medium]  10. Localize header [Medium]
   12. Day-cell decomposition [Low]   13. Dependency hygiene [Low]
   14. Stale detekt baseline [Low]
```

**Execute in this order:** ~~1, 2, 3, 5, 6, 4, 9, 10, 8, 7, 12, 13, 14, 11.~~ **1 and 5 are
shipped.** For the rest, use: **7, 6, 10, 8, 12, 14, 13.**

The original order was wrong: it placed **6** sixth, while 6 is blocked by 1, 3, **4, 5, 7 and 9** —
three of which it was scheduled ahead of. 6 is also last-by-design for the reason `CORE-03` gives
(it documents whatever the others decide), so it cannot sit in the middle regardless. The corrected
order puts 6 after all six of its blockers, keeps 14 after 12, and drops 11 entirely.

**1, 2, 3, 4, 5 and 9 are shipped** (see the "Shipped" section of each ticket). UI-02 immediately
proved its own worth on its first run: it found a missing `SemanticsProperties.Disabled` on day
cells, and it showed that the pager's **page → state** direction had no coverage at all — deleting
the swipe collector's state update left the whole suite green.

**1 was first** because it was a
verified correctness defect; its minimal fix was two arguments, and `RenderMonth.kt` is what makes it
durable. **5 was second** because it was a verified crash, and because both touch
`HijriCalendarGrid`'s signature — doing 6 first would have meant documenting code that was about to
change.

For the remaining ten: **3 is next** — it deletes the mechanism that let UI-01 happen, and now has a
harness to write its agreement test against. Then **4 and 9** together (both behavioural, both now
testable), **7**, then **6** last for the same reason as `CORE-03`: it documents whatever 3, 4, 7 and
9 decide, so writing it earlier guarantees writing it twice. **10, 8, 12, 14, 13** are independent
after that.

| # | Finding | Severity | Ticket |
|---|---|---|---|
| 1 | Scoped overrides ignored by every render path | **Critical** | `UI-01-scoped-overrides-ignored.md` · **shipped** |
| 2 | No composable test harness; the only Compose test never runs in CI | High | `UI-02-compose-test-harness.md` · **shipped** (one decision deferred) |
| 3 | The grid re-derives the month from a hand-listed subset of the state | High | `UI-03-one-month-builder.md` · **shipped** |
| 4 | `fontScale` forced to 1 — text scaling off for the whole subtree | High | `UI-04-accessible-text-scaling.md` · **shipped** |
| 5 | `minDate`/`maxDate` beyond ±500 months **crashes** on first composition | High | `UI-05-pager-window-crash.md` · **shipped** |
| 6 | Four public composables + two public modifiers with no contract; 10/12 files undocumented | High | `UI-06-public-surface-and-docs.md` |
| 7 | 14 `@Preview` composables ship in the published ABI | Medium | `UI-07-previews-out-of-abi.md` |
| 8 | `indication = null` — 42 day cells with no touch feedback | Medium | `UI-08-touch-feedback.md` |
| 9 | Two `LaunchedEffect`s unsynchronised; rotation animates the pager | Medium | `UI-09-pager-effect-sync.md` · **shipped** |
| 10 | Header text and a11y strings are not localizable | Medium | `UI-10-localization-completion.md` |
| 11 | ~~`@Immutable` on `HijriCalendarLabels` is unsound~~ — **withdrawn, premise disproven** | — | `UI-11-stability-honesty.md` |
| 12 | Day cell resolves colour in six `when`s before emitting a node; `require` in composition | Low | `UI-12-day-cell-decomposition.md` |
| 13 | Icons dependency on a stale release train | Low | `UI-13-dependency-hygiene.md` |
| 14 | `baseline-calendar-ui.xml` no longer matches the code | Low | `UI-14-stale-detekt-baseline.md` |

### 1 — Scoped overrides are ignored by the render path

Covered above. The *mechanism* is that `HijriCalendarGrid` takes both a `HijriCalendarState` and
a `CalendarMonth` and then ignores the month, rebuilding it itself from a hand-maintained
11-property `remember` key list (`HijriCalendarGrid.kt:113-125`). `state.monthLengths` is not on
that list, so nothing complained. That is [UI-03](#3--the-grid-re-derives-the-month-itself).

### 2 — Testing: nothing composes anything

20 tests, 193 lines, and every one calls a pure function — `toArabicIndicNumerals` (5),
`monthOffset`/`plusPageOffset` (10), `sameMonthRangeLabel` (5). `calendar-ui/build.gradle.kts:15-17`
declares `commonTest.dependencies { implementation(libs.kotlin.test) }` and **nothing else**, so
`runComposeUiTest` does not even resolve. The single Compose test in the repo lives in
`sample-android-app/src/androidTest/.../CalendarScreenDeviceUiTest.kt` and CI runs
`:sample-android-app:assembleDebug` — never `connectedDebugAndroidTest` — so it has never executed
in CI. See [UI-02](UI-02-compose-test-harness.md).

### 3 — The grid re-derives the month itself

`HijriCalendarGrid.kt:46` keeps only `calendarMonth.yearMonth` and discards `calendarMonth.days`;
`:113-137` then calls `month.toCalendarMonth(…)` with nine arguments, guarded by an eleven-item
`remember` key that a future tenth parameter must be added to by hand. The header does the same
work independently at `HijriCalendar.kt:55`. Two builders, one of which is the bug in finding 1.

### 4 — Text scaling is switched off

`HijriCalendar.kt:70-76` wraps the header and grid in `Density(fontScale = 1f)`. Every `Text` in
the subtree is sized in `sp`, so a user at 200% font scale gets 100%. For a published calendar
library that is a WCAG 1.4.4 failure, and it is invisible: `README.md:323-325` presents it as a
feature (*"the `dayCellSize` you pass is the size you get on every device"*), and
`@Preview(fontScale = 1.5f) HijriCalendarBothDatesFontScale150Preview` is named as though it
demonstrates 150% support when it exists to show the scale is ignored. The fixed-`Dp`-cell decision
is defensible; making it silently for every consumer, and selling it, is not.

### 5 — `minDate`/`maxDate` beyond ±500 months crashes the calendar

`HijriCalendarGrid.kt:62` calls the two-argument
`PAGER_CENTER_PAGE.coerceIn(minAllowedPage, maxAllowedPage)`, and both bounds are derived from
caller-supplied dates against a **fixed** ±500-month pager window. `coerceIn(min, max)` throws when
`min > max`, so any range that inverts or falls outside the window throws **while composing**.

Verified against the module's real `monthOffset`: the threshold is exact — `minDate` 1489-05 is 500
months from 1447-09 and renders; 1489-06 is 501 and throws `Cannot coerce value to an empty range:
maximum 1000 is less than minimum 1001`. It also throws for `minDate` after `maxDate`, and for a
`maxDate` 100 years in the past. A consumer mirroring `PakistanHijriCalendar`'s own documented
1400–1500 range is past the limit end to end, and nothing in the KDoc or README states a navigation
horizon.

`pageCount = { PAGER_PAGE_COUNT }` (`:63`) is why the window is enforced by yanking the page back
after the fact (`:85-88`) rather than simply having fewer pages. `drop(1)` at `:79-81` is a third,
quieter defect in the same block — see [UI-09](#9--pager-effect-synchronization).

This is the one finding a consumer hits **without opting in**: no unusual configuration, just a
date range.

### 6 — Public surface and documentation

Ten of twelve `commonMain` files have **zero** KDoc blocks, including all four public composables
and `util/ModifierExtensions.kt`. `README.md` never mentions `HijriCalendarGrid`, `HijriWeekRow`,
`HijriCalendarHeader`, `HijriCalendarDayCell`, `calendarDayCell`, or `clickableIfEnabled` — their
only documentation is the ABI dump. `HijriCalendarColors` has 14 undocumented fields, which is the
same criticism `INDEX.md` made of `CalendarDay` one module over. One signature is
self-contradictory: `HijriCalendarGrid` demands a state *and* its resolved month, then discards the
month, so a stale `calendarMonth` gives a correctly-typed call that renders the wrong month.

### 7 — Previews in the published ABI

14 public `@Preview` composables plus 21 `ComposableSingletons$PreviewsKt` lambda holders appear in
both `calendar-ui.klib.api` and `calendar-ui.api`. `Previews.kt` (317 lines) is `commonMain`, so it
is compiled for iOS and embedded in both K/N frameworks and the Xcode app. `ui-tooling-preview` is
declared `implementation` (`build.gradle.kts:13`) — a non-`api` dependency whose annotation now sits
on public signatures.

### 8 — No touch feedback

`ModifierExtensions.kt:25` passes `indication = null`. All 42 day cells have **no ripple, no press
state, no hover**, while still allocating a `MutableInteractionSource` to feed the null. The same
file exports two `public` modifier helpers that are in the ABI with no KDoc.

### 9 — Pager effect synchronization

`HijriCalendarGrid.kt:68-74` animates the pager when `calendarMonth` changes, while `:79-94` pushes
the pager back into the state when the user settles on a page. The two are not idempotent, and
`initialMonth` is frozen by `remember { }` (`:46`), so after a configuration change the restored
page is interpreted against the wrong month. `drop(1)` is the load-bearing hack that papers over
it.

### 10 — Localization is started, not finished

`HijriCalendarLabels` is a real seam and `CalendarNames` is shared with the widget data — good.
But the header line is concatenated in the component (`HijriCalendarHeader.kt:69`), so the year
renders in Western digits even in the module's own Urdu preview; `"Previous month"` /
`"Next month"` are hardcoded in two public places (`HijriCalendarHeader.kt:33-34` and
`HijriCalendarLabels.kt:29-30`); and the Gregorian range label's separator and ordering are fixed in
`HijriCalendar.kt:153-163`. `HijriCalendarLabels`'s KDoc claims *"All header and weekday text can be
localized via `labels`"*, which is currently false for the year, the header line and the a11y
strings.

### 11 — Stability annotation lies

`HijriCalendarLabels` is `@Immutable` but holds four `Function` fields, which the Compose compiler
rates unstable; the annotation overrides inference in the wrong direction. Verified in bytecode.
`HijriCalendarColors` is stable *by inference* and correctly carries no annotation — the asymmetry
is the tell. Consequences are the two classic ones: a label object rebuilt inline never compares
equal, and a lambda capturing a snapshot state compares equal while going stale.

### 12 — Long composables

`HijriCalendarDayCell` is 177 lines with six sequential `when` blocks resolving colour and geometry
before a single node is emitted, and computes both date strings unconditionally even in
`GREGORIAN_ONLY` mode. `HijriWeekRow` throws from composition via `require` on a caller-supplied
list. detekt already baselines all of it.

### 13 — Dependency hygiene

`material.icons.extended` is declared for two `AutoMirrored` arrows that live in
`material-icons-core`, which arrives with `compose.material3` anyway — and it is pinned to
**1.7.3** while the rest of Compose resolves from `1.12.0`. Five minors, one release train, in a
published artifact.

### 14 — The detekt baseline has drifted

`config/detekt/baseline-calendar-ui.xml:23` still lists `NoUnusedImports:HijriCalendarLabels.kt`,
but `CalendarDay` is used there at `:36`. Four real findings are baselined (`LongMethod` and
`CyclomaticComplexMethod` on the day cell, `LongMethod` on the grid, `LongParameterList`), which is
how they became permanent.

---

## Why nothing caught this

**The only Compose test never runs.** `CalendarScreenDeviceUiTest` is the single test in the repo
that composes anything. CI runs `:sample-android-app:assembleDebug` (`.github/workflows/build.yml:56`)
— compilation, not `connectedDebugAndroidTest`. It has never caught anything because it has never
executed.

**Both samples drive the process global.** `HijriMonthOverrides.current` *is*
`HijriCalendarState.monthLengths` when the state is built with defaults, so the omission in finding 1
is invisible in every demo. The bug requires a **scoped** table — the feature
[CORE-02](CORE-02-scope-month-overrides.md) added — and nothing in this module ever constructs one.

**`apiCheck` reported the ABI problem, not the behavioural ones.** It surfaced finding 7
(`PreviewsKt` in both dumps) because signature changes are mechanical. It is signature-only, so it
could never see finding 1.

**detekt's baseline absorbed the complexity.** 20 entries, mostly cosmetic, but four of them are
`LongMethod`/`CyclomaticComplexMethod`/`LongParameterList` on exactly the code that is hardest to
test — so CI is green on the code most worth reviewing.

**The comments point the other way.** `HijriCalendar.kt:67` explains *why* font scale is pinned and
`HijriCalendarGrid.kt:77-78` explains *why* `drop(1)` is there, in terms that make each look
like a considered trade-off. Both are correct explanations of a decision that is still wrong. A
reviewer skimming for `TODO`/`FIXME` finds nothing; the module has neither.

---

## KDoc audit

`rg -c '/\*\*'` per file, `calendar-ui/src/commonMain`:

| File | Lines | KDoc blocks |
|---|---|---|
| `DateDisplayMode.kt` | 23 | 4 |
| `HijriCalendarLabels.kt` | 50 | 5 |
| `HijriCalendar.kt` | 160 | **0** |
| `HijriCalendarGrid.kt` | 205 | **0** |
| `HijriCalendarHeader.kt` | 95 | **0** |
| `HijriCalendarDayCell.kt` | 177 | **0** |
| `HijriWeekRow.kt` | 44 | **0** |
| `HijriCalendarColors.kt` | 23 | **0** (14 fields) |
| `HijriCalendarDefaults.kt` | 51 | **0** |
| `util/ModifierExtensions.kt` | 32 | **0** (2 public funcs) |
| `preview/Previews.kt` | 317 | **0** (should not be published — [UI-07](#7--previews-in-the-published-abi)) |
| `preview/PreviewData.kt` | 20 | **0** |
| `PageWindow.kt` | 102 | 8 | *(added by UI-05)* |
| `RenderMonth.kt` | 63 | 3 | *(added by UI-01)*

**10 of 14 files have no KDoc at all**, and the two that do are the two that needed it least —
`DateDisplayMode` and `HijriCalendarLabels` are both small, self-describing data types. Not one
public composable explains a parameter, a default, or a precondition.

---

## Relationship to the core review

| `CORE-` ticket | Lands on this module as |
|---|---|
| [CORE-01](CORE-01-mode-aware-month-range.md) | **Resolved here.** The three-way branch is gone; the module no longer imports `HijriMonthOverrides` or `ObservedHijriCalendar`. Closed. |
| [CORE-02](CORE-02-scope-month-overrides.md) | **Finding 1.** The scoped table `HijriCalendarState.monthLengths` is unreachable from the render path. |
| [CORE-03](CORE-03-api-stability.md) | **Working as intended** — and it is how finding 7 was noticed, since `PreviewsKt` appears in both dumps. |
| [CORE-06](CORE-06-core-purity.md) | `DateDisplayMode`'s KDoc is exactly the enum documentation the ticket asked for. |
| [CORE-07](CORE-07-static-analysis.md) | **Finding 14.** The baseline mechanism shipped; its upkeep did not. |

The boundary is the story: `CORE-02` built the feature correctly, `CORE-01` kept the UI honest about
it, and then the UI silently stopped reading it. Every layer above is correct and the composition is
wrong.

---

## Verification for every ticket

Reproduce the shared setup:

```bash
./gradlew :calendar-core:desktopTest :calendar-ui:desktopTest \
          :calendar-widget-data:desktopTest
```

`detekt`, `apiCheck` and the doc-version script are the other two gates; `apiCheck` needs a macOS
host. Run `./gradlew apiDump` **only** when a ticket deliberately changes the ABI — finding 1
and finding 7 do, and both must record the break in `CHANGELOG.md`.

Tickets 3, 4, 5 and 9 change `commonMain` composition logic and touch the K/N frameworks, so they
must also pass the existing macOS CI job (`apiCheck`, iOS arm64 + simulator compile, and the
unsigned `xcodebuild`).

---

## Ticket index

Architecture tickets for this module are prefixed `UI-`. The `CORE-` prefix covers `calendar-core`;
the `HijriCalendar-Issue{N}-*` files are pre-existing consumer-bug reports and are unrelated to both.

| File | Title | Blocks | Blocked by |
|---|---|---|---|
| `UI-01-scoped-overrides-ignored.md` | Pass `overrides` through every render path | 6 | — |
| `UI-02-compose-test-harness.md` | Composable tests + the device tests in CI | 4, 9 | — |
| `UI-03-one-month-builder.md` | One builder for the rendered month | 6 | 1 |
| `UI-04-accessible-text-scaling.md` | Restore text scaling to the calendar subtree | 6 | 2 |
| `UI-05-pager-window-crash.md` | Derive a pager window that cannot invert or crash | 6 | — |
| `UI-06-public-surface-and-docs.md` | Narrow the surface; KDoc + README | — | 1, 3, 4, 5, 7, 9 |
| `UI-07-previews-out-of-abi.md` | Previews out of the published artifact | 6 | — |
| `UI-08-touch-feedback.md` | Restore press feedback; un-`public` the modifiers | — | — |
| `UI-09-pager-effect-sync.md` | Synchronise the two pager effects; fix restore | 6 | 2, 5 |
| `UI-10-localization-completion.md` | Localizable header text and a11y strings | — | — |
| `UI-11-stability-honesty.md` | Fix the unsound `@Immutable` | — | — |
| `UI-12-day-cell-decomposition.md` | Split the day cell's colour resolution | — | — |
| `UI-13-dependency-hygiene.md` | Drop `material-icons-extended`; align versions | — | — |
| `UI-14-stale-detekt-baseline.md` | Regenerate `baseline-calendar-ui.xml` | — | 12 |
