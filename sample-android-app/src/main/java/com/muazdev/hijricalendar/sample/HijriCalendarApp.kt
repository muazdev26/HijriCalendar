package com.muazdev.hijricalendar.sample

import android.app.Application
import com.muazdev.hijricalendar.sample.widget.HijriWidgetRefreshScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class HijriCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@HijriCalendarApp)
            modules(appModule)
        }

        // Central home of widget refresh scheduling. This is the single re-arm point for the
        // exact midnight alarm (also reached on boot via BOOT_COMPLETED, which starts this
        // process) and schedules the daily WorkManager backstop. The midnight receiver itself
        // never re-arms.
        HijriWidgetRefreshScheduler.schedule(this)
    }
}