# Issue 04: Text scaling is switched off for the whole calendar subtree

**Severity:** High
**Blocks:** [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** [Issue 02](UI-02-compose-test-harness.md)
**Module:** `calendar-ui`

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

- [ ] A test at `fontScale = 1.5f` renders the default calendar and asserts day text is present
      and not clipped to nothing
- [ ] No `CompositionLocalProvider` forces `fontScale = 1f`
- [ ] `README.md` describes the cap and the opt-out, not "intentionally ignored"
- [ ] The `fontScale = 1.5f` preview demonstrates supported behaviour
- [ ] `ignoreFontScale = true` reproduces today's output exactly (assert it, so the escape hatch is
      real)

## Notes

- **Coordinate with the grid pitch.** If capping still lets `BOTH` mode's two lines overlap,
  `DateDisplayMode.BOTH` is the constraint — its `Column(spacedBy = 0.dp)` at
  `HijriCalendarDayCell.kt:130-133` has zero slack. A lower cap for `BOTH` than for
  `HIJRI_ONLY` is defensible; say which.
- The upstream commit `337f631` is titled *"Fix day cell sizing, font scaling, and today
  indicator"*, so the `fontScale = 1f` override was a deliberate response to a real layout bug. Do
  not revert it wholesale — find the bug it fixed (likely `BOTH` mode overflow) and keep the fix
  scoped to it. `git show 337f631` is the place to look.
- Related but separate: [UI-09](UI-10-localization-completion.md) notes the header year is never
  rendered in Arabic-Indic digits. Both are localisation gaps that a screen reader or a
  large-text user hits together.