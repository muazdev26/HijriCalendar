package com.muazdev.hijricalendar.widget.glance

import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.setWidgetPreviews
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
public object HijriWidgetPreviewPublisher {

    /** Fire-and-forget [publishIfDue] for callers without a coroutine scope (receivers, app start). */
    public fun publishIfDueAsync(context: Context) {
        HijriWidgetScope.launch(context, "preview") { publishIfDue(it) }
    }

    /**
     * Generates and publishes real picker previews for every widget class, once per local
     * calendar day. Never throws: preview publishing is best-effort and must not take down a
     * refresh or app-start path.
     *
     * @return true when at least one preview publish ran.
     */
    public suspend fun publishIfDue(context: Context): Boolean {
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
            anySuccess = publish(manager, HijriDualDateWidgetReceiver::class) || anySuccess
        }.onFailure {
            HijriWidgetRefreshLog.e("preview", "publish failed", it)
        }
        if (anySuccess) {
            HijriWidgetConfig.markPreviewsPublishedNow(context, todayEpochDay)
            HijriWidgetRefreshLog.d("preview", "published all previews for epochDay=$todayEpochDay")
        }
        return anySuccess
    }

    /**
     * [GlanceAppWidgetManager.setWidgetPreviews] is API 35+, and this module's `minSdk` is 26.
     *
     * The runtime guard is the `SDK_INT` check at the top of [publishIfDue]; this annotation
     * repeats it so lint can see it. Lint resolves [RequiresApi] per function, so a guard in the
     * caller does not cover a call made from a callee — without this, `lintDebug` failed with
     * `NewApi` even though the code was correctly guarded, and lint had been reporting it only
     * because an unrelated compile failure had been stopping `build` before it ran.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
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
