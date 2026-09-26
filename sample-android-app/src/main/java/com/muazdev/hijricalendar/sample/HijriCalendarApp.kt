package com.muazdev.hijricalendar.sample

import android.app.Application
import com.muazdev.hijricalendar.widget.glance.HijriWidgetForegroundWatcher
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshScheduler
import com.muazdev.hijricalendar.widget.glance.PakistanWarmUp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class HijriCalendarApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@HijriCalendarApp)
            modules(appModule)
        }

        // Central home of widget refresh scheduling. This is the single re-arm point for the
        // exact midnight alarm (also reached on boot via BOOT_COMPLETED, which starts this
        // process) and schedules the daily WorkManager backstop.
        HijriWidgetRefreshScheduler.schedule(this)

        // Warm up the heavy Pakistan century calendar calculations asynchronously using Kotlin Coroutines
        // on the standard Dispatchers.Default pool so on-widget next/prev clicks execute instantly.
        // PakistanWarmUp is single-flight + joinable: an ActionCallback or render that beats this
        // coroutine to the cold table suspends on the same build instead of starting a second one.
        appScope.launch {
            PakistanWarmUp.ensureWarm()
        }

        // Tracks real foreground state across every activity and triggers the catch-up render
        // when the last one is stopped, so a refresh skipped while the app was visible is not
        // lost until the next midnight.
        registerActivityLifecycleCallbacks(HijriWidgetForegroundWatcher(this))
    }
}