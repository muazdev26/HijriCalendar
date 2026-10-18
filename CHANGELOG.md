# Changelog

Notable changes to the published modules, newest first. The long-form write-ups live in `docs/`.

## 2.3.0 - 2026-10-18

### Added

- `WidgetLocalization.ChromeLabels.noEvent` — the grid footer's "No event" line in the widget's own
  language (`"No event"` / `"کوئی تاریخی واقعہ نہیں"`). Additive.
- **`HijriWidgetFonts.bundledUrduFont`, and the bundled Noto Nastaliq Urdu face it switches on.** The
  library now ships the face it was designed around (plus its OFL licence as an asset) and can draw
  every Arabic-script slot with it: the text is rasterised in the app's process — where a bundled
  `res/font` *is* visible, unlike a system font lookup — and handed to the launcher as a bitmap.
  Off by default, and source-compatible: the field is an addition to an existing data class, with a
  default. **Digits never wear the face**: figures keep the system face whichever numeral style is
  set (a digits-only slot is a figure, not a script word — and the grid's cell geometry is derived
  from width), and inside a mixed line the rasteriser pins every non-script run — digits, a Latin
  word — to the system face, so Nastaliq draws only the script.
- `TodayHijriWidgetData.gregorianMonthShortName` and `CalendarNames.englishGregorianMonthAbbreviations`
  — the three-letter English Gregorian month (`Oct`) a 1x1 tile prints where the full name clips.
  English only; Urdu month names keep their full spelling. Additive, last constructor parameter.

### Changed

- **1x1 tiles print a short month name.** English gets `Oct`/`Jan` and `Rabi' I`/`Rabi' II` — a
  Roman ordinal, so the pair reads as the month's name rather than as "the first Rabi'" — and the
  Today strip's tightest caption rung picks up the same form. **Urdu month names are never
  shortened**: `ربیع الاول` renders in full, the same rule the Gregorian abbreviation follows.
- **Every widget in the family now scales its type with the cell it was granted.** The three 1x1
  tiles, the dual-date tile and the Today strip derive **one** scale from the granted cell
  (`WidgetTypeScale.widgetTypeScale`, both axes) and every line is that scale times its reference
  size, so a resize grows or shrinks the whole stack together instead of growing some lines and
  pushing others out. `MIN_TYPE_SCALE` is the floor; there are no per-line floors left. This
  reverses FD-10's "never shrink a 1x1 tile's type to fit its cell": the earlier evidence was
  against shrinking lines *individually*, which starved one to save another; a single scale cannot.
- **The weekday name sits in the family accent band on every widget.** The 1x1 tiles and the Today
  strip drew it as bare text while the dual-date tile drew it on an accent strip; all four now use
  the same band (`WidgetBand`/`WeekdayBand`, `widget_accent` fill with `widget_on_today` text).
- The Today strip's `sizeMode` is now `SizeMode.Exact`, so its composition sees the height the
  launcher granted rather than the declared minimum. (An earlier attempt at this was reverted when
  a height-derived figure clipped a line; one scale over all three lines is what makes it safe.)
- The grid footer says "No event" instead of rendering an empty line when nothing is to be named.
- **Nastaliq is measured, not assumed, when the bundled face is on.** A script line drawn with it is
  2.5 em tall against the system fallback's ~1.7, so the three fitted typographies (the 1x1 tiles,
  the dual-date tile, the Today strip) take the ratio they should reserve as a parameter
  (`HijriWidgetFonts.scriptLineRatio`) and a Nastaliq stack is sized for the box it will actually
  occupy — smaller type at the same cell, and nothing clipped, which sizing it at 1.7 would have
  cost. `TEXT_LINE_RATIO` and the previous numbers are unchanged, so nothing moves while the face is
  off.
