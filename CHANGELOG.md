# Changelog

All notable changes to the published modules (`calendar-core`, `calendar-ui`,
`calendar-widget-data`, `calendar-widget-glance`).

Versions follow the `publishing.version` Gradle property; distribution is currently JitPack.

## Unreleased

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

### Added

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

### Breaking

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

### Fixed

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
