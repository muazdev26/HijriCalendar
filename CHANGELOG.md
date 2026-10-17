# Changelog

Notable changes to the published modules, newest first. The long-form write-ups live in `docs/`.

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
