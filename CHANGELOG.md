# Changelog

Notable changes to the published modules, newest first, one line per change.

Sections are `Added`, `Changed`, `Fixed`, `Deprecated`, `Removed` and `Migration`; only those a
release actually has appear. `Migration` is where a consumer has to do something — a signature that
moved, a default that changed, an ABI that needs a recompile. The reasoning, the measurements and
the alternatives that were tried live in [`docs/CHANGELOG-ARCHIVE.md`](docs/CHANGELOG-ARCHIVE.md),
under the same version headings.

## 2.4.0 - 2026-11-10

### Added

- `HijriMonthLengthsRepository` (`calendar-widget-glance`): month-length overrides now persist to one
  DataStore Preferences file in the application's `filesDir`, shared by the app and its widgets, so an
  alarm-woken widget in a fresh process renders the overrides a settings screen wrote.
- `HijriDate` and `HijriYearMonth` (`calendar-core`): the library's own Hijri value types, with
  `HijriDate.yearMonth`, `HijriYearMonth.atDay` / `plusMonths`, and `toHijrahDate()` /
  `toHijrahYearMonth()` conversions. They are `@Serializable` and validate the month.
- `calendar-widget-data`'s `HijriYearMonth` is now a `typealias` for `calendar-core`'s, so the widget
  projection and the in-app calendar name one type.
- `calendar-widget-glance` is covered by `apiCheck`: its release ABI is dumped to
  `api/calendar-widget-glance.api`, so a changed signature there fails the build like it does in the
  other published modules.

### Deprecated

- `HijriMonthOverrides`, the process-global override table. Warning level; the durable replacement is
  `HijriMonthLengthsRepository`.
- Every `HijriCalendarState` member and `CalendarMonth` / `CalendarDay` / `toCalendarMonth` /
  `todayHijriDate` surface that names the third-party `HijrahDate` / `HijrahYearMonth`. Warning
  level; the replacements are listed under *Migration*.

### Migration

- `calendar-widget-data`'s `HijriYearMonth` moved to `com.muazdev.hijricalendar.core`. Source
  compatible, but the class file moved, so recompile when you upgrade. Signatures that changed:
  `offsetHijriMonth`, `WidgetOptions.pinned`, `resolveGridMonth`, `resolveGridMonthOrNull`.
- Replace the alpha types with the library-owned ones: `HijrahDate` → `HijriDate`,
  `HijrahYearMonth` → `HijriYearMonth`, `CalendarDay.hijrahDate` → `CalendarDay.hijri`,
  `CalendarMonth.yearMonth` → `hijriYearMonth`, `firstDay` / `lastDay` → `firstHijriDay` /
  `lastHijriDay`, `HijriCalendarState.currentMonth` / `selectedDate` / `minDate` / `maxDate` →
  `currentHijriMonth` / `selectedHijriDate` / `minHijriDate` / `maxHijriDate`, `todayHijriDate(n)` →
  `todayHijri(n)`. The old signatures keep working until 3.0.

## 2.3.5 - 2026-10-28

### Fixed

- The compact tile's Gregorian date sat under a gap and its captions were too small: the band now
  reserves the measured ink of the weekday it prints rather than the face's worst case, the body is
  centred, and the reference caption rose 8 → 9sp.
- The compact tile's Android 12-14 `previewLayout` mirror follows: centred body, 9sp captions, and
  the projection's short month form.

### Changed

- `CompactDateTileTypography.sizesFor(availableWidthDp)` is now `sizesForDrawnEdge(availableWidthDp,
  bandInkEm)`, because the band's ink is a parameter and `sizesFor(62f, 62f)` was ambiguous.

## 2.3.4 - 2026-10-28

### Added

- Pakistani national days in Pakistan mode: `PakistanEvent` / `PakistanEvents` (eight
  Gregorian-fixed days), `CalendarEvent`, `CalendarDay.pakistanEvent` / `notableEvents` /
  `hasNotableEvent`, `HijriCalendarLabels.pakistanEventName` / `monthEventBannerWithPakistan`, and
  `HijriDayWidgetData.eventKey`.
