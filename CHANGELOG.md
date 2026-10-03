# Changelog

All notable changes to the published modules (`calendar-core`, `calendar-ui`,
`calendar-widget-data`, `calendar-widget-glance`).

Versions follow the `publishing.version` Gradle property; distribution is currently JitPack.

## Unreleased

### Breaking

- **Adjacent-month days are no longer shown unless you ask for them.** Every grid in the library
  padded each Hijri month to a fixed 42 cells and painted the neighbours' days in a dimmer
  colour. They were not optional — there was no flag — so every consumer has been seeing them
  regardless of what it wanted. `WidgetOptions.showAdjacentDays` (default `false`) and
  `HijriCalendarState.showAdjacentDays` (default `false`) turn them off, and the grid then takes
  **five or six rows instead of always six**. A `rememberHijriCalendarState` /
  `rememberSaveableHijriCalendarState` parameter of the same name carries it on the in-app side,
  and `HijriCalendarDayCell` gained a `visible` parameter defaulting to `true`.
  ([FD-02](docs/issues/2026-10-03/FD-02-hide-adjacent-days-by-default.md))

  Neighbours are **blanked, not removed**: a Hijri month can begin mid-week, so the first padded
  week holds some of the previous month's days before the 1st, and dropping those cells would slide
  the 1st into column zero and put every day of the month under the wrong weekday heading. What is
  removed is any week with no day of the month in it.

  A widget stored before this field existed decodes into the new behaviour, so upgrading changes
  every already-placed grid. Set `showAdjacentDays = true` to keep the old look.

### Added

- **Which days the widget and the in-app calendar paint as non-working days is now an option.**
  `WidgetOptions.weekendPattern` (a new `WeekendPattern`: Friday+Saturday, Sunday, Friday only, none)
  and `HijriCalendarState.setWeekendDays(...)` replace a hardcoded `WeekDay.WEEKEND_DAYS` literal at
  the single call site that built a projection. The default is Friday+Saturday, which is what every
  release before this rendered, so no already-placed widget changes. ([FD-03](docs/issues/2026-10-03/FD-03-configurable-weekend-days.md))

  It was reported as "two red days, every calendar shows one" — they were Friday and Saturday, which is
  the correct weekend for the Pakistan calendar this library also supports. The behaviour was right and
  the *configuration* was missing, so nothing about the default changed; what changed is that a user
  whose weekend is not Friday and Saturday now has somewhere to say so. `WeekDay.WEEKEND_DAYS` in
  `calendar-core` is untouched — it is a default for consumers who never see a widget.

  `WeekendPattern` is a name-backed enum owned by `calendar-widget-data` rather than a set of
  `WeekDay` ordinals, which would have been the WD-05 hazard arriving a second time: inserting a
  `WeekDay` entry would silently re-interpret every stored widget.

### Added

- **Years now carry their era marker.** `AH`/`AD` in English, `ھ`/`ئے` in Urdu, on the widget grid
  header, the today strip, both 1×1 tiles, and the in-app calendar's header and selected-date card.
  ([FD-05](docs/issues/2026-10-03/FD-05-era-markers.md))

  A Hijri year and a Gregorian year are both four digits, and this is a bilingual calendar that shows
  one under the other — `١٤٤٨` above `2026` leaves a reader guessing which is which. `ھ` is U+06BE, the
  Urdu *hijri sani* letter; `ہ` (U+06C1) is a word letter and is a different codepoint that still
  renders as a plausible Urdu letter, which is why the test asserts the code point. `AD` rather than
  `CE`, matching the Urdu and Indian convention.

  The **library's** defaults are unchanged — an empty marker renders a bare year — because
  `HijriCalendarLabels` is published API and a default that changed output would silently restyle every
  existing consumer's header. Opting in is a host's decision, and the shipped Urdu bundle does it.

  New projection fields `HijriMonthWidgetData.hijriYearText`, `TodayHijriWidgetData.hijriYearText` and
  `.gregorianYearText` carry the era'd year alongside the bare one, because the era follows the
  *widget's* language rather than the device's resources (WG-12) and a renderer must not format it.

### Fixed

- **The widget header no longer names a Gregorian month the Hijri month has nothing to do with.** A
  Hijri month is 29 or 30 days and a Gregorian month is 28-31, so roughly half of all Hijri months
  start in one Gregorian month and end in another — and the widget named only the first. Safar 1448
  began on 30 July 2026, so that header read `July 2026` for a month with not one day in it.
  ([FD-06](docs/issues/2026-10-03/FD-06-widget-header-both-gregorian-months.md))

  This was never a missing feature so much as a **disagreement**: the in-app header already rendered
  the range (`September - October 2026`), and a user comparing the two surfaces concluded one was
  broken. `HijriMonthWidgetData.gregorianRange` — the correct, iOS-only string — is now folded into
  `gregorianMonthTitle`, and the truncated field is gone. A month that fits inside one Gregorian month
  is unchanged.

- **Switching dark mode no longer tears the widgets down and rebuilds them.** The widget palette was
  resolved with `context.getColor(…)` at compose time and handed to Glance as a literal int. A number
  carries no idea of where it came from, so the launcher could not re-resolve it and a night-mode
  switch had to invalidate the whole `RemoteViews` and rebuild it — the widget went blank while the new
  one was composed. The palette is now built from `@ColorRes` ids, so Glance serialises a resource
  reference and the launcher resolves it against **its own** configuration at bind time: a theme switch
  repaints inside the existing view, with no re-compose and no placeholder.
  ([FD-07](docs/issues/2026-10-03/FD-07-day-night-colour-reresolution.md))

  No `uiMode` broadcast receiver was added, deliberately: re-rendering *is* the restart, so a receiver
  would only make the rebuild faster while adding a background Glance render. The three dimmed day
  figures and the dimmed nav arrow became real colour resources with night variants, because a
  `ColorProvider` cannot carry an alpha — and two of them had no night variant at all before, so a
  dimmed day figure was the one thing on the widget that could not follow the theme.

  This is the one change here that needs a device pass (`adb shell cmd uimode night yes|no` with a
  widget placed); no desktop test can see the restart it removes.

