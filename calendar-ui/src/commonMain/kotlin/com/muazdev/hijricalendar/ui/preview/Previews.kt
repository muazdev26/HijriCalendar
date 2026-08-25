package com.muazdev.hijricalendar.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.ui.HijriCalendar
import com.muazdev.hijricalendar.ui.HijriCalendarDayCell
import com.muazdev.hijricalendar.ui.HijriCalendarHeader
import com.muazdev.hijricalendar.ui.HijriCalendarLabels
import com.muazdev.hijricalendar.ui.defaultOnDayClick
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun HijriCalendarHeaderPreview() {
    MaterialTheme {
        Surface {
            HijriCalendarHeader(
                monthName = "Ramadan",
                year = 1447,
                onPreviousMonth = {},
                onNextMonth = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview
@Composable
fun HijriCalendarHeaderWithGregorianPreview() {
    MaterialTheme {
        Surface {
            HijriCalendarHeader(
                monthName = "Ramadan",
                year = 1447,
                onPreviousMonth = {},
                onNextMonth = {},
                dateDisplayMode = DateDisplayMode.BOTH,
                gregorianMonthText = "February - March 2026",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview
@Composable
fun HijriCalendarDayCellDefaultPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendarDayCell(
                    day = sampleDay(dayOfMonth = 15),
                    onClick = {},
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarDayCellTodayPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendarDayCell(
                    day = sampleDay(dayOfMonth = 15, isToday = true),
                    onClick = {},
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarDayCellSelectedPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendarDayCell(
                    day = sampleDay(dayOfMonth = 15, isSelected = true),
                    onClick = {},
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarDayCellDisabledPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendarDayCell(
                    day = sampleDay(dayOfMonth = 15, isDisabled = true),
                    onClick = {},
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarDayCellWeekendPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendarDayCell(
                    day = sampleDay(dayOfMonth = 15, isWeekend = true),
                    onClick = {},
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarDayCellArabicIndicPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendarDayCell(
                    day = sampleDay(dayOfMonth = 25),
                    onClick = {},
                    useArabicIndicNumerals = true,
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarDayCellBothDatesPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendarDayCell(
                    day = sampleDay(dayOfMonth = 15),
                    onClick = {},
                    dateDisplayMode = DateDisplayMode.BOTH,
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarDayCellGregorianOnlyPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendarDayCell(
                    day = sampleDay(dayOfMonth = 15),
                    onClick = {},
                    dateDisplayMode = DateDisplayMode.GREGORIAN_ONLY,
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarPreview() {
    val state = HijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
        firstDayOfWeek = WeekDay.SATURDAY,
    )
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendar(
                    state = state,
                    onDayClick = state.defaultOnDayClick(),
                )
            }
        }
    }
}

@Preview
@Composable
fun HijriCalendarWithBothDatesPreview() {
    val state = HijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
        firstDayOfWeek = WeekDay.SATURDAY,
    )
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendar(
                    state = state,
                    onDayClick = state.defaultOnDayClick(),
                    dateDisplayMode = DateDisplayMode.BOTH,
                )
            }
        }
    }
}

private val ConsumerDarkColors = darkColorScheme(
    primary = Color(0xFF4FB286),
    onPrimary = Color.White,
    surface = Color(0xFF1C2B2A),
    onSurface = Color(0xFFE8EFEA),
    onSurfaceVariant = Color(0xFFA9BDB3),
    error = Color(0xFFE57373),
)

@Preview
@Composable
fun HijriCalendarConsumerRtlDarkGlassyPreview() {
    val state = HijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
        firstDayOfWeek = WeekDay.SATURDAY,
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = ConsumerDarkColors) {
            Surface(color = Color(0xFF101D1C)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.06f),
                    ),
                ) {
                    HijriCalendar(
                        state = state,
                        modifier = Modifier.padding(12.dp),
                        dateDisplayMode = DateDisplayMode.BOTH,
                        useArabicIndicNumerals = true,
                        onDayClick = state.defaultOnDayClick(),
                    )
                }
            }
        }
    }
}

@Preview(fontScale = 1.5f)
@Composable
fun HijriCalendarBothDatesFontScale150Preview() {
    val state = HijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
        firstDayOfWeek = WeekDay.SATURDAY,
    )
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                HijriCalendar(
                    state = state,
                    onDayClick = state.defaultOnDayClick(),
                    dateDisplayMode = DateDisplayMode.BOTH,
                )
            }
        }
    }
}

private val UrduHijriMonths = listOf(
    "محرم", "صفر", "ربیع الاول", "ربیع الثانی",
    "جمادی الاول", "جمادی الثانی", "رجب", "شعبان",
    "رمضان", "شوال", "ذی القعدہ", "ذی الحجہ",
)

private val UrduGregorianMonths = listOf(
    "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
    "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر",
)

private val UrduWeekdayNames = mapOf(
    WeekDay.SATURDAY to "ہفتہ",
    WeekDay.SUNDAY to "اتوار",
    WeekDay.MONDAY to "پیر",
    WeekDay.TUESDAY to "منگل",
    WeekDay.WEDNESDAY to "بدھ",
    WeekDay.THURSDAY to "جمعرات",
    WeekDay.FRIDAY to "جمعہ",
)

@Preview
@Composable
fun HijriCalendarUrduRtlPreview() {
    val state = HijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
        firstDayOfWeek = WeekDay.SATURDAY,
    )
    val urduLabels = HijriCalendarLabels(
        hijriMonthName = { _, month -> UrduHijriMonths[month - 1] },
        gregorianMonthName = { month -> UrduGregorianMonths[month - 1] },
        weekdayShortName = { weekDay -> UrduWeekdayNames.getValue(weekDay) },
        previousMonthContentDescription = "پچھلا مہینہ",
        nextMonthContentDescription = "اگلا مہینہ",
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            Surface {
                Column(modifier = Modifier.padding(16.dp)) {
                    HijriCalendar(
                        state = state,
                        dateDisplayMode = DateDisplayMode.BOTH,
                        useArabicIndicNumerals = true,
                        labels = urduLabels,
                        onDayClick = state.defaultOnDayClick(),
                    )
                }
            }
        }
    }
}