- A sixth widget, `HijriCompactDateWidget`, with `HijriCompactDateWidgetReceiver` and
  `HijriCompactDateWidgetLivePreview`: an app-icon-sized dual-date tile that paints a fixed square of
  at most 72dp centred in the cell it is granted.
- `CompactDateTilePreviewLayoutTest`, pinning that square, its typography and the static
  `previewLayout` mirror.

### Changed

- A Pakistan-mode cell fills for a national day, and the in-app event banner names it; a month holding
  both a national day and an observance interleaves them by day.
- `WeekdayBandFit` is now the one rule for every widget's weekday size (half the dominant figure,
  capped at 22sp) and the band is as tall as the word's ink, so growing the word costs the body
  nothing.

### Fixed

- A one-day hole in the Pakistan calendar at 14 August 2026: `(1448, 2)` is pinned to its announced
  start, so the grid no longer paints 1448-02-30 twice and skips the day Independence Day falls on.

### Migration

- `HijriCalendarLabels` gained two defaulted slots and `HijriDayWidgetData` gained `eventKey`. Both
  are source-compatible, but they move `copy` / `componentN`, so recompile against 2.3.4. Every
  widget's weekday band also renders differently.

## 2.3.3 - 2026-10-21

### Added

- `PrimaryDate` (`calendar-ui`) and `WidgetDatePrimary` + `WidgetOptions.datePrimary` /
  `createWidgetOptions(datePrimary = ...)` (`calendar-widget-data`): under `BOTH`, which date is the
  top, bold line. Additive with a `HIJRI` default.
- `WeekendPattern.FRIDAY_SUNDAY`, a fifth pattern: Friday for prayer and Sunday to match the
  Gregorian working week.
- `HijriEvents` gains the first of Ramadan (`key = "ramadan"`).
- `HijriCalendarLabels.monthEventBanner` (now `(List<HijriEvent>) -> String`) and `noEventBanner`,
  plus `HijriCalendar(primaryDate = ..., showEventBanner = true)`.

### Changed

- A single-date cell's figure is `ExtraBold` (`CellFigureWeight`), the weight the `BOTH` primary line
  already used; under `BOTH` only the primary line is emphasized.
- Under `BOTH` the primary date is bigger and bolder: `bodyMedium` `ExtraBold` with stated 18sp + 14sp
  line boxes, so the stack still totals the 32sp the 48dp cell was sized for.
- The sample app gains a primary-date selector, a Fri+Sun weekend chip, an auto-sync toggle with a
  "Sync now" action, and switchable light / dark / system themes.

### Fixed

- The in-app grid's cell rule is drawn on the reading-trailing edge, so an RTL grid no longer merges
  the two leftmost cells.
- The event banner is permanently present, lists the month's observances names-only in day order, and
  narrows to one name when a day carrying one is tapped.
- A weekend day's red is text only, in-app and on the widget; observance cells and the banner are the
  quiet `secondaryContainer` wash rather than red.

## 2.3.2 - 2026-10-19

### Changed

- The Today strip prints `TodayHijriWidgetData.weekdayTileName`, the spelling the 1x1 tiles use, so
  two small widgets side by side no longer disagree about the same day. No API change.

## 2.3.1 - 2026-10-19

### Added

- `TodayHijriWidgetData.weekdayTileName`, `WidgetLocalization.urduWeekdayTileNames` /
  `weekdayTileNames(language)`, `WidgetOptions.localizedWeekdayTileNames`, and a trailing
  `weekdayTileNames` parameter on `todayHijriWidgetData(...)`.

### Changed

- The 1x1 tiles' type ceiling is raised (weekday 22 → 32sp, figure 44 → 64sp), so the whole range a
  1x1 is resized into scales.
- The dual-date tile's figure is larger (20 → 24sp) with caption ratio 0.85 → 0.78.
- The in-app observance is an accent banner below the grid at 50% opacity with non-bold text; the
  grid widget's footer takes the same treatment.
- The in-app cell's vertical rule is 1dp rather than one device pixel.
- The script rasteriser no longer ellipsizes: a run that overhangs is drawn whole and scaled by its
  parent instead of losing a letter.

### Migration