- **The 1×1 date tiles now name the day they are showing.** `HijriDateWidget` and
  `GregorianDateWidget` render the localized weekday name above a big day figure above the month
  name. `TodayHijriWidgetData.weekdayName` has always been computed by the shared projection and
  always rendered by iOS; the two Android tiles were the only place it was dropped, so `21 محرم`
  was all a user could read. No schema change — the name follows `options.language`, not the
  device locale. ([FD-01](docs/issues/2026-10-03/FD-01-tile-day-name.md))
- **The weekday line on the tiles reads as part of the date.** It shipped at 10sp Medium in
  `widget_text_secondary`, which made it the weakest line on a tile whose whole content is a date —
  the weekday name is half the answer to "what day is it?" and it was drawn like a footnote. It is
  now 11sp Bold in `widget_text_primary`, matching the month name, so the two names bracket the day
  figure as a pair. ([FD-01](docs/issues/2026-10-03/FD-01-tile-day-name.md))
- **The weekday line on the tiles now scales with the width the launcher grants.** It was fixed, so a
  name that reads comfortably on one launcher's 1x1 read as a caption on a wider one. It is linear in
  the tile's text width between a floor and a ceiling, so a wide tile gets a larger name and a narrow
  one is floored rather than clipped — the weekday is the longest string on the tile, so an unbounded
  scale would render it as a stub on a launcher that grants a tight cell.
  ([FD-01](docs/issues/2026-10-03/FD-01-tile-day-name.md))
- **The tiles declare less content than before.** They laid out 34sp + 12sp plus 12dp of padding —
  58dp of text inside a 40dp box — and relied on the launcher handing over more than the
  widget-info's own minimum. Three lines now ask for ~47dp against the 34sp + 12sp layout's ~62dp,
  so the day figure is smaller (26sp rather than 34sp) and the month name is no longer the line that
  gets clipped. The 40dp minimum still cannot hold three lines of type; the tile is sized for the
  cell a launcher actually grants. ([FD-01](docs/issues/2026-10-03/FD-01-tile-day-name.md))

## 2.0.0 - 2026-10-03

Remediation of the `calendar-core` architecture review — see
[`docs/issues/INDEX.md`](docs/issues/INDEX.md) for the seven findings and their tickets.

### Breaking

- **`HijriCalendarGrid` no longer takes a `calendarMonth` parameter.** It took both a
  `HijriCalendarState` *and* the month resolved from it, then discarded the month and rebuilt it
  itself from a hand-maintained 11-key `remember` list. The list omitted `monthLengths`, which is
  how the scoped-override defect above survived. The grid now builds every page through
  `HijriCalendarState.calendarMonthFor(yearMonth)`, a new member that reads every input from the
  state, so a new builder parameter cannot be added and forgotten at a call site. Use
  `HijriCalendar`, or call `state.calendarMonthFor(...)` directly.
  ([UI-03](docs/issues/UI-03-one-month-builder.md))
- **`HijriCalendarState.calendarMonthFor` and `.gregorianRangeFor` are new.** A renderer that was
  calling `HijrahYearMonth.toCalendarMonth` by hand should call the state instead, so that the
  state's own configuration — selection, bounds, adjustment, calendar space, month-length override
  table — is applied. `gregorianRangeFor` resolves a month's real-world extent without building the
  42 cells, for headers.
  ([UI-03](docs/issues/UI-03-one-month-builder.md))
- **`CalendarMonth.gregorianRange` is now mode-aware.** Previously it reported the Umm al-Qura
  extent regardless of mode, so it was wrong for two of the three calendars. It is now computed by
  a single shared resolver. `CalendarMonth.gregorianMonthRange` (the old property) is **deprecated**
  but still returns the correct value.
  ([CORE-01](docs/issues/CORE-01-mode-aware-month-range.md))
- **Month-length overrides are scoped, not process-global.** `HijriMonthLengths` is a new
  instantiable table; `HijriCalendarState` takes a `monthLengths` parameter and every mutator,
  read and cache key uses that instance. `HijriMonthOverrides` remains as a delegating
  process-default for existing single-calendar consumers. Constructors that accept overrides
  (`toCalendarMonth`, `PakistanHijriCalendar.*`, `ObservedHijriCalendar.*`, `prewarm`) gained a
  trailing `overrides` parameter.
  ([CORE-02](docs/issues/CORE-02-scope-month-overrides.md))
- **`DateDisplayMode` moved from `calendar-core` to `calendar-ui`.** Import
  `com.muazdev.hijricalendar.ui.DateDisplayMode`. It was a presentation switch with no core
  consumer. Both sample platforms persist it by name, so no stored preference is invalidated.
  ([CORE-06](docs/issues/CORE-06-core-purity.md))
- **`ObservedHijriDate` gained a `localDate` constructor parameter.** It is stored rather than
  derived because the derivation needs an override table, and the value type does not carry one.
  The default reconstructs the old behaviour for the process-default table.
  ([CORE-06](docs/issues/CORE-06-core-purity.md))
- **`explicitApi()` is enabled on every published module.** Public declarations must now state
  their visibility and return type. Source-compatible for callers; it only affects this repo's
  own declarations.
  ([CORE-03](docs/issues/CORE-03-api-stability.md))

- **`WidgetOptions.firstDayOfWeekIndex` is replaced by a name-backed `weekStart`.** The old field was
  a bare ordinal into `calendar-core`'s `WeekDay` — another module's enum, in a published ABI —
  persisted in the shared wire format that exists precisely to avoid persisting ordinals. Inserting a
  `WeekDay` entry would have silently re-aligned every placed widget's header row with no compile
  error, no test failure and no log line. It is now the serializable `WeekStart` enum.
  `createWidgetOptions` and the flat `buildHijriMonthWidgetData` take `weekStart` instead of
  `firstDayOfWeekIndex`; read `WidgetOptions.effectiveWeekStart`, or the derived
  `firstDayOfWeekIndexValue` where the integer dialect is unavoidable. **Stored widgets are
  unaffected**: `WidgetOptionsJson` folds a legacy `"firstDayOfWeekIndex"` into `weekStart` at decode
  time, and never writes the ordinal back. ([WD-05](docs/widgets/WD-05-ordinal-first-day-of-week.md))
