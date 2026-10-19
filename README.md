# HijriCalendar

A Compose Multiplatform calendar for displaying and interacting with Hijri (Islamic) dates.

## Features

- Full month grid (6 weeks), month navigation, date selection
- Arabic-Indic numerals and RTL support
- Customizable colors, today indicator, weekend styling
- Configurable first day of week (default Saturday)
- Animated transitions, accessibility support
- Android (minSdk 26), iOS (arm64), Desktop (JVM)

## Installation

Distributed via [JitPack](https://jitpack.io/#muazdev26/HijriCalendar).

```kotlin
repositories { maven("https://jitpack.io") }
dependencies {
    implementation("com.github.muazdev26.HijriCalendar:hijri-calendar-compose:2.3.2")
}
```

## Quick Start

```kotlin
val state = rememberHijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
HijriCalendar(state = state, onDayClick = state.defaultOnDayClick())
```

## API

### `HijriCalendar`

```kotlin
@Composable
fun HijriCalendar(
    state: HijriCalendarState,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayCellSize: Dp? = null,
    onDayClick: (CalendarDay) -> Unit,
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    labels: HijriCalendarLabels = HijriCalendarDefaults.labels(),
    ignoreFontScale: Boolean = false,
)
```

No year/month picker, range/multi-select, or fixed-height mode. Navigate one month at a time via arrows/swipe, or `state.goToMonth(...)`.

### `HijriCalendarState`

```kotlin
class HijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.SATURDAY,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    weekendDays: Set<WeekDay> = setOf(WeekDay.FRIDAY, WeekDay.SATURDAY),
)
```

Properties: `currentMonth`, `selectedDate`, `calendarMonth`, `adjustmentDays`, `weekendDays`, `canGoToPreviousMonth`, `canGoToNextMonth`.
Methods: `goToNextMonth()`, `goToPreviousMonth()`, `selectDate(date)`, `goToMonth(yearMonth)`, `goToToday()`.

### Other public API

`rememberHijriCalendarState` / `rememberSaveableHijriCalendarState`, `HijriCalendarColors`, `HijriCalendarLabels`, `HijriCalendarDefaults`, `DateDisplayMode` (`HIJRI_ONLY`/`GREGORIAN_ONLY`/`BOTH`), `defaultOnDayClick`, `HijriCalendarDayCell`, `HijriCalendarHeader`. `HijriCalendarGrid` and `HijriWeekRow` are internal.

> `CalendarDay` carries up to three date slots (three calendars may disagree). `dayOfMonth` resolves them in order; `localDate` is the unambiguous Gregorian day.

## Persistence

State does not persist process death on its own. Use `rememberSaveableHijriCalendarState` (rotation + process death) or hoist into a `ViewModel` with `SavedStateHandle`. `CalendarDay` is `@Serializable`.

## Customization

```kotlin
// Theming
val colors = HijriCalendarDefaults.colors(selectedDayContainerColor = Color(0xFF1976D2))
HijriCalendar(state = state, colors = colors, onDayClick = state.defaultOnDayClick())

// First day of week
rememberHijriCalendarState(initialMonth = ..., firstDayOfWeek = WeekDay.SUNDAY)

// Arabic-Indic numerals
HijriCalendar(state = state, useArabicIndicNumerals = true, onDayClick = ...)

// RTL: set layoutDirection = LayoutDirection.Rtl in CompositionLocalProvider

// Custom day content
HijriCalendar(state = state, onDayClick = ..., dayContent = { day -> Text(day.dayOfMonth.toString()) })
```

## Date Range Limiting

```kotlin
rememberHijriCalendarState(initialMonth = ..., minDate = HijrahDate(1447, 1, 1), maxDate = HijrahDate(1447, 12, 30))
```

Out-of-range days are disabled; navigation is bounded by the range.

## Date Display Modes & Cell Sizing

`dateDisplayMode`: `HIJRI_ONLY` (default), `GREGORIAN_ONLY`, `BOTH`. Cells stay a fixed size (`48.dp` default via `dayCellSize`) regardless of mode. Text scales with the system font scale up to a cap (`BothModeMaxFontScale` 1.5× vs `SingleLineMaxFontScale` 2×); header is uncapped. Pass `ignoreFontScale = true` to disable scaling.

## Localization

```kotlin
val labels = HijriCalendarLabels(
    hijriMonthName = { _, month -> ... },
    gregorianMonthName = { month -> ... },
    weekdayShortName = { it.shortName },
    previousMonthContentDescription = "...",
    nextMonthContentDescription = "...",
    dayContentDescription = { day -> "Day ${day.dayOfMonth}" },
)
```

Defaults reproduce built-in English.

## Weekends

```kotlin
rememberHijriCalendarState(initialMonth = ..., weekendDays = setOf(WeekDay.FRIDAY))
```

## Moon Sighting Adjustment

```kotlin
rememberHijriCalendarState(initialMonth = ..., adjustmentDays = 1)
```

Shifts the whole calendar (day numbers, alignment, `isToday`, selection, bounds). `selectedDate` is already adjusted. Dates pushed out of range (~1300–1600 AH) render as disabled placeholders.

## Home-Screen Widgets

A shared projection module (`calendar-widget-data`, `WidgetCalendar` framework on iOS) computes each month grid + "today" projection, keeping both platforms in sync.

**Android (Glance):** five widgets ship in the library — `HijriCalendarWidget` (resizable month grid), `HijriTodayWidget` (today strip), and three 1x1 tiles `HijriDateWidget`/`GregorianDateWidget`/`HijriDualDateWidget`. Month grid has today's highlighted cell, weekend tint, faded out-of-month cells, on-widget month navigation, two-line Hijri/Gregorian header. Today's cell is a filled accent highlight; tapping clears it back to today. Today strip lays Gregorian + Hijri dates side by side under a weekday band. Tiles show day + month, or both dates at once (dual). Reading direction follows each widget's own language (Urdu RTL or English), not the device locale. Bundled Noto Nastaliq Urdu face is opt-in via `HijriWidgetFonts(bundledUrduFont = true)` — rasterised in-app, handed to the launcher as a bitmap. Settings (`adjustmentDays`, numeral style, first day of week, language, month names, source, pinned month for grid, bundled font, manual refresh) apply immediately with no Save step and refresh the whole family. Live previews via `HijriWidgetLivePreview` etc. Midnight + `TIME_CHANGED`/`TIMEZONE_CHANGED`/`LOCALE_CHANGED`/`BOOT_COMPLETED` refresh with a WorkManager 24h backstop; background renders skipped while foregrounded. Deep-link on tap to `hijricalendar://today`. Android 15+ uses `providePreview`; 12–14 use static `previewLayout` XMLs.

**iOS (WidgetKit, 17+):** `systemSmall`/`Medium`/`Large`, one timeline entry per day, deep-links to `hijricalendar://today`.

### Adding the widget to your app

```kotlin
dependencies { implementation("com.github.muazdev26.HijriCalendar:hijri-calendar-widget-glance:2.3.2") }
```

The library's manifest declares all five receivers; widgets work on defaults. Optional setup:

1. In `Application.onCreate`: `HijriWidgetRefreshScheduler.schedule(this)` + register `HijriWidgetForegroundWatcher`.
2. Handle the `hijricalendar://today` deep link on your root activity.
3. (Optional) Build a settings screen using `HijriWidgetConfig`, `widgetOptionsSaver()`, `HijriWidgetRefresher`, and the live-preview composables. Override the widget-info XML in your app to add `android:configure`.

## Underlying Library

Uses [HijrahDateTime](https://github.com/abdulrahman-b0/HijrahDateTime) and [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime).

## License

MIT © 2026 Muhammad Muaz
