# `calendar-core` Architecture Review

**Date:** 2026-09-30
**Scope:** `calendar-core/src/commonMain` (1,606 lines) and its test suite (1,700 lines)
**Status:** Complete — 7 of 7 tickets shipped
**Supersedes:** nothing. See "Relationship to the prior review" below.

> This is an **architecture** review of the core module. It is a different document from
> `docs/PRINCIPAL-REVIEW-2026-09-15.md`, which triaged consumer-reported bugs and closed its
> P0/P1 findings. That review found no architectural defects; this one does. Read both.
>
> **Companion:** [`UI-INDEX.md`](UI-INDEX.md) reviews `calendar-ui`. Four of its eight findings are
> the same *class* of defect this document found one layer down, and two are direct consequences of
> tickets that have shipped here.

---

## Verdict

**7 / 10.** Solid, unusually disciplined engineering with one genuine architectural debt and
a process gap that will bite at 1.0. The two worst findings — duplicated month-range policy and
the process-global override singleton — are now fixed; the rest is process and API surface.

| Dimension | Score | Note |
|---|---|---|
| Concurrency | 9/10 | Lock-free CAS + immutable snapshots, zero `synchronized` in commonMain. Exemplary. |
| Algorithmic craft | 9/10 | Two real complexity attacks, both with regression tests naming the bug they prevent. |
| Documentation | 8/10 | Data provenance documented, including admitting an estimate is an estimate. |
| Test discipline | 8/10 | Test:main > 1:1; knows what roundtrip tests *cannot* catch. |
| Module boundaries | 8/10 | **Was 6/10** — `CalendarMonth` is now correct in all three spaces (Issue 1). |
| Encapsulation | 8/10 | **Was 5/10** — `HijriMonthLengths` is instantiable; the global is a compatibility default (Issue 2). |
| API stability | 4/10 | No `explicitApi()`, no binary-compat validator, on a published artifact. |
| Process/tooling | 5/10 | No static analysis; `CONTRIBUTING.md` already drifted. |

---

## Two framing questions, answered first

These came up while reading the module and shaped every finding below.

### "Is state in `calendar-core` normal?"

Partly — and the key is what *"core"* means here. `calendar-core` is **not** a framework-free
domain module: `CalendarDay.kt:3` imports `androidx.compose.runtime.Immutable`, and
`HijriCalendarState` uses `mutableStateOf` / `derivedStateOf` / `rememberSaveable` / `Saver`.
It is a *compose-runtime-level* core.

That is a legitimate and common split. Material3's `DatePickerState`, `TextFieldState`, and
Compose Multiplatform's own `material3-adaptive` `CalendarState` all live in the component's
own artifact, not the app layer. A component library that forced every consumer to own a
ViewModel just to render a month grid would be the unusual choice. So a **state holder** in
this module is fine.

What was not fine is a specific third thing that also lived here: the **process-global
`HijriMonthOverrides` singleton**. That is now `HijriMonthLengths`, an instantiable table, with
the global reduced to a delegating default. See Issue 2.

### "Why three date types?"

Because they are not three representations of one chain. They are **three independent
authorities that can disagree about the same Gregorian day**.

| Type | Authority | Why it cannot be the other one |
|---|---|---|
| `HijrahDate` (3rd-party) | Umm al-Qura *arithmetic* | Closed type. `HijrahDate(1448, 3, 30)` is only constructible when `HijrahYearMonth(1448,3).numberOfDays == 30`. No month-start table. Anchors month identity (`HijrahYearMonth` for headers/nav) and `minDate`/`maxDate`. |
| `PakistanHijriDate` | Ruet-e-Hilal **committee decisions** (`FIXES`) | Not computable. Needs its own century table (`MonthTable`, 1400–1500) with per-month lengths and absolute epoch-day anchors. Asking `HijrahDate` for 1440-10-01 returns the Saudi answer — silently and wrongly. |
| `ObservedHijriDate` | Umm al-Qura **+ user correction** | Can produce a 30th in a month UAQ calculates as 29 days. `HijrahDate` cannot represent that, and has nowhere to put the extra field. |