- **`buildHijriMonthWidgetData` and `todayHijriWidgetData` (flat overloads) gained a trailing
  `overrides` parameter.** It defaults to `HijriMonthOverrides.current`, so every Kotlin call site
  is source-compatible, but Kotlin default arguments are not exported to ObjC — a Swift call site
  passing every argument must now pass one more. (The `options:`-taking overloads, which is what
  both native renderers call, are unchanged.) Previously these builders forwarded no overrides to
  `toCalendarMonth` / `resolveGregorianMonthRange` / `PakistanHijriCalendar.gregorianToHijri`, so a
  widget silently ignored a scoped `HijriMonthLengths` and rendered from the process global — while
  the in-app calendar honoured it.
  ([WD-01](docs/widgets/WD-01-no-overrides-threading.md))
- **`createWidgetOptions` gained a trailing `overridesCsv` parameter** for the same ObjC-default
  reason. Pass `nil`/omitted to keep today's behaviour. It feeds the new
  `WidgetOptions.monthLengthOverrides` field; the existing arguments are untouched.
  ([WD-01](docs/widgets/WD-01-no-overrides-threading.md))

- **`HijriYearMonth` replaces `Pair<Int, Int>` for every year-month crossing a widget API, and
  `offsetHijriMonth` returns it instead of the alpha type.** A `Pair<Int, Int>` has no type-level
  distinction between a Hijri year-month and any other pair, so a renderer could swap the components
  and ask for "month 1447" — which is out of range, returns `null`, and leaves a silently blank
  widget. And `offsetHijriMonth` returned `com.abdulrahman_b.hijrahdatetime.HijrahYearMonth?`, a type
  from a `2.0.0-alpha07` dependency sitting in the published ABI: when that library reaches 2.0.0
  final and reshapes the type, every consumer of this one breaks at compile time and cannot work
  around it, because the type is in *our* signature. The alpha type is now used internally only.
  This also reaches `HijriWidgetConfig.loadViewedMonth`, which leaked the same pair past the module
  boundary. **Stored formats are unchanged** — no widget migration — and `HijriYearMonth` validates
  `month in 1..12` in its constructor. ([WD-07](docs/widgets/WD-07-alpha-types-in-abi.md))

- **The calendar now honours the system accessibility font scale.** It previously wrapped its whole
  subtree in `Density(fontScale = 1f)`, so a user at Android's largest text size got a calendar at
  exactly 100% — a WCAG 1.4.4 failure, invisible, and described in the README as a feature. The day
  **text** now scales up to a cap (`HijriCalendarDefaults.SingleLineMaxFontScale` 2×, or
  `BothModeMaxFontScale` 1.5× for the two-line mode), while the **cell geometry stays fixed** — the
  `dayCellSize` you pass is still the size you get at every display mode and every font scale, which
  is what makes a month grid a fixed-pitch matrix. The header honours the system setting uncapped.
  Pass `ignoreFontScale = true` for the previous flat rendering; prefer the default.
  ([UI-04](docs/issues/UI-04-accessible-text-scaling.md))
- **`HijriCalendar.ignoreFontScale` is new**, defaulting to `false`.
- **`HijriCalendarHeader` takes a `title: String` and `labels` instead of `monthName`, `year` and two
  English content-description defaults.** The header joined the title with a space itself and read
  `"Previous month"` / `"Next month"` as parameter literals, so a locale could neither reorder the
  title nor write the year in its own digits, and a direct caller shipped English screen-reader
  output. `HijriCalendar` itself is unaffected — `HijriCalendarLabels.headerTitle` defaults to the
  identical `"$monthName $year"`, so nothing visible changes.
  ([UI-10](docs/issues/UI-10-localization-completion.md))
- **`HijriCalendarLabels` gains `headerTitle` and `gregorianMonthRangeLabel`.** Both return the whole
  formatted string so ordering, separator and numeral system are the consumer's. The defaults
  reproduce the previous English output exactly, so existing label sets are unaffected. The module's
  own Urdu preview previously showed Arabic-Indic day figures under a Western-numeral year.
- **17 `@Preview` composables are no longer part of the published ABI.** They lived in `commonMain`
  of a published module, so they were compiled for iOS, embedded in both K/N frameworks and the
  Xcode app, and visible from Swift — and because `ui-tooling-preview` was a *non*-`api` dependency,
  a consumer's code referencing them needed a dependency the library did not declare. They now live
  in `androidMain`, where Android Studio's preview panel can still see them. Nothing was renamed:
  the ABI change is a pure deletion of 62 lines (the previews plus the Compose compiler's generated
  `ComposableSingletons$PreviewsKt`).
  ([UI-07](docs/issues/UI-07-previews-out-of-abi.md))

### Removed

- **Eight `HijriWidgetConfig` declarations are no longer public** — breaking for a consumer that
  called them. They are the switches that decide whether the widget updates, and
  `HijriWidgetConfig.markUpdatedNow(context, todayEpochDay())` called from outside permanently
  suppresses every non-bypassing refresh for the rest of the day, with a log line reading "already
  refreshed". Every call site was already inside the module. Narrowed to `internal`: `PREFS`,
  `markUpdatedNow`, `isFreshFor`, `markRefreshPending`, `isRefreshPending`, `lastUpdatedEpochDay`,
  `lastPreviewPublishedEpochDay`, `markPreviewsPublishedNow`. Everything a host's settings screen
  actually needs — `load`/`save`, `saveFamily`/`loadFamily`/`hasFamily`, viewed-month navigation,
  `widgetOptionsSaver`, the decoders — is unchanged.
  ([WG-05](docs/widgets/glance/WG-05-refresh-markers-public.md))

### Added

- **One unreadable option no longer resets all of them.** `WidgetOptionsJson` tolerated an unknown
  option *key* but not an unknown *value*: one unrecognised enum name threw, the decoder returned
  `null`, and the caller substituted wholesale defaults — so a single renamed `language` silently reset
  a widget's adjustment days, numerals, first day of week, pin, source and month-name language at
  once, permanently (options are only rewritten on the next save). The codec now enables
  `coerceInputValues`, which confines the damage to the one field that is actually unreadable, and
  narrows its `catch` to `SerializationException` so a real serializer defect fails a test instead of
  quietly producing defaults. Every serialised enum value also carries `@JsonNames` aliases — including
  the *accurate* spellings of `WidgetSource.CALCULATION` (`"UAQ"`, `"UM_AL_QURA"`), so the rename this
  invites can happen without invalidating a stored widget.
  ([WD-06](docs/widgets/WD-06-no-tolerant-enum-decoding.md))
