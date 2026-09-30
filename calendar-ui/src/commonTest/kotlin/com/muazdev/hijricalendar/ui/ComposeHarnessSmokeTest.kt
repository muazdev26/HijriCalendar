package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import kotlin.test.Test

/**
 * Proves the [runComposeUiTest] harness actually renders this module's composables in
 * `desktopTest` on this machine. If this fails, every other test in the file is unrunnable and the
 * problem is the harness, not the calendar — see UI-02.
 */
@OptIn(ExperimentalTestApi::class)
class ComposeHarnessSmokeTest {

    @Test
    fun composesTheCalendarAndShowsTheMonthName() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Surface {
                    val state = HijriCalendarState(
                        initialMonth = HijrahYearMonth(1447, 9),
                        firstDayOfWeek = WeekDay.SATURDAY,
                    )
                    HijriCalendar(state = state, onDayClick = state.defaultOnDayClick())
                }
            }
        }

        onNodeWithText("Ramadan 1447").assertIsDisplayed()
    }
}