- **The grid header's two title lines now match.** The Hijri month + year and the Gregorian one under
  it are drawn at one size (`GridHeaderTypography.titleSp`), one weight (`Bold`) and one colour
  (`primaryText`). They used to be a hierarchy — Gregorian 10sp `secondaryText` Medium under Hijri
  13sp `primaryText` Bold — which read as a caption on a title rather than as the same month named in
  the other calendar. The era suffix on the Hijri line is what tells the two years apart now. The
  header asks ~7dp more height, which the weighted week rows give up.
- **The grid's weekday header row draws column rules.** With cell borders on, the seven day-name
  columns are divided by the same 1dp rules the day rows draw, in the same positions, so the header
  reads as the grid's first row rather than as a band floating above it. They follow
  `showCellBorders`, exactly as the day rows do, so a widget with rules off grows none. The rule
  needs a colour of its own: `widget_cell_border` *is* the accent at 40% and composites to the band's
  own value on the accent band, so the new palette member `WidgetColors.bandDivider` (resource
  `widget_band_divider`, plus its forced-light/dark counterparts) is the band content colour at 40%
  instead.
- **The Today strip's weekday band gives its height back to the month captions.** At a placed strip
  the band was ~43% of the widget and left the captions under each figure at ~9.4sp — the line the
  feedback was about. The weekday reference is now 13sp (was 17sp) and the caption 14sp (was 11sp), so
  at the same grant the band is ~33% and each caption is ~30% larger; the day figure is untouched, and
  all three still derive from one scale over the granted height.

### Fixed

- **Opening the app no longer turns placed widgets English.** The sample's settings sync ran on
  every app launch, and `HijriWidgetConfig.load()` answered with the library's English/PAKISTAN
  defaults when a widget had no per-instance options — which is every widget that follows the
  family mirror — so a mirror-following widget was rewritten to English on first open. Sync now
  skips mirror-followers, `load()` falls back to the family mirror, and the new
  `HijriWidgetConfig.loadOwn(id)` reads one widget's own options only. **Widgets already converted
  must be re-configured once**: the English values were written to storage and no migration is
  included.
- The Today strip's caption is Bold again, matching the grid's Hijri month (it had drifted to
  Medium).
- **The grid's weekday row draws in the bundled Urdu face, like every other Urdu slot on the
  widget.** The row passed no `maxLines`, so it silently took `Int.MAX_VALUE`, and the rasteriser —
  which handles a single line only — left the seven names in the system face while the rest of the
  widget was Nastaliq. `WidgetText` no longer defaults `maxLines`: a slot now has to state its line
  count to compile, so the omission cannot recur. The one slot that must wrap
  (`ChromeLabels.monthUnavailable`) states `Int.MAX_VALUE` and stays on Glance's `Text` by choice.
- The 1x1 tiles and the Today strip no longer clip a line when resized: at or above the declared
  minimum height the whole stack fits, so a taller cell renders larger type and a shorter one
  smaller type with nothing hidden.
- **Urdu Monday (`پیر`) is no longer rendered cropped on the grid's weekday row.** The rasteriser cut
  every script bitmap to the text's *advance* box, and Nastaliq ink overhangs it — `پیر` measures
  ~1.53 em of ink for ~0.71 em of advance, while all six other weekday names measure 0.95–0.97 — so
  the word lost its tail on the device. The line is now drawn into a scratch bitmap with one em of
  slack either side and cropped horizontally to the columns that actually painted (the crop function
  is `inkColumnRange`, pinned off-device). Height is still the full line box, which is what keeps a
  row of labels on one baseline.
- Tapping the grid's refresh icon now clears the tapped day (the selection block returns to today)
  while keeping the viewed month.
- **The grid header names both Gregorian months a Hijri month spans.** It printed the narrow tier's
  short title — the dominant Gregorian month, era dropped — so a Hijri month that straddled a month
  boundary named one of the two. It now always prints the full range
  (`HijriMonthWidgetData.gregorianMonthTitle`, pre-built in the projection); on the narrowest widgets
  the range ellipsizes rather than dropping a month. `gregorianShortTitle` stays in the API, but no
  first-party renderer reads it.