- **An impossible stored option is repaired at the decode boundary, and the repair is logged.**
  kotlinx-serialization checks types, not values, so `{"pinnedMonth":13}` decoded cleanly into options
  no projection could build — and the failure was silent and terminal: the grid builder returned
  `null` and the widget answered by falling back to a **compact today card**, so a widget the user had
  resized to a four-column month grid quietly showed a small card in a large frame. That reads as
  intentional, which is worse than blank. Values are now repaired once at the shared codec — an
  impossible or half-set pin becomes *unpinned*, so the widget shows today — and
  `WidgetOptionsJson.decodeOrReport` reports which fields it touched so the platform can log it. The
  pinned *year* is deliberately left alone: its valid window differs between the two source modes, so
  a bound written here would be wrong for one of them.
- **`todayHijriWidgetData` can no longer throw**, and a negative number no longer indexes the
  Arabic-Indic digit table out of range. Two latent crashes on a render path, each one a sign change
  away from being live. ([WD-09](docs/widgets/WD-09-no-decode-validation.md),
  [WD-10](docs/widgets/WD-10-small-defects.md))
- **`WidgetOptions.monthLengthOverrides` is a new option**, stored as `monthLengthKey(year, month)
  -> 29|30` (e.g. `{"1448-3": 30}`) and threaded into the projection through
  `WidgetOptions.overridesTable()`. Empty means "follow the process-wide table", so an
  unconfigured widget behaves exactly as before; non-empty *replaces* the global rather than merging
  with it, so a widget and the app can never be half-configured against each other. This is what
  closes the iOS gap — the WidgetKit extension is a separate process with its own copy of
  `HijriMonthOverrides`, so an app-side override could never reach it; travelling inside
  `WidgetOptions` through the shared app group does. New helpers: `monthLengthKey`,
  `monthLengthsToMap`, `monthLengthsFrom`, `encodeMonthLengthsCsv`, `decodeMonthLengthsCsv`.
  ([WD-01](docs/widgets/WD-01-no-overrides-threading.md))
- **`calendar-ui` has a composable test harness.** 28 new cases across 4 suites, run by
  `./gradlew :calendar-ui:desktopTest` — the command the docs already name — so the module's
  composables are covered on every CI push instead of only on a device. Previously the only
  Compose test in the repository lived in the sample app's `androidTest`, which no CI job executed.
  `calendar-ui` goes from 50 tests to 77. Two build changes: `org.jetbrains.compose.ui:ui-test` in
  `commonTest`, and `compose.desktop.currentOs` in `desktopTest` because `compose.uiTest` supplies
  Skiko's JVM API but not the platform natives. The tests assert *agreement* between paired values —
  the header's range against the cells actually painted, and the pager's page against the state's
  month in both directions — which is the gap the pre-existing suite left. No public API change.
  ([UI-02](docs/issues/UI-02-compose-test-harness.md))
- **`calendar-ui` no longer depends on `material-icons-extended`.** It was declared for two
  `AutoMirrored` arrow glyphs that live in `material-icons-core` — which `compose.material3` already
  brings in — and it was pinned to **1.7.3** while the rest of Compose resolved from 1.12.0, so a
  published artifact mixed two release trains and dragged `material-icons-core:1.7.3` with it. The
  arrows are now drawn by `NavChevron`, so the module has no icon dependency on either artifact.
  Dependency removal only; no public API change.
  ([UI-13](docs/issues/UI-13-dependency-hygiene.md))

### Fixed

- **`calendar-widget-glance` now declares its four widget `<receiver>` elements.** It shipped the
  receivers, the widget-info XMLs, the preview layouts, the labels and the colours, but declared only
  the update and midnight receivers — so a consumer that added the dependency got **no widget in the
  picker at all**, and `AppWidgetManager.requestPinAppWidget` returned `false` with no error. The
  declaration was the only part of the integration a consumer had to reverse-engineer, and getting
  `exported` or the `APPWIDGET_UPDATE` filter wrong either throws at install or silently never
  updates. The four receivers are now in the library manifest; the sample's copies are deleted, so
  the library is the single declaration site. Verified against the built APK: four `exported="true"`
  receivers with correct provider meta-data, and every `res/xml*` override still carrying its
  `android:configure`, so the library's `@xml/<name>` reference resolves to the consumer's override.
  The library still ships **no configure activity** — that decision is unchanged and separate.
  ([WG-10](docs/widgets/glance/WG-10-no-receivers-in-manifest.md))
- **`calendar-widget-glance` now publishes `androidx.compose.ui` (and its BOM) at `api` scope.**
  They were `implementation`, which publishes at *runtime* scope, while four `public` composables —
  `HijriWidgetLivePreview`, `HijriTodayWidgetLivePreview`, `HijriDateWidgetLivePreview`,
  `GregorianDateWidgetLivePreview` — take `Modifier` and `DpSize` in their signatures. A consumer
  that added the dependency and used the documented settings-screen integration path got
  `cannot access class 'androidx.compose.ui.Modifier'`. No source change and no ABI break: a
  consumer's *resolution* changes, and only in the direction of working. `material3`, `foundation`,
  `activity-compose` and `lifecycle-viewmodel-compose` stay `implementation` — nothing public names
  a type from them. ([WG-01](docs/widgets/glance/WG-01-compose-runtime-scope.md))
- **`HijriWidgetRefresher` no longer marks the widget family as fresh after a coalesced render.**
  `HijriWidgetRenderQueue.renderAll` can return having called no Glance API at all — it recorded the
  request for an already-in-flight render — but `refreshAll` wrote the day's marker unconditionally
  afterwards. The marker means "the display now shows today", so every background trigger for the
  rest of that day (the 30-minute `onUpdate` sweep, the WorkManager backstop, the midnight alarm,
  the foreground catch-up) then read "already refreshed", skipped, and logged a line that reads like
  correct behaviour. The queue now reports `RenderOutcome.Rendered`/`Coalesced` and the marker — and
  the cleared pending flag — are written only on `Rendered`; a coalesced pass leaves the marker alone
  and logs why. ([WG-02](docs/widgets/glance/WG-02-mark-fresh-on-noop.md))
