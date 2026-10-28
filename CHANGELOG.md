# Changelog

Notable changes to the published modules, newest first. The long-form write-ups live in `docs/`.

## 2.3.5 - 2026-10-28

### Fixed

- **The compact tile's Gregorian date sat too low, under a gap, and the date and month were too small to
  read.** Two independent defects met above the same row. The band reserved the face's *worst-case* ink
  (2.1em for Nastaliq) for whatever word it happened to print, so a Wednesday (`بدھ`, measured on a
  Pixel 6a at **0.77em**) reserved 2.1em and painted 0.77em — and the body was pinned to the
  bottom of its slot, which pooled that phantom in the one place a reader looks for the date. On a placed
  62dp tile that was **9.5dp of empty space** between the accent band and the Gregorian row.
  `WeekdayBandFit.inkEmFor` remains the worst case for a caller with no word to measure; the compact
  tile now measures its own (`weekdayInkEm` / `rememberWeekdayInkEm`, `Paint.getTextBounds` in the face
  the band draws with) and reserves what it paints, and the body is centred, so any residue is split
  rather than collected. Everything the band gave up goes to the body, which is also how the two
  captions grew: the reference caption is **8 → 9sp**, so at a placed 62dp cell the Gregorian date and
  the Hijri month render at ~5.7 → ~7.25sp and the Hijri figure at ~14.4 → ~16.1sp. Measured after the
  change, the gap above the Gregorian row is **0.4dp**.
- The Android 12-14 `previewLayout` mirror follows: centred body, 9sp captions, and the month sample is
  the projection's short form (`Oct`) — the full English name does not fit a half-width caption and was
  rendering clipped.

### Changed