- **The grid's day cells are no longer squashed under the bundled Nastaliq face.** The chrome above
  the six weighted day rows — the two title lines, the weekday row, the footer — is four Nastaliq line
  boxes at 2.5 em each while their ink is far shorter, and on a 4-row Urdu grid that left the
  two-line cells 26.9 dp against the 29.3 dp they need: the Gregorian figure rested on the cell
  border. The grid's chrome now rasterises with the empty leading band dropped
  (`WidgetText(trimScriptLeading = true)`), the root's vertical padding is 6 dp (was 10) and the
  weekday row's rule measures with `HijriWidgetFonts.scriptInkBandRatio()`, which gives the cells
  38.1 dp. The trim is opt-in and bottom-anchored at the face's descent, so a bottom-aligned row of
  labels still shares one baseline; the fitted typographies (the 1x1 tiles, the dual-date tile, the
  Today strip) keep the full box they are sized against.

## 2.2.1 - 2026-10-17

### Fixed

- The month grid widget now falls back to the consumer's app-wide family mirror
  (`HijriWidgetConfig.loadFamily`) when it has no per-instance options — the fallback every tile
  widget already used — instead of the library's built-in `WidgetOptions.DEFAULTS`. A grid placed
  from the launcher rendered the English default until the host app's settings mirror happened to
  run, which in the cold process that renders it may never happen, and corrected itself only once
  the app was opened. No API change.

## 2.2.0 - 2026-10-15

### Breaking

- `PakistanHijriDate` now requires its Gregorian `localDate`, and `ObservedHijriDate` no longer
  defaults one. Build both through `PakistanHijriCalendar` / `ObservedHijriCalendar`, which resolve
  the day against the override table you pass.
- `HijriMonthWidgetData` gained `gregorianShortTitle`. Additive with a default — the source still
  compiles — but it moves the constructor/`copy`/`componentN` ABI. Recompile consumers.

### Added

- `HijriMonthWidgetData.gregorianShortTitle`: the dominant Gregorian month, era dropped, for a
  narrow header.

### Changed

- **The month grid widget got a new header and selection.** The title is two centred lines; a
  phone-width widget uses the short Gregorian title instead of truncating; the today and refresh
  icons sit in the title row, and every control shrinks together below 300dp. Today is the default
  selection, the chosen day is a filled accent block, and today keeps an accent ring once the block
  moves.
- **Larger day cells**: the Hijri figure is 15–20sp and the Gregorian 9–12sp on a 28dp minimum cell.
- **Weekday labels read stronger**: `onSurface` in the app, `primaryText` on the widgets.
- **Cell dividers use the accent colour**, and the in-app default `cellBorderColor` is `primary`.
- Fonts are only resolved through the system font map; a bundled font will not reach a widget.
- A 1×1 tile no longer clips its month caption — the era drops before the year.

### Fixed

- The in-app today ring is drawn when dividers are on, pinned by a pixel test.
- A calendar constructed in Pakistan mode selects today instead of showing today ringed but
  unselected.
- Forcing a month's length no longer crashes a calendar with a Pakistan day selected; the selection
  follows its real-world Gregorian day.
- Widget Gregorian day labels are no longer one day high under a month-end override.
- The midnight alarm and daily backstop are scheduled only while a widget is actually placed, so an
  app with no widget placed no longer shows the next-alarm indicator.
- Urdu month 5/6 names corrected to `جمادى الأولى` / `جمادی الآخِرۃ`.
- In-app month-length changes now reach the widgets as soon as they are saved.

## 2.1.1 - 2026-10-09

A **patch** number carrying a **feature** release: `WidgetOptions` gained two fields, which is
additive at the source but moves the ABI. If you pin exact versions, treat this as breaking and
recompile. Prefer 2.2.0.

### Added

