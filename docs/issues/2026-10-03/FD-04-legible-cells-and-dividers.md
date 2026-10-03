# FD-04: Grid cell text is unreadably small, and the cells have no dividers

**Issue:** #14
**Severity:** Medium
**Blocks:** —
**Blocked by:** #8
**Module:** `calendar-widget-glance`, `calendar-ui`, `iosApp`
**Status:** Ready

---

## Problem

The widget grid cell is the densest thing the library draws, and it is drawn at fixed type sizes:

```kotlin
// calendar-widget-glance/src/main/.../HijriCalendarWidget.kt:820-860 (DayCell)
Text(text = hijriText,    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, ...))
Text(text = gregorianText, style = TextStyle(fontSize = 8.sp, ...))
```

13sp for the Hijri day, **8sp for the Gregorian day underneath it**, inside a cell that is roughly
28dp wide. The report is that the date is hard to read: correct, and it is worse than it sounds. 8sp
is below the legibility floor for a secondary line — a user with any visual impairment reads nothing
there at all, and an 8sp glyph at `widget_primaryText` alpha 0.6 against a `#FBF7F1` background is
close to the minimum contrast ratio for 14dp UI text.

There is a second half to the complaint, and it is the more interesting one:

> there should be an option, along with the current approach, that every cell should have a border
> which clearly divides each section; this can be show/hide on settings

Nothing divides the cells. `MonthGrid` lays out rows with `chunked(7)` and weights, with no divider
between cells and none between columns. On a wall-clock widget at 40dp cells, an undivided grid of
42 numbers is genuinely hard to scan — you read across, you stop reading, you re-read.

## Why fixed sizes are the wrong answer

The cell has a *known* size at render time — `MonthGrid` receives `LocalSize` from Glance and already
uses it to pick the compact card vs the grid at `size.width < 180.dp`. A 260×280 grid cell has more
room than a 200×160 one, and the type is identical in both. That is the defect: **the type does not
respond to the space it was given**, so a user who resizes the widget larger gets more padding and
the same 8sp.

So this is not "bump 13 to 15". It is "derive the type from the cell", which is the same move
`HijriCalendar.kt:158-165` already makes for font scale (capping `Density.fontScale` so a 2× system
scale does not shred the grid). The rule:

- Hijri figure: from 13sp at a 28dp cell up to about 17sp at a 44dp cell.
- Gregorian sub-digit: from 9sp up to about 11sp — **never below 9sp**, whatever the cell size.
  A floor, not a ratio. This is the part that must not be made clever.

Floors matter because Glance `Text` clips rather than reflowing at these sizes, and a cell that has
run out of vertical room clips the *bottom* line first. Clipping the Gregorian digit yields `14` cut
to `1`, which is worse than showing nothing.

## The borders

A hairline between every cell, plus a slightly stronger one after each row, in a new colour resource
(`widget_cell_border`, `#1F000000` at 12% light / `#FFFFFF` at 12% night). Off by default, so
nothing changes for existing users — but unlike FD-02 there is no argument that it *should* be on,
because the grid stays the same shape either way and the divider is purely a legibility aid for
people who want it.

Two things this must not become:

- **Not a `border` on every `Box`.** That is 42 `GradientModifier`s per render and Glance's
  `RemoteViews` cost is per-view. Draw the dividers as the row and column separators, not as a border
  around each cell — same visual result, a fraction of the views.
- **Not baked into `MonthGrid`'s `padding`.** The cells are weighted to fill the width exactly; a
  divider has to live in the gap or it lands on top of a digit.

## Acceptance criteria

- [ ] Hijri cell text scales with the available cell height across at least three widget sizes, and
      the Gregorian sub-digit never renders below 9sp.
- [ ] At the smallest supported widget size no text is clipped, asserted by a test that checks the
      composed text has room for both lines.
- [ ] `showCellBorders: Boolean = false` on `WidgetOptions`, defaulting off.
- [ ] With borders on, every cell is visually separated; with them off, the grid is byte-identical to
      today's.
- [ ] Divider lines are drawn as row/column separators, not 42 per-cell borders.
- [ ] The in-app calendar honours the same option (it has its own `HijriCalendarColors`; add a
      `cellBorderColor` there rather than reusing the widget resource).
- [ ] The static Android 12–14 preview layout stays consistent with the default (borders off).
- [ ] The sample settings screen has a toggle.
- [ ] iOS: the grid draws matching separators when the option is on.
- [ ] `apiDump` regenerated; `CHANGELOG.md` records the addition.

## Shared rendering

The cell text-sizing rule is stated **once**. `MonthGrid` exists twice — `calendar-ui` (Compose) and
`calendar-widget-glance` (Glance) — plus a third in Swift, and they will drift if each derives its
own numbers. The sizes belong as a pure function taking a cell `Dp` and returning two sizes, in
`calendar-widget-data`, with a test per platform asserting all three agree. It is a few lines of
arithmetic, but it is exactly the kind of thing that silently diverges over two releases.