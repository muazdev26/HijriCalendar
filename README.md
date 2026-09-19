# HijriCalendar

A Compose Multiplatform calendar component for displaying and interacting with Hijri (Islamic) calendar dates.

## Features

- Full month grid view with 6-week layout
- Month navigation (previous/next)
- Date selection with visual feedback
- Arabic-Indic numeral display
- RTL layout support
- Customizable theming (colors, today indicator, weekend styling)
- Configurable first day of week (default: Saturday)
- Animated month transitions
- Accessibility support (content descriptions, roles)
- Works on Android, iOS, and Desktop (JVM)

## Platform Support

| Platform | Status |
|----------|--------|
| Android  | Supported (minSdk 26) |
| iOS      | Supported (arm64, simulatorArm64) |
| Desktop  | Supported (JVM 11+) |

## Installation

The library is distributed via [JitPack](https://jitpack.io/#muazdev26/HijriCalendar).

### Gradle

Add the JitPack repository and the dependency to your module's `build.gradle.kts`:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.muazdev26.HijriCalendar:hijri-calendar-compose:1.0.0-alpha05")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.muazdev26.HijriCalendar</groupId>
    <artifactId>hijri-calendar-compose</artifactId>
    <version>1.0.0-alpha05</version>
</dependency>
```

## Quick Start

```kotlin
import androidx.compose.runtime.Composable
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.ui.HijriCalendar
import com.muazdev.hijricalendar.ui.rememberHijriCalendarState

@Composable
fun MyScreen() {
    val state = rememberHijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9), // Ramadan 1447
    )

    HijriCalendar(
        state = state,
        onDayClick = state.defaultOnDayClick(),
    )
}
```

## API Reference

### `HijriCalendar`

The top-level composable that renders the full calendar.

```kotlin
@Composable
fun HijriCalendar(
    state: HijriCalendarState,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    onDayClick: (CalendarDay) -> Unit,
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `state` | `HijriCalendarState` | The calendar state holder (required) |
| `modifier` | `Modifier` | Optional modifier for the calendar |
| `colors` | `HijriCalendarColors` | Custom theme colors |
| `useArabicIndicNumerals` | `Boolean` | Display day numbers in Arabic-Indic numerals |
| `onDayClick` | `(CalendarDay) -> Unit` | Callback when a day is clicked |
| `dayContent` | `(@Composable (CalendarDay) -> Unit)?` | Custom content for each day cell |

### `HijriCalendarState`

Holds the calendar's current state (selected month, selected date).

```kotlin
@Stable
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

| Property | Type | Description |
|----------|------|-------------|
| `currentMonth` | `HijrahYearMonth` | The currently displayed month |
| `selectedDate` | `HijrahDate?` | The currently selected date (adjusted, see below) |
| `calendarMonth` | `CalendarMonth` | Computed month grid with all day data |
| `adjustmentDays` | `Int` | Moon-sighting adjustment applied to the whole grid |
| `weekendDays` | `Set<WeekDay>` | Which weekdays render with weekend styling (default: Fri/Sat) |
| `canGoToPreviousMonth` | `Boolean` | Whether the previous month is within `minDate`/`maxDate` |
| `canGoToNextMonth` | `Boolean` | Whether the next month is within `minDate`/`maxDate` |

| Method | Description |
|--------|-------------|
| `goToNextMonth()` | Navigate to the next month |
| `goToPreviousMonth()` | Navigate to the previous month |
| `selectDate(date)` | Select a specific date |
| `goToMonth(yearMonth)` | Jump to a specific month |
| `goToToday()` | Navigate to and select today's date |

### `rememberHijriCalendarState`

A composable helper to create and remember a `HijriCalendarState`. It lives in `com.muazdev.hijricalendar.core` and is re-exported from `com.muazdev.hijricalendar.ui`, so you can import it from either package:

```kotlin
import com.muazdev.hijricalendar.ui.rememberHijriCalendarState
```

```kotlin
@Composable
fun rememberHijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.SATURDAY,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    weekendDays: Set<WeekDay> = setOf(WeekDay.FRIDAY, WeekDay.SATURDAY),
): HijriCalendarState
```

There is also a `rememberSaveableHijriCalendarState(...)` with the same signature that stores
the current month and selection across configuration changes and process death — see
[Persistence](#persistence) below.

## Persistence

`HijriCalendarState` is a plain state holder: it does **not** persist `currentMonth` /
`selectedDate` across process death on its own. Unless you hoist persistence, the calendar
silently resets to the `initialMonth`/`initialSelectedDate` you pass in when the process is
recreated.

The simplest way to keep the selection across configuration changes (rotation) and Android
process death is the built-in saveable variant:

```kotlin
import androidx.compose.runtime.Composable
import com.muazdev.hijricalendar.ui.HijriCalendar
import com.muazdev.hijricalendar.ui.rememberSaveableHijriCalendarState