- **A short localization list no longer throws from the widget projection.** `buildHijriMonthWidgetData`
  indexed four caller-supplied name lists without a bound, on a function whose documented failure
  mode is `null` and whose callers are a Glance composition and a WidgetKit timeline callback, where
  an `IndexOutOfBoundsException` is not recoverable and reaches the user only as a blank widget. An
  11-entry Gregorian month list — guarded by nothing but a KDoc sentence saying it "must be 12" —
  was enough. All three lists are now normalised once at the entry point and a short one falls back
  to the built-in English names. The fallback is **wholesale rather than per entry** on purpose: the
  per-entry `getOrNull` this replaces degraded to a weekday header row with some names in one script
  and the rest in another, which a reader cannot diagnose and which has no error to notice it by.
  `todayHijriWidgetData` was brought in line — it never threw, but it rendered *partly* localized
  cards, so the two builders could disagree about what a short list means. No ABI change.
  ([WD-04](docs/widgets/WD-04-unguarded-name-indexing.md))
- **`widgetOptionsSaver()` stores one JSON string instead of a positional list of enum ordinals.**
  `restore` indexed `WidgetLanguage.entries[ordinal]` and two siblings with no bounds check, and
  `restore` runs inside `rememberSaveable` on the main thread during composition — so a Bundle
  written by a build whose `NumeralStyle` had a different number of entries, or an entry that was
  reordered, threw an `IndexOutOfBoundsException` into composition and took down the settings screen.
  It now delegates to `WidgetOptionsJson`, the module's own storage format and the one every other
  native renderer writes, so enums are stored by name and nothing in the saved value is positional.
  **One-time cost:** saved state in a live Bundle across an in-place app update was written in the old
  format and will not decode, so in-progress settings edits are lost once; that now degrades to the
  defaults rather than throwing. ([WG-06](docs/widgets/glance/WG-06-saver-unbounded-index.md))
- **The widget header and its cells can no longer describe different Gregorian months.** The grid
  was built with the caller's month-length table and the header was then computed by a *second* call
  to the core resolver, which read the process-wide table at a different instant — so an override
  landing in between produced a header naming one Gregorian month above cells containing another.
  Silent, and in a Hijri↔Gregorian bridge widget the two halves contradicting each other is the
  worst failure available. The header now reads the range off the month that was just built. This
  also completes WD-01: once `overrides` was threaded into the grid builder, the second call did not
  receive it and kept re-reading the global. ([WD-02](docs/widgets/WD-02-gregorian-title-second-resolve.md))
- **A half-set pin now reads as "not pinned" on every platform.** `WidgetOptions.pinnedYear` and
  `pinnedMonth` are two independent nullable fields on the wire, so a hand-edited or partially
  migrated JSON blob can carry one without the other. The iOS timeline checked `isPinned` (both
  non-null) and fell through to today; the Android widget read the two fields directly and chained
  each against today's month, so `{"pinnedYear":1447}` rendered a grid **labelled 1447** painting
  **this** year's days — silently, with nothing to notice. `WidgetOptions.pinned` is now the single
  read path for the pair and returns `null` unless both halves are set, and the four copies of the
  `viewed ?: pinned ?: today` precedence chain (three in the widget, one in the iOS timeline) are
  down to one shared resolver. The stored JSON format is unchanged, so every already-placed widget
  is untouched. Additive API only: `pinned` is new, and `equals`/`hashCode`/`toString` are now
  overridden to compare the *normalised* state, so a half-pin compares equal to no-pin — which is
  what they render as. ([WD-03](docs/widgets/WD-03-half-set-pin.md))
- **The midnight alarm now wakes the device, and is re-armed even when the render fails.** Both arms
  used `AlarmManager.RTC`, which fires only while the device is **awake** — and a phone at local
  midnight is normally asleep, so the rollover did not happen at midnight. It happened at the next
  unlock. The 30-minute `onUpdate` sweep does not rescue it either, because a widget host only
  delivers `onUpdate` while the launcher is up, so a widget could sit on yesterday's date until the
  daily backstop worker ran: up to 24 hours stale. This also meant the library declared
  `USE_EXACT_ALARM` — a permission whose entire justification is alarms that wake the device — without
  using it that way. Now `RTC_WAKEUP`, and the inexact arm upgrades from `set` to
  `setAndAllowWhileIdle` so degrading to an inexact alarm does not also give up Doze survival.
  ([WG-08](docs/widgets/glance/WG-08-alarm-not-wakeup.md))
- **A midnight refresh that throws can no longer leave the alarm unarmed — or kill the process.** The
  re-arm was the second statement rather than a `finally`, so a single `DataStore` I/O failure at
  00:00 skipped it and the only recovery was the daily worker. It is now in a `finally`, and the
  render is caught and logged at error level. The receiver's ad-hoc `CoroutineScope` had a bare
  `SupervisorJob` with no handler, which on Android means an uncaught throwable reaches the default
  uncaught-exception handler and **kills the process**; all three ad-hoc scopes in the module now
  share `HijriWidgetScope`, which logs instead. The receiver reports how long after arming it fired,
  which turns "the widget said yesterday" into a number. ([WG-07](docs/widgets/glance/WG-07-midnight-rearm-not-in-finally.md))
- **A coalesced render request can no longer be stranded.** `HijriWidgetRenderQueue` held a `Mutex`
  and a single pending slot, so a request arriving between the drain reading the slot as empty and
  the unlock found the lock held, recorded itself, and returned — leaving nothing holding the lock to
  drain it. The intent sat unrendered until an unrelated later request, which for a widget's
  next/prev arrows means it shows the *previous* month: the exact defect the queue exists to
  prevent. The queue is now a work queue plus a drainer flag with a release-then-recheck hand-off,
  which closes the window by construction. Collapsing also improved: an instance request already
  behind a queued sweep is now dropped instead of rendered and then immediately superseded, so a
  double-tap costs one render pass rather than two.
  ([WG-13](docs/widgets/glance/WG-13-stranded-pending-request.md))
- **A screen-reader user in an Urdu widget is no longer told "Next month" in English.** The grid's
  navigation arrows and the month title carried hardcoded English `contentDescription`s, so
  TalkBack announced them verbatim while every other string on the widget — month names, weekday
  headers, numerals, reading direction — followed the per-widget `WidgetOptions.language`. The
  arrows are the grid's *only* interactive controls, so for a screen-reader user they were the entire
  navigation experience. The three labels are now `WidgetLocalization.ChromeLabels` in
  `calendar-widget-data`, alongside the month-name lists they were bypassing, because a widget's
  language is data rather than a resource-configuration value and there is no `values-ur` that could
  express it. A fourth label, the "date unavailable" body, was the same bug as *visible text* — it is
  the entire body of the today strip and the two 1x1 tiles — and is localized too.
  ([WG-12](docs/widgets/glance/WG-12-hardcoded-content-descriptions.md))