- `WidgetOptions.theme` (`SYSTEM` / `LIGHT` / `DARK`) to pin a widget's appearance.
- `WidgetOptions.dateDisplayMode` (`HIJRI_ONLY` / `GREGORIAN_ONLY` / `BOTH`) for the grid's cells.

### Fixed

- The Today strip's picker preview no longer fails to inflate.

## 2.1.0 - 2026-10-09

### Breaking

- Shipped widget defaults changed: cell dividers on, Pakistan source, English.
- Adjacent-month days are hidden by default (`showAdjacentDays = true` brings them back).
- `TodayHijriWidgetData` gained `hijriMonthShortName`, an ABI-moving addition.

### Added

- `HijriDualDateWidget`, a fifth widget showing both dates in a 1×1 tile (FD-10).
- `TodayHijriWidgetData.hijriMonthShortName` for a narrow header band (FD-10).
- The Today strip names the weekday and separates its two dates with a hairline.
- `HijriWidgetFonts` to set the widget text families.
- `WidgetOptions.weekendPattern` and `HijriCalendarState.setWeekendDays` (FD-03).
- A loading bar and today/refresh buttons on the grid widget; the month arrows now also clear the
  selection.
- Era markers on years: `AH` / `AD`, `ھ` / `ء` (FD-05).
- `HijriCalendarDayCell.visible` and `showCellBorders`.

### Fixed

- The Urdu Mawlid name is corrected to `میلاد النبی`.
- The in-app header no longer names an observance from another month.
- The sample's mode toggle no longer crashes on a Gregorian period.
- Turning on grid dividers no longer costs the widget its last week.
- The widget header now names both Gregorian months a Hijri month straddles (FD-06).
- Switching dark mode no longer tears the widgets down and rebuilds them (FD-07).
- The 1×1 tiles name the weekday they show (FD-01).
- The grid widget's event footer renders, and an interrupted month step can no longer leave it
  untappable.

## 2.0.0 - 2026-10-03

Everything since 1.0.0, and breaking for a consumer on 1.0.0.

### Breaking

- `DateDisplayMode` moved to `calendar-ui`.
- `ObservedHijriDate` gained a `localDate` parameter.
- `HijriYearMonth` replaces `Pair<Int, Int>` for every year-month crossing a widget API.
- `minDate` / `maxDate` are resolved into real Gregorian days, not compared as Hijri dates.
- `HijriCalendarState` and `HijriCalendarHeader` changed shape; `HijriCalendarLabels` gained
  `headerTitle` and `gregorianMonthRangeLabel`.
- Accessibility text scaling is honoured (`HijriCalendar.ignoreFontScale` to opt out).
- `explicitApi()` is on: every public declaration names its type.
- Eight `HijriWidgetConfig` helpers moved to `internal`.

### Added

- `HijriMonthLengths`, a scoped month-length override table, and `WidgetOptions.monthLengthOverrides`
  to carry one into a widget.
- `CalendarNames`, `CalendarLog`, `WeekDay.fromIndex` and `OrNullIfOutOfRange`.
- The widget receivers are declared in the library manifest, so adding the dependency is enough to
  see widgets in the picker.
- `WidgetOptionsJson` decodes defensively: one unreadable field no longer resets the others.
- `apiCheck`, the binary-compatibility gate, and a consumer-resolution check that catches
  dependency-scope mistakes.

### Fixed

- Scoped month-length overrides were ignored by the grid and the Pakistan builder.
- `ObservedHijriDate.localDate` resolved against the wrong override table.
- `minDate` / `maxDate` were quietly ignored outside Umm al-Qura, and a wide range crashed the pager.
- Changing a setting left the grid on the previous month.
- Forcing a month end had no effect in Pakistan mode.
- Enabling dividers pushed the widget's last week out of view.
- The midnight alarm now uses `RTC_WAKEUP` and re-arms in a `finally` (WG-07, WG-08).
- Localized widget strings follow the widget's language, not the device (WG-12).
