# Issue 04: Text scaling is switched off for the whole calendar subtree

**Severity:** High
**Blocks:** [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** [Issue 02](UI-02-compose-test-harness.md)
**Module:** `calendar-ui`
**Status:** Shipped — see "Shipped" below.

---

## Problem

```kotlin
// calendar-ui/.../HijriCalendar.kt:68-76
// Keep the entire calendar rendering at its designed size: ignore the system's
// font-scale so cells never inflate or shrink from accessibility text sizing.
val density = LocalDensity.current
val fixedDensity = remember(density.density) {
    Density(density = density.density, fontScale = 1f)
}
Column(modifier = modifier) {
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
```

Every `Text` inside — day cells, the week row, both header lines — is sized in `sp`, which resolves
through `LocalDensity.fontScale`. Forcing it to `1f` means a user at Android's largest font scale
sees the calendar at exactly 100%. There is no per-consumer opt-out: the override is unconditional
and wraps the whole subtree.

## Why it matters

Font scaling is not a preference, it is an accessibility requirement (WCAG 1.4.4; Android's
`fontScale` is a user-facing OS setting that apps are expected to honour). For a *published*
calendar library, overriding it silently on every consumer's behalf — including consumers whose
users need it — is the wrong default.

The library also **sells it**:

- `README.md:323-325`: *"System-level font scaling (accessibility `fontScale`) is intentionally
  ignored so cells never inflate or shrink the grid; the `dayCellSize` you pass is the size you get
  on every display mode and every device."*
- `Previews.kt:268` declares `@Preview(fontScale = 1.5f) fun HijriCalendarBothDatesFontScale150Preview`.

That preview is named as though it demonstrates 150% support. It exists to show the scale is
**ignored**. A future maintainer reading the name alone will conclude the opposite, and a reviewer
skimming the preview list will tick a box that is actually a defect.

## What is legitimate and must survive

The *layout* decision is sound: a month grid is a fixed-pitch matrix, and letting day text scale
unbounded would either clip the numerals or blow up the row pitch. The error is fixing this by
lying to the text rather than by bounding the text, and by doing it invisibly.

## Proposed change

**1. Fix the cell box, not the text.** `dayCellSize` (default 48.dp) already sizes the cell via
`Modifier.calendarDayCell(cellSize)`. Let `sp` inside scale; the box does not move. The existing
`maxLines = 1` + `softWrap = false` on every `Text` (`HijriCalendarDayCell.kt:115-116, 126-127,
140-141`) already handles overflow by clipping, which is what a fixed-pitch grid should do.

**2. Bound the growth so the grid cannot break.** Compute a cell-local `fontScale` from the
requested one, capped at a value the cell can render:

```kotlin
val cellDensity = remember(density.density, density.fontScale) {
    Density(density.density, fontScale = density.fontScale.coerceAtMost(MAX_CELL_FONT_SCALE))
}
```

`MAX_CELL_FONT_SCALE` is a design constant like `SingleLineCellSize` — state it in
`HijriCalendarDefaults` with the reason. Capping rather than clamping to `1f` gives accessibility
users a real range without handing them a broken grid.

**3. Leave the header unscaled or scaled, but decide deliberately.** The header text is
`titleMedium`/`bodySmall` in a `Row` with no fixed box, so scaling it is free. Do not include it in
the override; that alone restores the month name and year to readable sizes.

**4. Make it a knob.** Add `ignoreFontScale: Boolean = false` to `HijriCalendar` (and the
`HijriCalendarDefaults` constant for the cap), so a consumer who genuinely wants the exact-1f
behaviour keeps it. A default of `false` inverts the current behaviour — call that out in
`CHANGELOG.md`.

**5. Fix the preview's name and add a sibling.** Rename it to something honest about what it shows
(for example `HijriCalendarBothDatesAtFixedSizePreview`), and add a real
`@Preview(fontScale = 1.5f)` for the *default* path so the supported range is visible.

## Done when

- [x] A test at `fontScale = 1.5f` renders the default calendar and asserts day text is present
      and not clipped to nothing
- [x] No `CompositionLocalProvider` forces `fontScale = 1f` — the override is now a *cap*, and the
      opt-out is the only path that pins it to 1
- [x] `README.md` describes the cap and the opt-out, not "intentionally ignored"
- [x] The `fontScale = 1.5f` preview demonstrates supported behaviour, and is named for what it shows
- [x] `ignoreFontScale = true` reproduces today's output exactly, and is asserted

## Shipped

Text and geometry are now handled separately, which is the fix the original code needed and did not
have. `git show 337f631` explains why: before it, the **cell** was scaled by the system font scale
(`baseCellSize * max(1f, fontScale.coerceAtMost(1.5f))`), which grew every row and broke the layout.
That was a real bug and decoupling the cell from the font scale was the right call. The mistake was
going one step further and pinning `fontScale = 1f` for the whole subtree, so the **text** stopped
scaling too.

| | before | now |
|---|---|---|
| cell geometry | grew with font scale (broken) | fixed at `dayCellSize`, always |
| day text | pinned at 1f (WCAG 1.4.4) | scales, capped |
| header | pinned at 1f | honours the system setting, uncapped |

- **`HijriCalendarDefaults.maxFontScaleFor(dateDisplayMode)`** — 2.0x for the single-line modes, 1.5x
  for `BOTH`, which stacks two lines in a box sized for one. All three KDoc constants say *why*, with
  the arithmetic (`bodySmall` over `labelSmall` with no gap is ~31dp at scale 1, ~46dp at 1.5x).
- **`SingleLineCellSize`'s 48dp now states its constraint.** It was "chosen" for nothing in
  particular; it is sized to hold the `BOTH` stack at the cap, and that is what a future contributor
  needs to know before "fixing" it.
- **The header moved out of the override.** It is a `Row` with no fixed box, so scaling it is free.
  It is now composed as a sibling of the grid's `CompositionLocalProvider`, not inside it.
- **`ignoreFontScale: Boolean = false`** — the old flat behaviour, opt-in, on a parameter documented
  as an accessibility regression so a consumer choosing it has to have read why.

### `HijriCalendarFontScaleTest` (9 cases), verified in both directions

The invariant is *text scales, geometry does not*, and either half alone is satisfied by the old code:
"the cell did not grow" passes today, and "the text grew" is the behaviour that was missing. So both
are asserted, together and separately.

| Deliberately reintroduced | Goes red |
|---|---|
| flat `fontScale = 1f` for everything (the original defect) | `dayTextGrowsWithTheSystemFontScale`, `theDefaultIsToRespectTheFontScale`, `textScalesWithinAnUnchangingCell` |
| cell scaled by the font scale (the `337f631` bug) | `cellGeometryIsIdenticalAtEveryFontScale`, `textScalesWithinAnUnchangingCell` |

**One test bug worth recording**, because the failure looked like a passing product bug: the first
version measured the day figure with `onNodeWithText`, which returned **48px at 1.0 and 48px at 2.0**,
i.e. "the calendar is still forcing fontScale to 1". `HijriCalendarDayCell` sets
`mergeDescendants = true`, so the merged node is the fixed 48dp cell and the `Text` beneath it is
hidden. The test was measuring the box — which is constant by design — and would have "passed" for
the wrong reason. It now queries the unmerged tree, and the comment says so.

### The previews, which were the most misleading thing here

`HijriCalendarBothDatesFontScale150Preview` was annotated `fontScale = 1.5f` and rendered *identically*
to the 1.0 preview. A reviewer skimming the preview list ticked a box for a feature that did not
exist. It is now `HijriCalendarBothDatesAtLargeTextPreview` and actually shows larger figures in
unchanged cells, joined by `HijriCalendarBothDatesBeyondTextCapPreview` (2x, past the single-line cap)
and `HijriCalendarFixedTextPreview` (the opt-out).

## Notes

- **This is a behavioural change for every consumer.** Anyone relying on the calendar ignoring the
  system font scale will now see larger text. That is the point; `CHANGELOG.md` records it with a
  pointer to the opt-out.
- **Do not "fix" this by scaling the cell.** A grid that grows with the font scale reflows every row,
  changes the pager page height and moves every touch target — a much larger blast radius for a
  smaller win. Fixed geometry with scaled text is the correct decomposition, and it is what
  `337f631` was reaching for.
- **`337f631` should not be reverted.** Read it first; the cell-scaling half of it was a real fix and
  survives as the "geometry does not move" half of the invariant.
- **Not covered:** the exact rendered glyph size at a given scale is not asserted, only that the text
  grows and stops growing at the cap. That is enough to catch both regressions above without pinning
  Material 3's typography values, which are not this library's to freeze.



## Notes

- **Coordinate with the grid pitch.** If capping still lets `BOTH` mode's two lines overlap,
  `DateDisplayMode.BOTH` is the constraint — its `Column(spacedBy = 0.dp)` at
  `HijriCalendarDayCell.kt:130-133` has zero slack. A lower cap for `BOTH` than for
  `HIJRI_ONLY` is defensible; say which.
- The upstream commit `337f631` is titled *"Fix day cell sizing, font scaling, and today
  indicator"*, so the `fontScale = 1f` override was a deliberate response to a real layout bug. Do
  not revert it wholesale — find the bug it fixed (likely `BOTH` mode overflow) and keep the fix
  scoped to it. `git show 337f631` is the place to look.
- Related but separate: [UI-10](UI-10-localization-completion.md) notes the header year is never
  rendered in Arabic-Indic digits. Both are localisation gaps that a screen reader or a
  large-text user hits together.