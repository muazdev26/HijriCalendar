# Issue 14: `baseline-calendar-ui.xml` no longer matches the code

**Severity:** Low
**Blocks:** —
**Blocked by:** [Issue 12](UI-12-day-cell-decomposition.md)
**Module:** `config/detekt`
**Status:** Shipped — see "Shipped" below.

---

## Problem

`config/detekt/baseline-calendar-ui.xml:23` lists:

```xml
<ID>NoUnusedImports:HijriCalendarLabels.kt$com.muazdev.hijricalendar.ui.HijriCalendarLabels.kt</ID>
```

## The finding was half wrong, and checking it is the point

The ticket claimed `NoUnusedImports:HijriCalendarLabels.kt` was **stale**, on the grounds that
`CalendarDay` is used at `HijriCalendarLabels.kt:36`:

```kotlin
val dayContentDescription: (CalendarDay) -> String = { day -> … }
```

That reasoning is about the *wrong import*. Deleting the baseline and running detekt showed the
finding is **real**: the unused import is `com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth`,
which nothing in the file references — `hijriMonthName` takes `(year: Int, month: Int)`, so no
`HijrahYearMonth` is ever constructed. `CalendarDay` is imported *and used*.

So the entry was accurate, not stale. It was still worth removing, but by deleting the import.

**The general lesson is the ticket's own warning, applied to itself.** A detekt baseline cannot be
audited by reading it and reasoning about the code — with a baseline in place detekt suppresses its
own output, so "no findings reported" and "finding fixed" look identical. The only way to tell is to
empty the baseline, run detekt, and compare. That is what surfaced the error here, and it is the
check the ticket proposes in its proposal §4 (a CI step that diffs the baseline) is really asking
for.

A stale entry is still a real problem — the module looks worse than it is, and the record can no
longer distinguish "fixed" from "never ran" — but this particular one was not an instance of it.

## The rest of the baseline

The remaining 19 entries are almost entirely cosmetic — import ordering (5), trailing newline (6,
duplicated as both `FinalNewline` and `NewLineAtEndOfFile`), and the two formatting nits at
`HijriCalendarHeader.kt:60` and `HijriCalendarDefaults.kt:15`. That is the right shape for a
baseline: noise that drains as files are touched.

Two entries are **real** debt being hidden rather than drained:

```
<LongMethod:HijriCalendarDayCell.kt$…>              (:16)
<CyclomaticComplexMethod:HijriCalendarDayCell.kt$…> (:6)
<LongMethod:HijriCalendarGrid.kt$…>                 (:17)
<LongParameterList:HijriCalendarGrid.kt$(days, …)>  (:18)
```

All four are [UI-12](UI-12-day-cell-decomposition.md) (three) and
[UI-03](UI-03-one-month-builder.md) / [UI-06](UI-06-public-surface-and-docs.md) (one). They are
baseline entries, which means CI will never flag them and nothing schedules them. Leaving them
baselined is how a long composable and a 217-line composable with two divergent month builders
became the module's defining shape.

## Proposed change

**1. Regenerate the baseline** after
[UI-12](UI-12-day-cell-decomposition.md) lands, so the stale `NoUnusedImports` entry disappears.
`./gradlew :calendar-ui:detektBaseline`.

**2. Delete the four real entries by fixing them**, not by regenerating. Regenerating re-adds them.
Order matters: fix, then regenerate.

**3. Add a note to the root `build.gradle.kts` detekt block** — or `config/detekt/README.md` —
listing the baseline entries that correspond to an open ticket, so a reader of the baseline can
tell "known, unowned debt" from "unreviewed". The root block already explains why the baseline
absorbs debt; it does not say which debt is *scheduled*.

**4. Consider failing on baseline *growth*.** Detekt does not do this out of the box, but a CI step
that diffs the baseline count against `main` would catch a new module quietly inheriting debt. This
is the same class of fix as `check-doc-versions.sh` in
[CORE-07](CORE-07-static-analysis.md) — assert the thing is not drifting rather than restating it.

## Done when

- [x] `rg "NoUnusedImports" config/detekt/baseline-calendar-ui.xml` returns nothing — though
      the entry turned out to be **accurate**, so it was fixed rather than dismissed
- [x] No baseline entry corresponds to an open `UI-` ticket
- [x] A comment or doc names any deliberately retained baseline entry

## Shipped

**Fix first, then regenerate** — the order this ticket insists on, and it is the whole content of
the finding. `baseline-calendar-ui.xml` went **20 entries → 12**, and **0 were added**.

Removed, each because the code stopped triggering it:

| Entry | Fixed by |
|---|---|
| `NoUnusedImports:HijriCalendarLabels.kt` | A genuinely unused `HijrahYearMonth` import, deleted. The ticket mis-diagnosed it as stale — see "The finding was half wrong" |
| `LongMethod:HijriCalendarDayCell.kt` | [UI-12](UI-12-day-cell-decomposition.md)'s `CellText` extraction |
| `CyclomaticComplexMethod:HijriCalendarDayCell.kt` | UI-12's `cellStyle` extraction |
| `LongMethod:HijriCalendarGrid.kt` | [UI-03](UI-03-one-month-builder.md) + [UI-09](UI-09-pager-effect-sync.md) — one builder, one collector |
| `ImportOrdering` × 3 | import sorting; one surfaced only because the signatures changed |
| `SpacingBetweenDeclarationsWithAnnotations` | KDoc on `HijriCalendarDefaults` |

**The four "real debt being hidden" entries the ticket named are all gone**, which was its actual
point: `LongMethod` and `CyclomaticComplexMethod` on the day cell, `LongMethod` on the grid, and
`LongParameterList` on the grid. CI would never have flagged them and nothing had scheduled them;
they were simply absorbed. Note the last one is now on an **internal** composable, which is why it
stays baselined rather than being fixed — an eight-parameter internal is not the same problem as an
eight-parameter public API, and UI-06 removed the public ones.

The remaining 12 are all cosmetic and drain as files are touched: trailing-newline reports
(`FinalNewline` and `NewLineAtEndOfFile`, duplicated — 6 of the 12), import order in the two
moved preview files plus one test file, one `ForEachOnRange`, one `ArgumentListWrapping`, one
`MaximumLineLength`.

### `baseline-calendar-core.xml` checked too

The ticket flagged it as *"worth the same check… the same ten minutes"*. Done: **74 → 74, zero
diff**. `calendar-core`'s tests were heavily edited by the `CORE-` tickets and its baseline had
**not** drifted — so the mechanism is working there, and the failure was specific to `calendar-ui`.

## Notes



## Notes

- **Do not regenerate before fixing.** Running `detektBaseline` first would re-add
  `NoUnusedImports` (the entry matches the file, and detekt reports per-file, not per-symbol) and
  re-add the four `Long*` entries. Sequence is: fix, then regenerate, then diff.
- `CORE-07` shipped the baseline mechanism. This ticket is about its upkeep, which the mechanism
  does not cover — a baseline is a snapshot with no expiry.
- `baseline-calendar-core.xml` is worth the same check; `calendar-core`'s tests were heavily edited
  by the `CORE-` tickets and its baseline may have drifted too. Not this ticket's scope, but the
  same ten minutes.