`ObservedHijriDate` is the load-bearing case. Its fourth field, `monthLength`
(`ObservedHijriDate.kt:19`), is the *effective* length after overrides — the whole reason the
type exists. It is also why the saver must persist a fourth int (`HijriCalendarState.kt:432-439`):
after process death, `1448-03-30` is ambiguous without it. That asymmetry with the other two
types is a correctness requirement, not sloppiness.

**The invariant holding all three together:** the Gregorian day is the only real anchor; each
cell stores one calendar's *interpretation* of that day. Hence `CalendarDay.localDate` resolves
through a fallback chain and subtracts `adjustmentDays` (`:51-61`), and `dayOfMonth` tries
observed → hijrah → pakistan (`:33`).

**The cost:** `CalendarDay` is a tagged union with three nullable slots and no discriminator, so
"which slot is populated" is a mode-dependent invariant every consumer must know. The
symptoms are the defensive fallbacks — `dayOfMonth ?: 0` and `localDate` returning *today's*
date for a placeholder cell (`:57`) — which exist only because "no date at all" is
representable, needed solely for the disabled placeholders in Pakistan mode.

---

## What is genuinely excellent

Preserve these in any refactor. Each is a deliberate, documented decision.

**The concurrency work is the standout.** `HijriMonthOverrides` (`:26-95`) uses
`AtomicReference<Map>` + `AtomicLong` revision with CAS loops, and skips the revision bump on
a no-op write. `PakistanHijriCalendar` (`:104-134`) bundles starts + lengths into one immutable
`MonthTable` CAS-published per revision, so readers never lock. This is materially better than
most production Kotlin.

**Complexity was attacked, not merely observed.** Two concrete cases:
- The precomputed century table. `PakistanCalendarPerformanceTest.kt:11-13` documents the old
  chain as "minutes/hung" and asserts a full-range scan under 5s.
- `observedDateAt`'s drift-bounded binary search (`ObservedHijriCalendar.kt:57-95`) replaced a
  walk capped by a magic `iterations < 24`. The replacement derives its search window from a
  *proof* (each override shifts ≤1 day and month starts are monotonic ⇒ the containing month is
  within ±drift/29 + 1) rather than guessing a larger constant.

**`PakistanCalendarMonthTest` exists because the author knew the limits of roundtrip testing.**
An off-by-one in `monthStart`'s forward chain shifts every later month ~30 days and is invisible
to roundtrip tests, because both directions share the bug. Absolute Gregorian anchors live in
their own file for exactly that reason.

