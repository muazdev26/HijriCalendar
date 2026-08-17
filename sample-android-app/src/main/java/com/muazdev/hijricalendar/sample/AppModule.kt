package com.muazdev.hijricalendar.sample

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::CalendarViewModel)
}