@Composable
fun MyScreen() {
    val state = rememberSaveableHijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
    )

    HijriCalendar(
        state = state,
        onDayClick = state.defaultOnDayClick(),
    )
}
```

`rememberSaveableHijriCalendarState` saves the current month and selected date as a small list
of integers (via the `androidx.compose.runtime.saveable.Saver` machinery). The immutable range /
weekend configuration (first day of week, `minDate`/`maxDate`, `adjustmentDays`, `weekendDays`)
is captured at first composition and reapplied on restore.

### Hoisting into a ViewModel / `SavedStateHandle`

If you prefer to own persistence yourself (e.g. the state lives inside a `ViewModel`), mirror the
pattern used by the sample app: keep a `SavedStateHandle` in sync with the state's snapshots.

```kotlin
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class CalendarViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val state: HijriCalendarState = HijriCalendarState(
        initialMonth = restoreMonth() ?: todayHijriMonth(),
        initialSelectedDate = restoreSelectedDate() ?: todayHijriDate(),
    )

    init {
        viewModelScope.launch {
            snapshotFlow { state.currentMonth }
                .distinctUntilChanged()
                .collect { month ->
                    savedStateHandle["current_month_year"] = month.year.toLong()
                    savedStateHandle["current_month_value"] = month.month.number.toLong()
                }
        }
        // ... and the selected year / month / day keys.
    }
}
```

Note: when you hoist the state into a `ViewModel`, the `HorizontalPager` inside the grid is
itself `rememberSaveable`-backed; the grid intentionally drops the pager's first restored page so
the pager cannot overwrite the (correct) ViewModel state after a configuration change.

`CalendarDay` is marked `@Serializable`, so if you persist day-level data of your own
(e.g. an annotations map keyed by day), the model exposes a ready-made serializer for it.

## Customization

### Theming

```kotlin
val customColors = HijriCalendarDefaults.colors(
    selectedDayContainerColor = Color(0xFF1976D2),
    selectedDayContentColor = Color.White,
    todayBorderColor = Color(0xFFD32F2F),
)

HijriCalendar(
    state = state,
    colors = customColors,
    onDayClick = state.defaultOnDayClick(),
)
```

### First Day of Week

```kotlin
val state = rememberHijriCalendarState(
    initialMonth = HijrahYearMonth(1447, 9),
    firstDayOfWeek = WeekDay.SUNDAY, // Western convention
)
```

### RTL Support

The calendar automatically adapts to RTL layout direction. Arrow icons use `AutoMirrored` variants. Set `layoutDirection = LayoutDirection.Rtl` in your `CompositionLocalProvider` for RTL layouts.

### Arabic-Indic Numerals

```kotlin
HijriCalendar(
    state = state,
    useArabicIndicNumerals = true,
    onDayClick = state.defaultOnDayClick(),
)
```

### Custom Day Content

```kotlin
HijriCalendar(
    state = state,
    onDayClick = state.defaultOnDayClick(),
    dayContent = { day ->
        if (day.dayOfMonth == 1) {
            Text("1", color = MaterialTheme.colorScheme.primary)
        } else {
            Text(day.dayOfMonth.toString())
        }
    },
)
```

## Date Range Limiting

Restrict selectable dates with `minDate` and `maxDate`:

```kotlin
val state = rememberHijriCalendarState(
    initialMonth = HijrahYearMonth(1447, 9),
    minDate = HijrahDate(1447, 1, 1),
    maxDate = HijrahDate(1447, 12, 30),
)
```

Days outside the range will be visually disabled and non-clickable, and month navigation is
bounded by the range: the header's previous/next buttons disable once the adjacent month no
longer contains any in-range day, and swiping the pager snaps back to the boundary month instead
of paging past it.

## Date Display Modes & Cell Sizing

`dateDisplayMode` controls what each cell shows: `HIJRI_ONLY` (default), `GREGORIAN_ONLY`,
or `BOTH` (Hijri number stacked over the Gregorian day). Day cells always use one fixed
size (`48.dp` by default, override with `dayCellSize`) — switching to `BOTH` does **not**
grow the grid. The two date lines pack tightly into the same cell with minimal vertical
padding between cells, matching Google Calendar's dense layout.

The calendar renders with a simple default font at its designed size. System-level font
scaling (accessibility `fontScale`) is intentionally ignored so cells never inflate or
shrink the grid; the `dayCellSize` you pass is the size you get on every display mode and
every device.

## Localization (`HijriCalendarLabels`)

All header and weekday text can be localized via `labels`. Defaults reproduce the
built-in English output, so passing nothing changes nothing:

```kotlin
val urduLabels = HijriCalendarLabels(
    hijriMonthName = { _, month ->
        listOf(
            "محرم", "صفر", "ربیع الاول", "ربیع الثانی", "جمادی الاول", "جمادی الثانی",
            "رجب", "شعبان", "رمضان", "شوال", "ذی القعدہ", "ذی الحجہ",
        )[month - 1]
    },
    gregorianMonthName = { month ->
        listOf("جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
               "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر")[month - 1]
    },
)