- **A consumer-resolution gate exists.** `.github/scripts/check-consumer-resolution.sh` publishes the
  four modules into a scratch local repository and compiles `.github/consumer-check` — a separate
  Gradle build whose *only* dependency is the published `hijri-calendar-widget-glance` — against
  them. It is the only check in the repository that can observe a dependency-scope mistake: the
  sample app declares its own Compose/Glance dependencies, so a wrongly-scoped `implementation`
  keeps every other gate green. It reproduces WG-01 exactly when the defect is re-injected.
  ([WG-01](docs/widgets/glance/WG-01-compose-runtime-scope.md))

### Fixed outside the widget tickets

Both were found only because `./gradlew build` could finally run to completion: an earlier
compile failure in `calendar-core`'s common tests had been stopping the build before lint and the
K/N tests were ever reached. Neither is a widget change, and both are recorded here because a red
`./gradlew build` would otherwise have been attributed to the widget work.

- **`calendar-widget-glance`'s preview publisher was missing an API-level annotation.**
  `GlanceAppWidgetManager.setWidgetPreviews` is API 35+ and this module's `minSdk` is 26. The
  runtime guard was present — an `SDK_INT` check at the top of `publishIfDue` — but lint resolves
  `@RequiresApi` per function, so a guard in the caller does not cover a call made from the
  `publish` helper, and `lintDebug` failed with `NewApi`. The annotation now repeats the guard on
  the function that makes the call. Not a crash on older devices, but a hard lint failure.
- **`calendar-core`'s common tests could not compile for Kotlin/Native.**
  `OutOfRangeTest` used `::class.java`, which does not exist on K/N, so `compileTestKotlinIos*`
  failed and took `./gradlew build` with it. Now compared as `KClass` — and with `assertEquals`,
  not `assertSame`: a `KClass` reference is a wrapper and each `X::class` allocates a fresh one, so
  an identity check fails on the JVM even when the underlying class is identical. The first fix
  tried `assertSame` and made the JVM suite red in a way that looked like a real regression.

### Internal