- `CompactDateTileTypography.sizesFor(availableWidthDp)` — the width-only projection — is now
  `sizesForDrawnEdge(availableWidthDp, bandInkEm)`. With the band's ink as a parameter, `sizesFor(62f,
  62f)` was ambiguous between *(width, height)* and *(width, ink)*, and Kotlin resolved it silently.

## 2.3.4 - 2026-10-28

### Added

- **Pakistani national days, in Pakistan mode.** `PakistanEvent` + `PakistanEvents` (`calendar-core`)
  carry eight Gregorian-fixed national days — Kashmir Solidarity Day (5 Feb), Pakistan Day (23 Mar),
  Labour Day (1 May), Youm-e-Takbir (28 May), Independence Day (14 Aug), Defence Day (6 Sep), Iqbal
  Day (9 Nov), Quaid-e-Azam Day (25 Dec) — with English and Urdu names, a stable `key`, and
  `forDate` / `forMonth` / `hasAnyIn`. They are **Gregorian** coordinates and never Hijri: expressing
  14 August as a `HijriEvent` would move it eleven days earlier every year. Eid, Ashura and
  Eid Milad-un-Nabi stay in `HijriEvents`, because Pakistan *announces* those rather than fixing them
  by statute; provincial and one-off days are deliberately absent.
- `CalendarEvent`, the interface both event tables implement, plus `CalendarDay.pakistanEvent` (`null`
  outside Pakistan mode), `CalendarDay.notableEvents` (national day first, then the Hijri observance,
  in cell order) and `CalendarDay.hasNotableEvent`. Additive: `CalendarDay.event` is unchanged, and a
  day can now carry both marks without either being dropped.
- `HijriCalendarLabels.pakistanEventName` and `HijriCalendarLabels.monthEventBannerWithPakistan`, both
  defaulted. **Appended at the end of the data class, not grouped with `eventName`**: this is an
  `@Immutable` parameter object on a published artifact with a binary golden file, and inserting a
  parameter mid-list renumbers every later `componentN` — which is a `NoSuchMethodError` in already
  compiled consumer code rather than a recompile. `monthEventBannerWithPakistan` is only consulted for
  months that actually hold a national day, so a host's existing `monthEventBanner` customisation keeps
  applying to every other month. `UrduCalendarLabels` supplies both.
- `HijriDayWidgetData.eventKey` — the stable key of the day's notable date, so a host can print its own
  label without matching on a name. `null`-defaulted and additive. The grid widget's footer
  (`eventFor` / `nextEventName`) now resolves through it, Pakistan day first, which is also what fixes
  the footer naming a different observation than the filled cell meant in Pakistan/observed mode.
- **A sixth widget: `HijriCompactDateWidget`** (with `HijriCompactDateWidgetReceiver` and the public
  `HijriCompactDateWidgetLivePreview`). An app-icon-sized dual-date tile, four lines top to bottom: the
  weekday in the family accent band, then **one Gregorian row** with the day at one end and the short
  month at the other, then the **Hijri day as the largest line**, then the **Hijri month name** below it
  — no year, no era marker, no separator. The weekday appears once: a closing copy of it was built and
  removed, because at this size a duplicate of the same word costs a caption line. It answers both dates
  in the one cell where `HijriDateWidget` and `GregorianDateWidget` answer half each, and where
  `HijriDualDateWidget`'s four stacked zones collapse into each other. `minWidth/minHeight = 40dp`,
  `targetCell = 1x1`, `resizeMode="none"`, so a host grants one cell and offers no resize that would
  duplicate the dual tile. The tile **paints itself at a fixed square of at most 72×72dp centred in that
  cell** (`CompactDateTileTypography.DRAWN_EDGE_DP` / `drawnEdgeDp`) rather than filling it: the leftover
  cell height stays transparent, so no background shows past the content, and the square renders the same
  size on every host. It is capped by the cell — a placed widget on a Pixel 6a launcher gets a 62×88dp
  content area, so that host draws 62×62dp and only a cell at least 72dp in both axes draws 72dp — and a
  grant *below* the square shrinks the whole stack rather than clipping it. Type is derived from the
  drawn edge by one scale (reference 72×72dp, ceilings 12sp/40sp), and `sizeMode` is `SizeMode.Exact` so
  the composition can see a grant below the edge. The Gregorian day sits at the reading-start edge in both
  languages (`options.language.isRtl`), the two halves are weighted to opposite ends of their row, and
  the Hijri figure carries the accent colour.
- The compact tile is wired end to end: the library manifest (`<receiver>` and
  `hijri_compact_date_widget_info.xml`), `HijriWidgetPresence`, `HijriWidgetPreviewPublisher`,
  `HijriWidgetRenderQueue`, the Android 15+ `providePreview`
  (`PreviewSizeMode.Responsive(72×72dp)`), an Android 12-14 `previewLayout` mirror and an
  `previewImage` fallback for older hosts. The sample app gains `WidgetKind.COMPACT_DATE`, a configure
  activity, a settings screen entry, a catalog entry and its own widget-info override. **The existing
  five widgets are unchanged** — same classes, same receivers, same defaults.
- `CompactDateTilePreviewLayoutTest` pins the compact tile's contract: the square never above 72dp and
  capped by the cell, its type sized at the drawn edge, the stack fitting the declared 40dp minimum,
  and the static `previewLayout` mirror agreeing with the live composition line for line — band,
  Gregorian day, Gregorian month, Hijri figure, Hijri month — with no line carrying a year, an era
  or a separator.

### Fixed

- **The weekday name on every widget is legible, and its size is a rule rather than a per-widget
  choice.** `بدھ` was reported as "showing very very small, unable to read" on everything but the
  in-app grid. Two things were wrong, and both were the band: each widget sized the word from whatever
  line it happened to share (a caption on the dual and compact tiles, a hand-picked 13sp on the tiles and
  the strip), and the band then took the whole font *line box* as its height — 1.7em for ~0.75em of ink,
  so a short word sat as a smudge in a bright accent block a third of a 1×1 tile tall. The rule is now
  `WeekdayBandFit`: the word is **half the widget's dominant figure** (capped at 22sp), and the band is
  as tall as its **ink** plus its paddings, drawn through a raster cropped to that ink
  (`WidgetText(cropToInk = true)`), so growing the word costs the body nothing. At a placed Pixel 6a
  cell the weekday went from ~6.9 → ~9.1sp on the compact tile, ~8.7 → ~11.4sp on the dual tile and
  ~11.7 → ~12.5sp on the Today strip, while every band got *shorter*; the 1×1 tiles' word kept its 13sp
  and doubled its painted ink. `WeekdayBandFitTest` states the share on all four fitted typographies and
  `WidgetScriptTextTest` pins the three crop modes (`ScriptCrop`).
- **A one-day hole in the Pakistan calendar at 14 August 2026.** `PakistanHijriCalendar.FIXES` held a
  verified 1448 Rabi al-Awwal anchor (2026-08-15) but no Safar entry, so the chain fell back to Umm
  al-Qura's earlier start and ran 1448-02 out on 2026-08-13: the grid painted 1448-02-30 twice and **no
  page ever showed 2026-08-14** — the day Independence Day falls on. `(1448, 2)` is now pinned to its
  announced start (2026-07-16) and its 30 days, which is exactly the re-sync between a fallback-length
  month and a verified anchor that a `Fix` exists for. Pinned by
  `PakistanCalendarMonthTest.theMonthBeforeAResyncReachesItWithoutAHole`; a roundtrip test cannot catch
  this class of bug, since both directions share the chain.

### Changed

- **A Pakistan-mode cell fills for a national day**, and the in-app event banner names it: tapping 14
  August narrows the banner to "Independence Day" the same way tapping an observance narrows it to that
  observance, and a month holding both interleaves them by day rather than listing one table after the
  other. Calculation mode is untouched — a national day belongs to the Ruet-e-Hilal calendar the user
  chose, and showing it on the calculated grid would put a fill on a page whose whole claim is "these
  are calculated dates". The banner still reads the month's **cells**, so it cannot disagree with the
  fills above it.

## 2.3.3 - 2026-10-21

### Added

- `PrimaryDate` (`calendar-ui`) and `WidgetDatePrimary` + `WidgetOptions.datePrimary` /
  `createWidgetOptions(datePrimary = ...)` (`calendar-widget-data`): under `BOTH`, which date is the
  **top, bold** line. Name-backed and stored by name; additive with a `HIJRI` default, so an existing
  widget repaints identically.
- `WeekendPattern.FRIDAY_SUNDAY` — Friday for prayer and Sunday to match the Gregorian working week.
  New enum entry, so a stored pattern is unaffected; `WeekendPattern.of` now round-trips five patterns.
- `HijriEvents` gains the **first of Ramadan** (`key = "ramadan"`, "Ramadan" / "رمضان"), so the month's
  first day is named like Muharram's New Year is. Ten entries now; additive, and the lookup is still
  `(month, day)`.
- `HijriCalendarLabels.monthEventBanner` (now `(List<HijriEvent>) -> String`) and
  `HijriCalendarLabels.noEventBanner`, plus `HijriCalendar(primaryDate = ..., showEventBanner = true)`.
  Both labels have defaults and `UrduCalendarLabels` supplies Urdu versions.

### Fixed

- **The in-app grid's missing cell rule under RTL.** Each day cell drew its column rule on the
  *physical* trailing edge, but the reading-last cell is skipped so the grid keeps no outer border —
  and that cell sits on the **left** in an RTL grid, so the skipped rule landed *inside* the grid and
  left Thursday|Friday (a Saturday-first week's two leftmost cells) looking like one double-width
  cell ("grid lines missing on Thursday/Friday, inconsistently").
  New `GridDividerDirectionTest` renders both directions and fails on the old geometry.
- **The event banner is permanently present**, and lists **every** observance the displayed month
  holds, in day order, names only — no day numbers, which the report asked to stop attaching. While
  the user pages through months it describes the month on screen; tapping a day **that carries an
  observance** narrows the line to that one name, and tapping a day without one (or paging away from a
  tapped day) keeps the month's list. It reads the month's **cells**, so it cannot disagree with the
  filled cells in Pakistan or observed mode, and a month with none falls back to a localizable "no
  event this month". `showEventBanner = false` restores the old behaviour.
- **The bold red on a weekday is text, never a fill.** Days in the user's chosen weekend pattern
  (Sunday, Friday+Sunday, …) keep the red *figure* in the in-app grid (`weekendDayContentColor`, the
  theme's `error`) and on the grid widget (`widget_weekend_text`); nothing is drawn behind them. A
  fill was tried in this round and reported back: the grid's one fill has to mean "observance", and a
  weekend is a day the user does not work rather than a second kind of observance.
- Observance cells and the event banner are **not red** at all: back to the quiet
  `secondaryContainer` wash with `onSecondaryContainer` content in-app, and the accent wash
  (`widget_event_day_background`) on the widget. An observance can fall on a working day, so red there
  carried no information.
- The event banner's container is the quiet wash at 50% opacity, matching the observance cell above it.

### Changed

- **A single-date cell's figure is bold.** `HIJRI_ONLY` and `GREGORIAN_ONLY` draw the one date they
  were asked for at `ExtraBold` (`CellFigureWeight`), the same weight the `BOTH` primary line uses; they
  had been left at `bodyMedium`'s own weight, so a one-date grid read lighter than the two-line one
  beside it. Under `BOTH` **only** the primary line is emphasized — the sub-line keeps `labelSmall`'s
  own weight, because two lines at one weight read as two primary values. Pinned by
  `DayCellFigureWeightTest`, which measures the painted ink of the cell against a regular and a bold
  reference: no other test can see a `fontWeight`, since semantics expose a `Text`'s string only.
- **Under `BOTH`, the primary date is bigger and bolder**: `bodyMedium` at `ExtraBold` instead of
  `bodySmall` at `Bold`, with stated line boxes (18sp + 14sp) so the two-line stack still totals the
  32sp the fixed 48dp cell was sized for at `BothModeMaxFontScale`.
- The grid widget's weekend treatment is unchanged in *meaning* — `widget_weekend_text` on the figure,
  no fill — and `dayCellTone` still puts an observance above a weekend, so an event on a Friday shows
  the event wash and is named by the footer.
- The sample app gains a primary-date selector (`Both` only), a **Fri+Sun** weekend chip, and a
  persisted **"Apply these settings to widgets automatically"** toggle (default on) with a "Sync now"
  action that ignores it. The toggle is what makes the mirror's override of individually-configured
  widgets something the user chose; the settings it writes now include weekend, adjacent days, cell
  dividers, the date-in-cell mode and the primary date, not only the calendar source.
- **The sample app renders in light *and* dark**, switchable in the app bar (`System` / `Light` /
  `Dark`, persisted app-wide). Every screen used to be wrapped in a bare `MaterialTheme { }` — which is
  Material's light scheme whatever the device says — so `HijriCalendarColors`, all of it derived from
  `MaterialTheme.colorScheme`, could never be seen in a dark palette on this app. The scheme is the
  plain `lightColorScheme()`/`darkColorScheme()` pair and **not** dynamic (wallpaper) colour, so two
  screenshots of the same month stay comparable. The status-bar icons follow the *chosen* mode rather
  than the device's, and the widget settings form regained the window insets Android 15+ stopped
  granting it (its title had been drawing under the status bar).

## 2.3.2 - 2026-10-19

### Changed

- **The Today strip names the weekday the way the 1x1 tiles do.** It printed
  `TodayHijriWidgetData.weekdayName` — Urdu Monday `پیر` — while the tiles printed `weekdayTileName`
  (`سوموار`), so two small widgets sitting side by side disagreed about the same day. The strip now
  reads `weekdayTileName` too. No API change: the field and both lists already exist, and a caller
  that passes no list still gets `weekdayName`. The grid's weekday *column heading* and the in-app
  weekday header keep `پیر`, because there the word heads a column rather than naming one day.

## 2.3.1 - 2026-10-19

### Added

- `TodayHijriWidgetData.weekdayTileName`, `WidgetLocalization.urduWeekdayTileNames` /
  `weekdayTileNames(language)`, `WidgetOptions.localizedWeekdayTileNames`, and a trailing
  `weekdayTileNames` parameter on `todayHijriWidgetData(...)`. A **1x1 tile** spells Urdu Monday its
  own way (`سوموار`), where the grid, the Today strip and the in-app weekday header keep `پیر`. The
  projection owns the choice, so every renderer just prints the field; `weekdayName` is unchanged and
  still what the non-tile widgets read. Additive with defaults, but the new constructor parameter
  moves `TodayHijriWidgetData`'s `copy`/`componentN` ABI — recompile consumers.

### Changed

- **The 1x1 tiles' type ceiling is raised (weekday 22 → 32sp, figure 44 → 64sp).** The previous
  ceilings were reached at ~2 columns / ~2 rows, so every further resize rendered the *same* weekday
  and figure and the tile looked like it had stopped responding to a resize. The ceiling now sits at
  a ~3x3 cell, so the whole range a 1x1 is actually resized into scales. Growing a ceiling cannot
  clip: the height axis bounds the scale at exactly the height the stack asks for.
- **The dual-date tile's figure is larger** (20 → 24sp) with a matching caption ratio (0.85 → 0.78),
  keeping the three lines inside the declared 40dp minimum.
- **The in-app observance moves below the grid** and becomes an accent banner at 50% opacity with
  **non-bold** text, instead of a coloured line under the header. `HijriCalendarHeader.eventText`
  stays public; the built-in `HijriCalendar` no longer passes it. The widget's grid event footer takes
  the same treatment: an accent chip on `eventDayBackground` with `onEventDayText`, not bold.
- The in-app cell **vertical** rule is now 1dp. It was drawn at `1f` — one *device pixel*, ~0.36dp at
  a 2.75x density — so the columns came out far fainter than the 1dp horizontal rules and rounded away
  at some columns: "the cell borders vanish". The widget's `CellDivider` was already 1dp.
- **The script rasteriser no longer ellipsizes.** It laid the line out at the width budget, so a run
  the minimum-shrink floor could not fit was trimmed by `TruncateAt.END` — which ate the final `ت`
  of an Urdu Thursday (`جمعرات`) on the bordered grid widget. The layout is now built at the run's
  own natural width and a run that overhangs is drawn whole; the parent's `fitCenter` scales it back
  down, which shrinks a word rather than amputating a letter from it.

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
