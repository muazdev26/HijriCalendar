package com.muazdev.hijricalendar.consumercheck.ui

import androidx.compose.runtime.Composable
import com.muazdev.hijricalendar.ui.DateDisplayMode
import com.muazdev.hijricalendar.ui.HijriCalendarColors
import com.muazdev.hijricalendar.ui.HijriCalendarDefaults
import com.muazdev.hijricalendar.ui.HijriCalendarLabels
import com.muazdev.hijricalendar.ui.PrimaryDate

/**
 * Consumer-resolution gate for `hijri-calendar-compose`.
 *
 * This file exists to be compiled, not run. It references the public API of calendar-ui so that
 * a scope mistake on any transitive dependency is caught here rather than in a consumer's build.
 */
@Suppress("unused")
object UiConsumerCheck {

    @Composable
    fun colorsApi(): HijriCalendarColors = HijriCalendarDefaults.colors()

    // Verify enum types are accessible
    fun dateMode(): DateDisplayMode = DateDisplayMode.HIJRI_ONLY
    fun primary(): PrimaryDate = PrimaryDate.HIJRI

    // Verify labels are accessible
    fun labels(): HijriCalendarLabels = HijriCalendarLabels()
}
