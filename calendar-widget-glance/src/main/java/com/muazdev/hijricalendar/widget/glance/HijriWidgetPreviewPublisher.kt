package com.muazdev.hijricalendar.widget.glance

import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.Context
import android.os.Build
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.setWidgetPreviews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Publishes the *generated* widget previews that power the Android 15+ widget picker: instead of
 * the static [android:previewLayout] / [android:previewImage] fallbacks used on Android 12-14 and
 * below, a real remote-views composition of each widget ([GlanceAppWidget.providePreview]) is
 * generated with today's actual Hijri/Gregorian date and the family's current language, numerals
 * and source, and pushed to the system. The picker then shows the widget exactly as it will
 * render (the recommended 15+ pattern; see developer.android.com/develop/ui/views/appwidgets
 * /previews).
 *
 * Previews are (re)published at most once per local calendar day, because the system rate-limits
 * per-provider preview updates (~2/hour). The marker lives in [HijriWidgetConfig], so every entry
 * point — app launch, any successful family refresh and the host's `onUpdate` — is a cheap
 * idempotent check.
 */
object HijriWidgetPreviewPublisher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget [publishIfDue] for callers without a coroutine scope (receivers, app start). */
    fun publishIfDueAsync(context: Context) {
        scope.launch { publishIfDue(context) }
    }

    /**
     * Generates and publishes real picker previews for all four widget classes, once per local
     * calendar day. Never throws: preview publishing is best-effort and must not take down a
     * refresh or app-start path.
     *
     * @return true when at least one preview publish ran.
     */
    suspend fun publishIfDue(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay()
        if (HijriWidgetConfig.lastPreviewPublishedEpochDay(context) >= todayEpochDay) return false

        val manager = GlanceAppWidgetManager(context)
        var anySuccess = false
        runCatching {
            anySuccess = publish(manager, HijriCalendarWidgetReceiver::class) || anySuccess
            anySuccess = publish(manager, HijriTodayWidgetReceiver::class) || anySuccess
            anySuccess = publish(manager, HijriDateWidgetReceiver::class) || anySuccess
            anySuccess = publish(manager, GregorianDateWidgetReceiver::class) || anySuccess
        }.onFailure {
            HijriWidgetRefreshLog.e("preview", "publish failed", it)
        }
        if (anySuccess) {
            HijriWidgetConfig.markPreviewsPublishedNow(context, todayEpochDay)
            HijriWidgetRefreshLog.d("preview", "published all previews for epochDay=$todayEpochDay")
        }
        return anySuccess
    }

    private suspend fun publish(
        manager: GlanceAppWidgetManager,
        receiverClass: kotlin.reflect.KClass<out androidx.glance.appwidget.GlanceAppWidgetReceiver>,
    ): Boolean {
        val result = manager.setWidgetPreviews(
            receiverClass,
            widgetCategories = intSetOf(WIDGET_CATEGORY_HOME_SCREEN),
        )
        when (result) {
            GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS ->
                HijriWidgetRefreshLog.d("preview", "${receiverClass.simpleName}: published")
            else ->
                HijriWidgetRefreshLog.d(
                    "preview",
                    "${receiverClass.simpleName}: rate-limited (retried after next marker day)",
                )
        }
        return result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
    }
}