HijriCalendar(
    state = state,
    labels = urduLabels,
    onDayClick = state.defaultOnDayClick(),
)
```

`HijriCalendarLabels` also exposes `weekdayShortName`, `previousMonthContentDescription`,
`nextMonthContentDescription`, and `dayContentDescription` for accessibility strings:

```kotlin
val localizedLabels = HijriCalendarLabels(
    weekdayShortName = { it.shortName },
    previousMonthContentDescription = "پچھلا مہینہ",
    nextMonthContentDescription = "اگلا مہینہ",
    dayContentDescription = { day ->
        // Disambiguate leading/trailing cells from adjacent months for screen readers.
        val prefix = if (day.isCurrentMonth) "" else "خارجی "
        val suffix = if (day.isDisabled) ", غیر فعال" else ""
        "${prefix}دن ${day.dayOfMonth}$suffix"
    },
)
```

The default `dayContentDescription` reproduces the built-in `"Day N"` / `"Day N, disabled"`
labels; supply your own lambda to localize or to disambiguate repeated day numbers from
adjacent months.

## Weekends

The days styled with `weekendDayContentColor` default to Friday + Saturday (common across the
Gulf and Levant). Change them per-region by passing `weekendDays`:

```kotlin
val state = rememberHijriCalendarState(
    initialMonth = HijrahYearMonth(1447, 9),
    weekendDays = setOf(WeekDay.FRIDAY), // Friday-only weekend
)
```

## Moon Sighting Adjustment (`adjustmentDays`)

Locally observed Hijri dates often differ from the Umm al-Qura calculation by ±1 day
depending on regional moon sighting. Pass `adjustmentDays` to compensate — the entire
calendar shifts, not just labels:

```kotlin
// Wire it to a user setting of -1 / 0 / +1
var adjustmentDays by rememberSaveable { mutableIntStateOf(0) }

val state = rememberHijriCalendarState(
    initialMonth = HijrahYearMonth(1447, 9),
    adjustmentDays = adjustmentDays,
)

HijriCalendar(
    state = state,
    onDayClick = state.defaultOnDayClick(),
)
```

Semantics:

- The observed Hijri date of a Gregorian day is the Umm al-Qura conversion of
  `(gregorianDate + adjustmentDays)`. With `+1`, a locally-observed community is one day
  ahead: the Gregorian day Umm al-Qura calls *Ramadan 30* displays *Shawwal 1*.
- The shift is applied where cells are generated, so everything stays consistent:
  day numbers, weekday column alignment, `isToday`, selection, and month boundaries.
- `state.selectedDate` (and each `CalendarDay.hijrahDate`) is the **adjusted** date —
  display its `.day` directly; no manual correction needed.
- `minDate`/`maxDate` are compared in adjusted space too.
- Dates pushed outside the supported Umm al-Qura range (~1300–1600 AH) are rendered as
  disabled placeholder cells clamped to the range boundary instead of crashing.

## Home-Screen Widgets

The sample apps ship home-screen widgets backed by a shared, render-ready projection module
(`calendar-widget-data`, published as the `WidgetCalendar` framework on iOS). It computes
each Hijri month 6x7 grid plus a compact "today" projection without any native rendering
code, so both platforms stay in sync on layout math, day accuracy, and the moon-sighting
`adjustmentDays` convention.

Android (Glance):

- Month grid widget with today highlight and a small "today" card
- Long-press configuration: `adjustmentDays` (moon sighting, -2..+2), numeral style
  (Western / Arabic-Indic), first day of week, and an optional pinned Hijri year/month
- Midnight + `TIME_CHANGED`/`TIMEZONE_CHANGED`/`BOOT_COMPLETED` refresh with a WorkManager
  24h backstop; tapping today's cell deep-links the app to the current month

iOS (WidgetKit, iOS 17+):

- `systemSmall` (today card), `systemMedium` (4-row grid) and `systemLarge` (full grid)
- One timeline entry per Hijri month day; anchor epochs are computed with a Gregorian
  calendar in the user's timezone (never by dividing time by 86400)
- Tap-through deep-links to `hijricalendar://today`

The demo configuration screens are in `sample-android-app` and the `HijriWidgetExtension`
target inside `iosApp/iosApp.xcodeproj`.

## Underlying Library

This library uses [HijrahDateTime](https://github.com/abdulrahman-b0/HijrahDateTime) for Hijri date calculations and [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) for date/time operations.

## License

```
MIT License

Copyright (c) 2026 Muhammad Muaz

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