- The new `weekdayTileNames` constructor parameter moves `TodayHijriWidgetData`'s `copy` /
  `componentN` ABI — recompile against 2.3.1.

## 2.3.0 - 2026-10-18

### Added

- `WidgetLocalization.ChromeLabels.noEvent`, the grid footer's "No event" line in the widget's own
  language.
- `HijriWidgetFonts.bundledUrduFont` and the bundled Noto Nastaliq Urdu face (off by default): every
  Arabic-script slot on a widget is rasterised in the app's process and handed to the launcher as a
  bitmap. Digits never wear the face.
- `TodayHijriWidgetData.gregorianMonthShortName` and
  `CalendarNames.englishGregorianMonthAbbreviations`.

### Changed

- 1x1 tiles print a short month name (`Oct`, `Rabi' I`); Urdu month names are never shortened.
- Every widget in the family scales its type from the granted cell with one scale, so a resize grows
  or shrinks the whole stack together.
- The weekday name sits in the family accent band on every widget.
- The Today strip's `sizeMode` is `SizeMode.Exact`.
- The grid header's two title lines share one size, weight and colour; the era suffix separates the
  years.
- The grid's weekday header row draws the same column rules the day rows draw.
- The Today strip's weekday band is smaller (17 → 13sp) and its captions larger (11 → 14sp).
- Nastaliq is sized for the box it actually occupies (`HijriWidgetFonts.scriptLineRatio`).

### Fixed

- Opening the app no longer turns placed widgets English: `load()` falls back to the family mirror,
  and the new `HijriWidgetConfig.loadOwn(id)` reads one widget's own options only.
- The grid's weekday row renders in the bundled face like every other Urdu slot (`WidgetText` no
  longer defaults `maxLines`).
- Urdu Monday (`پیر`) is no longer cropped on the grid's weekday row: the raster is cropped to its
  painted ink rather than to its advance.
- The grid's day cells are no longer squashed under the bundled Nastaliq face
  (`WidgetText(trimScriptLeading = true)`, 6dp root padding).
- Tapping the grid's refresh icon clears the tapped day while keeping the viewed month.
- The grid header names both Gregorian months a Hijri month spans.
- The Today strip's caption is Bold again, matching the grid's Hijri month.
- The 1x1 tiles and the Today strip no longer clip a line when resized.

### Migration

- Widgets the sample had already converted to English must be re-configured once: those values were
  written to storage and no migration is included.

## 2.2.1 - 2026-10-17

### Fixed

- The month grid widget falls back to the consumer's family mirror when it has no per-instance
  options, instead of the library's English defaults. No API change.

## 2.2.0 - 2026-10-15

### Added

- `HijriMonthWidgetData.gregorianShortTitle`, the dominant Gregorian month for a narrow header.

### Changed

- The month grid widget got a two-line header, in-title today / refresh icons, a 300dp size split,
  today as the default selection, a filled block for the chosen day and an accent ring for today.
- Larger day cells (Hijri 15–20sp, Gregorian 9–12sp on a 28dp minimum), stronger weekday labels and
  accent-coloured cell dividers.
- A 1×1 tile no longer clips its month caption; the era drops before the year.

### Fixed

- The in-app today ring is drawn when dividers are on.
- A calendar built in Pakistan mode selects today instead of ringing it unselected.
- Forcing a month's length no longer crashes a calendar with a Pakistan day selected.
- Widget Gregorian day labels are no longer one day high under a month-end override.
- The midnight alarm and daily backstop are scheduled only while a widget is placed.
- Urdu month 5/6 names corrected to `جمادى الأولى` / `جمادی الآخِرۃ`.
- In-app month-length changes reach the widgets as soon as they are saved.

### Migration

- `PakistanHijriDate` now requires its Gregorian `localDate`, and `ObservedHijriDate` no longer
  defaults one: build both through `PakistanHijriCalendar` / `ObservedHijriCalendar`.
- `HijriMonthWidgetData` gained `gregorianShortTitle`. Additive with a default, but it moves the
  constructor / `copy` / `componentN` ABI — recompile against 2.2.0.
