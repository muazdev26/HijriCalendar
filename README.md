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
    implementation("com.github.muazdev26.HijriCalendar:hijri-calendar-compose:1.0.0-alpha02")
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
    <version>1.0.0-alpha02</version>
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
)
```

| Property | Type | Description |
|----------|------|-------------|
| `currentMonth` | `HijrahYearMonth` | The currently displayed month |
| `selectedDate` | `HijrahDate?` | The currently selected date |
| `calendarMonth` | `CalendarMonth` | Computed month grid with all day data |

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
): HijriCalendarState
```

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

Days outside the range will be visually disabled and non-clickable.

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
