package com.muazdev.hijricalendar.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.ui.HijriCalendar
import com.muazdev.hijricalendar.ui.HijriCalendarDayCell
import com.muazdev.hijricalendar.ui.HijriCalendarHeader
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