**`WidgetOptions` is the house documentation standard.** Selective KDoc on non-obvious fields
only, with the reasoning inline. Use it as the benchmark when fixing the gaps in
[the audit](#kdoc-audit-data-class-fields).

---

## The 7 findings

Full tickets live in this directory. Summary and blocking edges:

```
        ┌─────────────────────────────┐
        │ 4. Logger seam (small)      │──┐
        └─────────────────────────────┘  │
                                        ▼
        ┌─────────────────────────────┐  ┌──────────────────────────┐
        │ 1. Mode-aware month range   │──▶ 2. Scoped overrides      │
        │    (no blockers — first)    │  └────────────┬─────────────┘
        └─────────────────────────────┘               │
                                                     ▼
        ┌─────────────────────────────┐  ┌──────────────────────────┐
        │ 7. detekt + docs sync       │  │ 5. Exception semantics   │
        │    (no blockers)            │  └────────────┬─────────────┘
        └─────────────────────────────┘               │
                                                     ▼
        ┌─────────────────────────────┐  ┌──────────────────────────┐
        │ 6. Core purity              │─▶│ 3. API stability         │
        └─────────────────────────────┘  │  (last — locks the API)   │
                                          └──────────────────────────┘
```

**Execute in this order:** 4, 1, 2, 5, 6, 3, 7.

4 and 1 have no blockers and 4 is small — do it as a warm-up. **2 must precede 5**: both touch
the `PakistanHijriCalendar` surface, and doing 5 first means reworking it twice. **3 is last**
because `explicitApi()` freezes whatever the others decide.

| # | Finding | Severity | Status | Ticket |
|---|---|---|---|---|
| 1 | `CalendarMonth` silently wrong in 2 of 3 modes; correct derivation duplicated 3× | High | **Done** | `CORE-01-mode-aware-month-range.md` |
| 2 | Process-global overrides: no scoping, no persistence, forces test cleanup and a revision mirror | High | **Done** | `CORE-02-scope-month-overrides.md` |
| 3 | No `explicitApi()` / binary-compat gate on a published artifact with a positional saver | Critical | **Done** | `CORE-03-api-stability.md` |
| 4 | `println` in a shipped library; diagnostics unreachable to consumers | Medium | **Done** | `CORE-04-logger-seam.md` |
| 5 | `gregorianToHijri` conflates "out of range" with "internal fault"; 11 swallowed `catch` sites | Medium | **Done** | `CORE-05-exception-semantics.md` |
| 6 | `DateDisplayMode` (presentation) in core; duplicated English name lists; `minDate`/`maxDate` ignored outside UAQ mode | High | **Done** | `CORE-06-core-purity.md` |
| 7 | No static analysis; `CONTRIBUTING.md` already drifted | Medium | **Done** | `CORE-07-static-analysis.md` |

### 1 — `CalendarMonth` is silently wrong in two of three modes

`CalendarMonth.gregorianFirstDay` / `gregorianLastDay` (`CalendarMonth.kt:27-29`) unconditionally
compute the plain Umm al-Qura answer from `yearMonth.firstDay`. In Pakistan or observed mode
that is not the month's real extent.

Because the correct derivation was never available, it is hand-rolled in **three** places:

- `calendar-core/.../CalendarMonth.kt:27-29` — plain UAQ only, i.e. the wrong one
- `calendar-ui/.../HijriCalendar.kt:52-78` — full 3-mode branch, with fully-qualified
  `com.muazdev.hijricalendar.core.HijriMonthOverrides` calls inline
- `calendar-widget-data/.../WidgetDataApi.kt:174-198` — full 3-mode branch, independently written

Copies 2 and 3 agree today, but only by coincidence: neither consults the other, so nothing stops
them diverging. Plus the dead `CalendarMonth.gregorianMonthRange` (`:32-48`), deprecated with a
message about *localization* when the actual problem is *wrongness*.

(`sample-shared/.../CalendarScreen.kt:300-320` resembles a fourth copy at a glance, but it is the
month-length override row — it reports *lengths*, not a Gregorian extent. It still needs updating
when Fix 2 changes the overrides API, but it is not part of this duplication.)

AGENTS.md already enforces the opposite rule
for the widget module — "must both build data through the top-level helpers … so they cannot
drift" — it is just not applied here. **This is the first thing to fix:** it is the only place
the library can hand a consumer the wrong month, and it is a prerequisite for everything else,
because while the branch is triplicated every future mode is a three-site edit with no compiler
help.

### 2 — The global singleton has no escape hatch

`HijriMonthOverrides` is process-wide, not persisted, not scoped. Consequences already visible:

- `MonthLengthOverridesTest` needs `@AfterTest { clearAll() }` in five places, fragile if a test
  fails mid-body.
- Because the singleton is not Compose state, the grid would never recompute, so the state
  holder hand-rolls a revision mirror (`HijriCalendarState.kt:57`) read inside `derivedStateOf`
  (`:113`). That bridge is the tell: the complaint is not "state in core", it is "state in core
  that the consumer does not own".
- A consumer cannot render two calendars with different override sets, cannot render a preview
  with overrides, and cannot restore them without reaching for a global.

The precedence *rule* (`user > FIXES > UAQ`) belongs in core. The *storage* should not.

### 3 — No API stability gate for a published artifact

No `explicitApi()`, no `binary-compatibility-validator`, no checked-in `.api` dump. Meanwhile
`calendar-core` `api`-exports a third-party type (`hijrah-datetime`), and
`hijriCalendarStateSaver` encodes a positional `List<Int>` coupled to the public constructor
(`HijriCalendarState.kt:414-453`).

A signature change or enum reordering breaks every consumer's `rememberSaveable` restore
*silently* — the state comes back wrong rather than failing to compile. That is the class of bug
that costs a release. Rated critical because it is the cheapest fix with the highest blast radius.

### 4 — `println` in a shipped library

`TodayHijriDate.kt:26` and `PakistanHijriCalendar.kt:252`. In `commonMain` you cannot reach
`android.util.Log`, which is exactly why the ecosystem norm is a tiny `expect fun` logger
(coil3, ktor, and most database builders all do this). Today a consumer can only get these
diagnostics by redirecting stdout.

### 5 — Exception-as-control-flow hides bugs

Eleven `catch (_: Exception)` sites in `commonMain`. Worst case:
`PakistanHijriCalendar.gregorianToHijri` (`:231-243`) wraps its entire month walk and returns
`null`, so an internal arithmetic fault is indistinguishable from "outside the supported
window". The `?:` cascade in `CalendarDay` then papers over it downstream.

### 6 — Layering drift on small things

- `DateDisplayMode` (7 lines) lives in core but is consumed only by `calendar-ui` and the
  samples. It is presentation.
- `UrduCalendarNames` is localization data in core while `calendar-ui/HijriCalendarLabels` does
  the same job. Two homes for one concern.
- `minDate` / `maxDate` are typed `HijrahDate` (UAQ) even in Pakistan mode, so range clamping is
  evaluated in the wrong date space in 2 of 3 modes.
- `HijriCalendarState.kt:318` has a dead conjunct: `_overridesRevision >= 0` is always true,
  a leftover from a removed branch.

### 7 — Process

No detekt, ktlint, or spotless anywhere in the catalog. `CONTRIBUTING.md` has already drifted the
way `AGENTS.md` did: it claims JDK 11 + Android Studio Ladybug, lists 5 modules when there are 8,
and never mentions `calendar-widget-data` or `calendar-widget-glance`. That was P0 #2 in the
prior review; the class recurred in a different file. The fix is a docs-freshness check, not
another manual sync.

---

## KDoc audit: data-class fields

Read every file in `commonMain`. Coverage is measured in KDoc openers per file:

| File | KDoc blocks | Lines | Verdict |
|---|---|---|---|
| `HijriCalendarState.kt` | 18 | 514 | Good. `@param` tags on the constructor explain adjusted space. |
| `PakistanHijriCalendar.kt` | 13 | 325 | Good. Documents data provenance, admits 1448-04 is an estimate. |
| `HijriMonthOverrides.kt` | 8 | 95 | Good. |
| `ObservedHijriCalendar.kt` | 7 | 122 | Good. |
| `UrduCalendarNames.kt` | 5 | 40 | Good. |
| `CalendarMonthExt.kt` | 3 | 248 | Adequate — the three variants each get a block. |
| `CalendarDay.kt` | 3 | 62 | **Poor.** All 3 are on computed properties. |
| `ObservedHijriDate.kt` | 2 | 30 | Partial. |
| `TodayHijriDate.kt` | 1 | 28 | Good. |
| `WeekDay.kt` | 1 | 36 | **Poor.** |
| `CalendarMonth.kt` | 0 | 54 | **None.** |
| `DateDisplayMode.kt` | 0 | 7 | **None.** |

### Specific gaps

**`CalendarDay` — 9 constructor fields, zero KDoc.** The critical undocumented invariant is
*which of the three nullable slots is populated in which mode*, currently discoverable only by
reading `CalendarMonthExt`. This is the module's worst offender and the type consumers touch
most.

**`CalendarMonth` — zero KDoc in the entire file**, and it holds the Issue-1 correctness bug.

**`WeekDay` — and here is a latent bug, not just a gap.** `index = ordinal` (`WeekDay.kt:24`)
has no warning that it is a **persisted wire format**. `firstDayOfWeekIndex` is stored in
`WidgetOptions` JSON (`WidgetOptions.kt:26`) *and* in Glance `SharedPreferences`
(`HijriWidgetConfig.kt:252-253`), defaulted from `WeekDay.DEFAULT_FIRST_DAY.index`, and only
guarded by `coerceIn(0, 6)` — which catches an out-of-range value but **cannot** detect that
ordinal 3 now means a different day.

Reordering the `WeekDay` enum would silently reinterpret the first day of week on every already-
placed widget. That is precisely the hazard `WidgetOptionsJson` was built to avoid ("enums by
name, never by ordinal — reordering an enum entry must not reinterpret a stored widget",
AGENTS.md) — violated one layer down. The `coerceIn` is doing a job it cannot do. Fix folded
into Issue 6.

**`ObservedHijriDate`** — good class KDoc, but never states that `month` is 1-indexed, or *why*
`monthLength` exists. The "why" is the reason the saver persists a fourth int; a reader who does
not know that will "simplify" the saver.

**`PakistanHijriDate`** — no `MIN_YEAR..MAX_YEAR` range note on the fields; the `require` in
`hijriToGregorian` is the only guard, and it is far from the type.

**`HijriCalendarStateConfig`** (internal) — undocumented, yet it is the `remember` key for the
saver. A missed field yields a stale saver and a silently wrong restore.

### Benchmark

`WidgetOptions` is the standard: selective KDoc, only on fields where the *reason* is not
obvious from the type, with the reasoning inline. Do not document what the type already says.
`val dayOfMonth: Int` needs nothing; `val monthLength: Int` needs everything.

---

## Relationship to the prior review

`docs/PRINCIPAL-REVIEW-2026-09-15.md` triaged three consumer-reported bugs plus version drift,
and closed its P0/P1 findings (#1–#5) in commit `cbd0bf8`. That review is accurate and its
fixes are all present in the code. It made no architectural claims, and nothing here contradicts
it.

One overlap worth noting: that review's P0 #2 was "AGENTS.md version drift". Issue 7 covers the
same *class* of failure and the general fix (a freshness check), since the drift has since
recurred in `CONTRIBUTING.md`.

---

## Ticket index

Architecture tickets are prefixed `CORE-`; the pre-existing consumer-bug reports in this
directory use `HijriCalendar-Issue{N}-*` and are unrelated. `calendar-ui` uses `UI-`, so three
numbering schemes share this directory and the prefix is what disambiguates them.

| File | Title | Blocks | Blocked by |
|---|---|---|---|
| `CORE-04-logger-seam.md` | Pluggable logger instead of `println` | 5 | — |
| `CORE-01-mode-aware-month-range.md` | One source for the mode-aware month range | 3, 6 | — |
| `CORE-02-scope-month-overrides.md` | Scoped overrides instead of a process global | 5 | 1 |
| `CORE-05-exception-semantics.md` | Distinguish "out of range" from "internal fault" | 3 | 2, 4 |
| `CORE-06-core-purity.md` | Move presentation/i18n out; re-type `minDate`/`maxDate` | 3 | 1 |
| `CORE-03-api-stability.md` | `explicitApi()` + binary-compatibility validator | — | 1, 5, 6 |
| `CORE-07-static-analysis.md` | detekt with a generated baseline; docs sync | — | — |

## Verification for every ticket

```bash
./gradlew :calendar-core:desktopTest :calendar-ui:desktopTest \
          :calendar-widget-data:desktopTest \
          :calendar-widget-glance:testDebugUnitTest
./gradlew :sample-android-app:assembleDebug
```

Issues 2, 3 and 6 change the KMP surface, so also run the existing macOS CI job (iOS arm64 +
simulator compile, then the unsigned `xcodebuild`) — `CalendarMonth` crosses into Swift, and
Issue 6 moves types across a module boundary.
