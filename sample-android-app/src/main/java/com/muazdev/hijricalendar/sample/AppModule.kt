package com.muazdev.hijricalendar.sample

import com.muazdev.hijricalendar.widget.glance.HijriMonthLengthsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // One DataStore per process, shared by the app and its widgets.
    single { HijriMonthLengthsRepository.shared(androidContext()) }
    viewModelOf(::CalendarViewModel)
}
