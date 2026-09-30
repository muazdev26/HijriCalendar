# Issue 14: `baseline-calendar-ui.xml` no longer matches the code

**Severity:** Low
**Blocks:** —
**Blocked by:** [Issue 12](UI-12-day-cell-decomposition.md)
**Module:** `config/detekt`

---

## Problem

`config/detekt/baseline-calendar-ui.xml:23` lists:

```xml
<ID>NoUnusedImports:HijriCalendarLabels.kt$com.muazdev.hijricalendar.ui.HijriCalendarLabels.kt</ID>
```

But `CalendarDay` **is** used in that file — at `HijriCalendarLabels.kt:36`:

```kotlin
val dayContentDescription: (CalendarDay) -> String = { day -> … }
```

The baseline has not been regenerated since that file was last edited, so the entry is stale. A
detekt baseline is supposed to be a truthful record of existing debt; a stale entry means the module
looks worse than it is and the record can no longer be trusted to distinguish "fixed" from
"never ran".

## The rest of the baseline

The remaining 25 entries are almost entirely cosmetic — import ordering (8), trailing newline (6,
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

All four are [UI-11](UI-12-day-cell-decomposition.md) (three) and
[UI-03](UI-03-one-month-builder.md) / [UI-14](UI-06-public-surface-and-docs.md) (one). They are
baseline entries, which means CI will never flag them and nothing schedules them. Leaving them
baselined is how a 155-line composable and a 217-line composable with two divergent month builders
became the module's defining shape.

## Proposed change

**1. Regenerate the baseline** after
[UI-11](UI-12-day-cell-decomposition.md) lands, so the stale `NoUnusedImports` entry disappears.
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

- [ ] `rg "NoUnusedImports" config/detekt/baseline-calendar-ui.xml` returns nothing
- [ ] No baseline entry corresponds to an open `UI-` ticket
- [ ] A comment or doc names any deliberately retained baseline entry

## Notes

- **Do not regenerate before fixing.** Running `detektBaseline` first would re-add
  `NoUnusedImports` (the entry matches the file, and detekt reports per-file, not per-symbol) and
  re-add the four `Long*` entries. Sequence is: fix, then regenerate, then diff.
- `CORE-07` shipped the baseline mechanism. This ticket is about its upkeep, which the mechanism
  does not cover — a baseline is a snapshot with no expiry.
- `baseline-calendar-core.xml` is worth the same check; `calendar-core`'s tests were heavily edited
  by the `CORE-` tickets and its baseline may have drifted too. Not this ticket's scope, but the
  same ten minutes.