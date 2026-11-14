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

Distributed via Maven Central.

```kotlin
dependencies {
    implementation("com.muazdev.hijricalendar:hijri-calendar-compose:2.5.0")
}
```

### Legacy: JitPack (2.4.x only)

JitPack will not receive releases after 2.4.x.

```kotlin
repositories { maven("https://jitpack.io") }
dependencies {
    implementation("com.github.muazdev26.HijriCalendar:hijri-calendar-compose:2.5.0")
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
    primaryDate: PrimaryDate = PrimaryDate.HIJRI,
    showEventBanner: Boolean = true,
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

## Pakistan (Ruet-e-Hilal) mode

`HijriCalendarState(..., pakistanDates = true)` (and `toCalendarMonth(pakistan = true)`) switches from the Umm al-Qura calculation to the Pakistan moon-sighting calendar. Months with an official Ruet-e-Hilal fix are pinned to the announced start and length; every month after the last verified fix falls back to Umm al-Qura as a proxy, so the further you look past the end of the verified table, the more Pakistany dates can drift by a day. The library ships the verified window in `calendar-core` (`pakistan-fixes.json`, a resource) and a `PakistanCalendarFreshnessTest` that fails when the table is more than 12 months old, so the proxy is never extended indefinitely by silence — if that test fails, re-check the Central Ruet-e-Hilal Committee's announcements and update `pakistan-fixes.json` rather than widening the test.

## Home-Screen Widgets

A shared projection module (`calendar-widget-data`, `WidgetCalendar` framework on iOS) computes each month grid + "today" projection, keeping both platforms in sync.

**Android (Glance):** six widgets ship in the library — `HijriCalendarWidget` (resizable month grid), `HijriTodayWidget` (today strip), three 1x1 tiles `HijriDateWidget`/`GregorianDateWidget`/`HijriDualDateWidget`, and `HijriCompactDateWidget` (compact dual-date tile). Month grid has today's highlighted cell, weekend tint, faded out-of-month cells, on-widget month navigation, two-line Hijri/Gregorian header. Today's cell is a filled accent highlight; tapping clears it back to today. Today strip lays Gregorian + Hijri dates side by side under a weekday band. Tiles show day + month, or both dates at once (dual). Reading direction follows each widget's own language (Urdu RTL or English), not the device locale. Bundled Noto Nastaliq Urdu face is opt-in via `HijriWidgetFonts(bundledUrduFont = true)` — rasterised in-app, handed to the launcher as a bitmap. Settings (`adjustmentDays`, numeral style, first day of week, language, month names, source, pinned month for grid, bundled font, manual refresh) apply immediately with no Save step and refresh the whole family. Live previews via `HijriWidgetLivePreview` etc. Midnight + `TIME_CHANGED`/`TIMEZONE_CHANGED`/`LOCALE_CHANGED`/`BOOT_COMPLETED` refresh with a WorkManager 24h backstop; background renders skipped while foregrounded. Deep-link on tap to `hijricalendar://today`. Android 15+ uses `providePreview`; 12–14 use static `previewLayout` XMLs.

**iOS (WidgetKit, 17+):** `systemSmall`/`Medium`/`Large`, one timeline entry per day, deep-links to `hijricalendar://today`.

### Adding the widget to your app

```kotlin
dependencies { implementation("com.muazdev.hijricalendar:hijri-calendar-widget-glance:2.5.0") }
```

The library's manifest declares all six receivers; widgets work on defaults. Optional setup:

1. In `Application.onCreate`: `HijriWidgetRefreshScheduler.schedule(this)` + register `HijriWidgetForegroundWatcher`.
2. Handle the `hijricalendar://today` deep link on your root activity.
3. (Optional) Build a settings screen using `HijriWidgetConfig`, `widgetOptionsSaver()`, `HijriWidgetRefresher`, and the live-preview composables. Override the widget-info XML in your app to add `android:configure`.

### Manifest permissions

The library's own manifest declares what the widget refresh needs, so a consumer does not have to add it:

- `RECEIVE_BOOT_COMPLETED` — re-arms the midnight rollover after a reboot.
- `USE_EXACT_ALARM` (Android 13+) — auto-granted and not user-revocable.
- `SCHEDULE_EXACT_ALARM` (Android 12+) — a special app access. Android 12 pre-grants it; **from Android 14 it is not pre-granted to most apps**, and the user can revoke it at any time under *Settings → Special app access → Alarms & reminders*.

`HijriWidgetRefreshScheduler` never assumes the access. It asks `canScheduleExactAlarms()` and arms `setExactAndAllowWhileIdle` when granted, `setAndAllowWhileIdle` otherwise — so the widget still rolls over, just at the platform's next opportunity instead of exactly at 00:00. Both the alarm and the daily WorkManager backstop exist only while at least one widget is placed.

### Google Play: exact alarm permission

The library declares `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` through its manifest; the host app's merged manifest will include them. If the host app is not an alarm or calendar app, declare the removal in the host manifest:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.USE_EXACT_ALARM"
        tools:node="remove" />
</manifest>
```

See the [official Play policy on exact alarms](https://developer.android.com/about/versions/13/changes/user-exact-alarms) for requirements.

## Requirements

- Kotlin 2.4.10+
- Compose Multiplatform 1.11.0+
- AGP 9.4.0+ (Android consumers)
- minSdk 26 (Android)
- iOS 17+ (WidgetKit)

## Underlying Library

Uses [HijrahDateTime](https://github.com/abdulrahman-b0/HijrahDateTime) and [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime).

## License

MIT © 2026 Muhammad Muaz