- **The Android 12-14 widget-picker preview now matches the widget.** The `previewLayout` XMLs are a
  hand-written mirror of a Glance composition, and the grid one had drifted: 4 week rows where the
  widget paints 6 (42 cells, not 28), no leading or trailing out-of-month days, and no Gregorian
  sub-digit under any day except today's. A 28-cell month grid does not look broken — it looks like
  a calendar, so the picker gave no signal that the placed widget would render differently. It is
  regenerated from the real projection, and `StaticPreviewLayoutTest` ties its structure to
  `CalendarMonth.TOTAL_DAYS` so the preview cannot go stale relative to the widget. The test reads
  the layouts off disk (a `previewLayout` never reaches a unit test's resources), which means Gradle
  did not treat them as a task input: re-injecting the original drift still reported BUILD SUCCESSFUL
  until they were declared as one. ([WG-14](docs/widgets/glance/WG-14-static-preview-drift.md))
- **`computeLayoutRtl` is now testable, and tested.** It read
  `context.resources.configuration.layoutDirection` directly, so `android.jar`'s stubbed
  `ContextWrapper(null).resources` made it unreachable from a local unit test — the render-cache
  suite had a comment saying so. The platform read is now separated from the decision
  (`resolveLayoutRtl(deviceRtl, language)`), which is the right split on its own terms, and
  `LayoutDirectionTest` asserts the full truth table. The two mixed rows are the ones that carry the
  logic: on an RTL device the platform already mirrors the `LinearLayout` rows, so an Urdu widget
  must **not** pre-reverse, and an English widget must. `language.isRtl` alone — the obvious
  implementation — is wrong in both. ([WG-16](docs/widgets/glance/WG-16-test-coverage.md))
- **`PakistanCalendarPerformanceTest` asserts a ratio instead of a wall-clock budget.** Its 5 s
  full-range-scan budget was calibrated on the JVM and measured ~7 s on a debug Kotlin/Native build
  on the iOS simulator, failing for reasons unrelated to the code. Platform-sniffing was rejected
  because neither `kotlin.native.Platform` nor `java.lang.Class` can be named from `commonTest` at
  all. It now compares the cost of a year at the far end of the range against one at the near end,
  which is the actual invariant — an O(1) lookup costs the same wherever it sits, an O(N²) chain
  re-sum does not — and holds on every platform. A two-minute ceiling remains for the original
  symptom, which was "never completed".
- **`calendar-widget-glance`'s detekt baseline: 34 entries → 11.** Most were not accepted debt. Six
  were live `NoUnusedImports` (three redundant same-package `R` self-imports, plus `LocalContext`,
  `WidgetLocalization` and `WeekDay`); four files were missing a trailing newline and were baselined
  under *both* `FinalNewline` and `NewLineAtEndOfFile`, so eight entries covered four problems; and
  six more were findings this same batch of tickets had already fixed while the baseline kept
  vouching for them — including `LongParameterList` on the root composable that WG-12's
  `WidgetActions` grouping had already removed, and two `ReturnCount`s on functions WG-11 and WG-13
  rewrote. A baseline entry that matches nothing is not a failure detekt can report, so the only way
  to find one is to delete it and see; `./gradlew detektBaseline` is now a verified no-op.
  ([WG-15](docs/widgets/glance/WG-15-stale-detekt-baseline.md))
- **A parity test that can fail.** WD-01 was invisible to `calendar-widget-data`'s parity suite
  because every case in it omitted the `overrides` argument and so compared the process global
  against itself — a test that omits a parameter whose default *is* the global tests the global and
  appears to test the parameter. The new
  `month_gridMatchesInAppCalendarCellForCell_underAScopedOverrideTable` builds a scoped
  `HijriMonthLengths` and asserts the two builders agree cell for cell in both source modes. Writing
  it also produced a trap worth recording: the obvious "did the table change anything?" probe is a
  comparison of the grid's Gregorian epoch days, and in observed mode it **cannot** detect the
  override, because a 42-cell window anchored to the month's start leaves every date in place when
  the month is shortened — the dropped day is replaced by the next month's first, carrying the same
  date. The test compares `(hijriDay, isCurrentMonth)` instead.
  ([WD-11](docs/widgets/WD-11-test-coverage.md))
- **`WidgetActions` groups the four nullable render actions** into one `internal data class`.
  Threading the widget's language through `HijriWidgetRoot` pushed it further past detekt's
  `LongParameterList`, but the grouping is worth keeping for its own sake: both previews now build
  `WidgetActions()` — all four null — instead of each call site passing four nulls and having to
  agree with the other one about being non-interactive. `MonthHeader` was likewise split out of
  `MonthGrid`, which the same change pushed past the length limit; the header is the only part of the
  grid whose layout depends on the reading direction.
  ([WG-12](docs/widgets/glance/WG-12-hardcoded-content-descriptions.md))
- **`hijri.publish` adds a `consumerCheck` file repository** under `build/consumer-repo`, feeding the
  gate above. It is a scratch target only; a release run still publishes to Maven Central, and the
  script wipes the directory before each run so a stale artifact cannot satisfy the consumer.
  ([WG-01](docs/widgets/glance/WG-01-compose-runtime-scope.md))
- **`HijriWidgetRenderCache` keys on the widget's own override table.** It keyed on
  `HijriMonthOverrides.currentRevision`, which the projection never read — so it invalidated the
  cache on a change that could not affect the output, and would have missed one that could. The key
  is now the options' `monthLengthOverrides` map plus the global revision *only when that map is
  empty*. It is structural rather than a hash on purpose: a one-entry `Map`'s `hashCode()` is
  `key.hashCode() xor value.hashCode()`, so `{"1448-3": 30}` and `{"1448-4": 29}` collide, and a
  collision would hand a widget back a stale grid.
  ([WD-01](docs/widgets/WD-01-no-overrides-threading.md))
- **`widgetOptionsSaver()` carries the override map as a CSV** (element 8), not a `Map`: a map is
  not reliably Bundle-safe, and the saver is a `listSaver`. Lists written before this option existed
  end at element 7 and restore to "no widget-level overrides". The round trip uses the shared
  `encodeMonthLengthsCsv`/`decodeMonthLengthsCsv`, so the Swift store can use the same codec.
  ([WD-01](docs/widgets/WD-01-no-overrides-threading.md))
- **`check-doc-versions.sh` enforces Compose catalog coherence.** Every `org.jetbrains.compose.*`
  entry must resolve from `composeMultiplatform`; `androidx.compose.*` keeps an explicit
  allow-list of version-less BOM-managed entries. This is the only gate that can catch a transitive
  regression of the kind above — `apiCheck` is signature-only and `detekt` does not read the
  catalog. Note the check as first written passed when the original defect was re-injected, because
  it asked only whether an entry *had* a version; it now asserts which train the version comes from.
  ([UI-13](docs/issues/UI-13-dependency-hygiene.md))

### Fixed

- **Changing a setting no longer left the grid showing the previous month.** Each pager page cached
  its built `CalendarMonth` in a `remember` keyed on the month alone, so `adjustmentDays`,
  `pakistanDates`, the selection and the month-length override table all changed the state without
  changing anything on screen — `isSelected`, `isToday`, `isDisabled` and `isCurrentMonth` are
  constructor fields baked in at build time, so nothing downstream could notice. Pages now derive
  their month instead. Introduced by the UI-03 refactor, which replaced a hand-maintained 11-key
  `remember` list that omitted `monthLengths` (UI-01) with a single key that is always incomplete.
- **Forcing a month end had no effect in Pakistan (Ruet-e-Hilal) mode.** Two independent defects.
  `HijriCalendarState.calendarMonth` observed the override table's revision but `calendarMonthFor`,
  which the pager calls for every page, did not, so no page recomputed. Separately, the Pakistan
  builder converted each cell with `PakistanHijriCalendar.gregorianToHijri(shifted)` *without*
  `overrides`, silently falling back to the process-global default while the same function's
  start-anchor call honoured the passed table — so a **scoped** `HijriMonthLengths` produced a grid
  whose anchor and cells came from two different calendars. Invisible to every existing test, all of
  which drive the process global, where the default and the explicit argument are the same object.
  The header's Gregorian extent had the same omission.
- **The sample's selected-date card reported "No date selected" whenever a month-length override was
  in force.** `selectDay` routes selection to observed space in that case, and the card read only the
  Umm al-Qura and Pakistan holders.
- **Day cells had no press feedback at all.** Every one of the 42 cells passed `indication = null`
  to `clickable`, so a tap on a 48dp target produced nothing until the selection state changed —
  fast enough to read as "the tap did nothing". Day cells now get Material's default indication,
  bounded to the cell's circle for free because `clickable` is applied after `Modifier.clip`. The
  default indication is used rather than a constructed `ripple()` so it follows the ambient theme.
  This also removes a `MutableInteractionSource` that was retained per cell per composition purely to
  be handed to a `clickable` that ignored it.
  ([UI-08](docs/issues/UI-08-touch-feedback.md))

- **Rotating the device scrolled the calendar sideways through the months.** The pager and the
  state holder were kept in agreement by two `LaunchedEffect`s plus a `snapshotFlow { … }
  .drop(1)`. Because `rememberPagerState` restores its page from saved state, a restored pager
  disagreeing with a restored month was resolved by *animating* across every month in between, while
  the `drop(1)` discarded the one emission that would have corrected it silently — so a large jump
  such as "jump to today" or a restored month also scrolled through intervening months. The two
  effects are now one reconciliation collector with a stated invariant (**the state is the
  authority**), the emission-count heuristic is gone, and a month change of one page animates while
  any larger jump lands immediately. The rule is a named `pageJump` policy with its own tests, which
  caught an `Int` overflow in the first version that classified the largest possible jump as a
  one-page animation.
  ([UI-09](docs/issues/UI-09-pager-effect-sync.md)) `HijriCalendarState.monthLengths`
  — the instantiable table CORE-02 added — was never read outside `calendar-core`. Both calendar
  builders in `calendar-ui` omitted the `overrides` argument and silently fell back to the
  process-global `HijriMonthOverrides.current`, so a calendar configured with its own table
  **rendered a month its own data model said did not exist**: the state reported 29 days and the
  grid painted 30. The header's Gregorian range was wrong by the same amount. Both call sites now
  route through a single internal seam (`renderMonthFor` / `renderGregorianRangeFor`) that reads
  every input from the state, so a new builder parameter cannot be added without being passed.
  Covered by `RenderMonthOverridesTest` (11 cases, 8 of which fail against the old code).
  Source- and binary-compatible; no public API changed.
  ([UI-01](docs/issues/UI-01-scoped-overrides-ignored.md))
- **`minDate` / `maxDate` more than 500 months apart crashed the calendar.** The pager computed two
  page indices from the caller's bounds and fed them to a two-argument `Int.coerceIn`, which
  throws when the range is empty. A `minDate` 501 months (≈41 years 9 months) ahead of the initial
  month, a `maxDate` that far behind it, any wider range, or an inverted `minDate`/`maxDate` threw
  `IllegalArgumentException` **while composing** — on the consumer's first frame, with a `coerceIn`
  stack trace and no mention of a calendar limit. A caller mirroring `PakistanHijriCalendar`'s own
  documented 1400–1500 range was past the limit from one end to the other, and nothing documented a
  navigation horizon.
  The window is now a `PageWindow` value type whose bounds cannot invert silently, whose
  `coercePage` is single-argument and cannot throw, and whose unbounded sides reach
  `HijrahDate.MIN`/`MAX` — the same edges `canGoToPreviousMonth`/`canGoToNextMonth` clamp at, so a
  header arrow can no longer enable a month the pager cannot represent. Inverted bounds collapse to
  a single month (the arrows disable) rather than throwing. The fixed ±500-month constant is gone.
  Covered by `PageWindowTest` (20 cases, 11 of which fail against the old semantics).
  ([UI-05](docs/issues/UI-05-pager-window-crash.md))
- **`minDate` / `maxDate` were silently ignored outside plain Umm al-Qura mode.** The Pakistan and
  observed grid builders never received the bounds and hardcoded `isDisabled = false`, and
  `selectPakistanDate` / `selectObservedDate` did not consult them either — so a bounded calendar
  became unbounded whenever Pakistan mode or a month-length override was active. The bounds are
  now resolved into real-world Gregorian days and applied in all three modes, including cells
  whose day exceeds their month's calculated length.
  ([CORE-06](docs/issues/CORE-06-core-purity.md))
- **`ObservedHijriDate.localDate` resolved against the wrong override table.** It read the
  process-global default, so a date produced under a scoped table reported the Gregorian day of a
  different calendar — wrong by the cumulative drift between the two.
  ([CORE-06](docs/issues/CORE-06-core-purity.md))
- **`OutOfRange.kt` replaces eleven swallowed `catch` sites.** Range failures are classified
  explicitly; internal defects (`IllegalStateException`, `ArithmeticException`, …) now propagate
  instead of reading as "no date". Pakistan conversion walks stop at the supported boundaries
  instead of relying on iteration exhaustion.
  ([CORE-05](docs/issues/CORE-05-exception-semantics.md))
- **The in-app calendar and the widget showed different English month names.** Both modules held
  private copies and six of the twelve Hijri names had diverged (`Shaban`/`Sha'ban`,
  `Dhu al-Qadah`/`Dhu al-Qa'dah`, …). Both now read `CalendarNames`. **This changes six in-app
  Hijri month names** to the widget's spelling.
  ([CORE-06](docs/issues/CORE-06-core-purity.md))

### Added

- **`CalendarLog`** — a settable diagnostics sink (`d` / `w` / `e`). Replaces `println`, which is
  not reachable from a consuming app. See the KDoc for how to install one.
  ([CORE-04](docs/issues/CORE-04-logger-seam.md))
- **`HijriMonthLengths`** — instantiable, lock-free month-length override table.
  ([CORE-02](docs/issues/CORE-02-scope-month-overrides.md))
- **`CalendarNames`** — the single owner of the built-in English calendar name lists.
  ([CORE-06](docs/issues/CORE-06-core-purity.md))
- **`WeekDay.fromIndex`** — nullable, range-checked counterpart to the persisted `WeekDay.index`.
  `WeekDayOrdinalTest` now pins the ordinal mapping so reordering the enum fails the build.
  ([CORE-06](docs/issues/CORE-06-core-purity.md))
- **`OrNullIfOutOfRange`** helper for documented, narrowly-caught range failures.
  ([CORE-05](docs/issues/CORE-05-exception-semantics.md))
- **Binary-compatibility gate.** `apiCheck` runs in CI against committed `.api` and `.klib.api`
  dumps for all three KMP published modules, covering the iOS/Swift surface as well as the JVM one.
  A changed public signature is a build failure, not a release surprise.
  ([CORE-03](docs/issues/CORE-03-api-stability.md))

### Known limitations

- **`calendar-widget-glance` is not covered by `apiCheck`.** It is a plain `com.android.library`
  under AGP 9's built-in Kotlin support, which the binary-compatibility validator does not
  recognise, so it registers no dump task. It is listed in `apiValidation.ignoredProjects` so the
  gap is visible rather than looking like coverage. Closing it requires applying the standalone
  `org.jetbrains.kotlin.android` plugin to that module.
  ([CORE-03](docs/issues/CORE-03-api-stability.md))
- **`apiCheck` runs on the macOS CI job only.** The KLib dump spans the iOS targets and
  Kotlin/Native cannot produce those klibs on a Linux host.
  ([CORE-03](docs/issues/CORE-03-api-stability.md))
- **`calendar-core` re-exports `hijrah-datetime`, `kotlinx-datetime`, `kotlinx-serialization`
  and `kotlinx-collections-immutable` as `api`.** Consumers inherit their compatibility surface,
  and `apiCheck` cannot detect a breaking change in a third-party dependency. `HijrahDate` is
  alpha (`2.0.0-alpha07`).

### Internal

- `CalendarMonth.gregorianMonthRange` is deprecated in favour of the correct
  `gregorianRange` / `firstGregorianDay` / `lastGregorianDay`.
  ([CORE-01](docs/issues/CORE-01-mode-aware-month-range.md))
- `PakistanHijriCalendar`'s month-start table is cached per override instance; override-free
  lengths get a separate shared cache. `prewarm` and `isWarmForCurrentOverrides` take an explicit
  table.
  ([CORE-02](docs/issues/CORE-02-scope-month-overrides.md))