- Fonts resolve only through the system font map; a bundled `res/font` will not reach a widget (see
  `HijriWidgetFonts.bundledUrduFont` in 2.3.0 for the mechanism that does).

## 2.1.1 - 2026-10-09

### Added

- `WidgetOptions.theme` (`SYSTEM` / `LIGHT` / `DARK`) to pin a widget's appearance.
- `WidgetOptions.dateDisplayMode` (`HIJRI_ONLY` / `GREGORIAN_ONLY` / `BOTH`) for the grid's cells.

### Fixed

- The Today strip's picker preview no longer fails to inflate.

### Migration

- A patch number carrying a feature: two `WidgetOptions` fields were added, which is additive at the
  source but moves the ABI. Treat it as breaking if you pin exact versions, and prefer 2.2.0.

## 2.1.0 - 2026-10-09

### Added

- `HijriDualDateWidget`, a fifth widget showing both dates in a 1×1 tile.
- `TodayHijriWidgetData.hijriMonthShortName` for a narrow header band.
- The Today strip names the weekday and separates its two dates with a hairline.
- `HijriWidgetFonts` to set the widget text families.
- `WidgetOptions.weekendPattern` and `HijriCalendarState.setWeekendDays`.
- A loading bar and today / refresh buttons on the grid widget.
- Era markers on years: `AH` / `AD` and `ھ` / `ء`.
- `HijriCalendarDayCell.visible` and `showCellBorders`.

### Fixed

- The Urdu Mawlid name is corrected to `میلاد النبی`.
- The in-app header no longer names an observance from another month.
- The sample's mode toggle no longer crashes on a Gregorian period.
- Turning on grid dividers no longer costs the widget its last week.
- The widget header names both Gregorian months a Hijri month straddles.
- Switching dark mode no longer tears the widgets down and rebuilds them.
- The 1×1 tiles name the weekday they show.
- The grid widget's event footer renders, and an interrupted month step cannot leave it untappable.

### Migration

- Shipped widget defaults changed: cell dividers on, Pakistan source, English.
- Adjacent-month days are hidden by default (`showAdjacentDays = true` brings them back).
- `TodayHijriWidgetData` gained `hijriMonthShortName`, an ABI-moving addition — recompile.

## 2.0.0 - 2026-10-03

Everything since 1.0.0, and breaking for a consumer on 1.0.0.

### Added

- `HijriMonthLengths`, a scoped month-length override table, and `WidgetOptions.monthLengthOverrides`
  to carry one into a widget.
- `CalendarNames`, `CalendarLog`, `WeekDay.fromIndex` and `OrNullIfOutOfRange`.
- The widget receivers are declared in the library manifest, so adding the dependency is enough to
  see the widgets in the picker.
- `WidgetOptionsJson` decodes defensively: one unreadable field no longer resets the others.
- `apiCheck`, the binary-compatibility gate, and a consumer-resolution check that catches
  dependency-scope mistakes.

### Fixed

- Scoped month-length overrides were ignored by the grid and the Pakistan builder.
- `ObservedHijriDate.localDate` resolved against the wrong override table.
- `minDate` / `maxDate` were ignored outside Umm al-Qura, and a wide range crashed the pager.
- Changing a setting left the grid on the previous month.
- Forcing a month end had no effect in Pakistan mode.
- Enabling dividers pushed the widget's last week out of view.
- The midnight alarm uses `RTC_WAKEUP` and re-arms in a `finally`.
- Localized widget strings follow the widget's language, not the device.

### Migration

- `DateDisplayMode` moved to `calendar-ui`.
- `ObservedHijriDate` gained a `localDate` parameter.
- `HijriYearMonth` replaces `Pair<Int, Int>` for every year-month crossing a widget API.
- `minDate` / `maxDate` are resolved into real Gregorian days, not compared as Hijri dates.
- `HijriCalendarState` and `HijriCalendarHeader` changed shape; `HijriCalendarLabels` gained
  `headerTitle` and `gregorianMonthRangeLabel`.
- Accessibility text scaling is honoured (`HijriCalendar.ignoreFontScale` to opt out).
- `explicitApi()` is on: every public declaration names its type.
- Eight `HijriWidgetConfig` helpers moved to `internal`.
