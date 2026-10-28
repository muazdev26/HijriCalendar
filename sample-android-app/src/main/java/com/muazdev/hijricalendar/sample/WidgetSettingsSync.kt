package com.muazdev.hijricalendar.sample

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.ui.DateDisplayMode
import com.muazdev.hijricalendar.ui.PrimaryDate
import com.muazdev.hijricalendar.widget.glance.GregorianDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriCalendarWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriCompactDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriDualDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriTodayWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriWidgetConfig
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshLog
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefresher
import com.muazdev.hijricalendar.widgetdata.WeekendPattern
import com.muazdev.hijricalendar.widgetdata.WidgetDateDisplayMode
import com.muazdev.hijricalendar.widgetdata.WidgetDatePrimary
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import com.muazdev.hijricalendar.widgetdata.monthLengthsToMap
import kotlinx.coroutines.runBlocking

/**
 * Everything the in-app calendar holds that a placed widget can also be told about.
 *
 * A data class rather than a parameter list because the set has grown twice already and each sync
 * call site had to be edited in step: one value means a new option reaches *every* mirror in the app
 * as soon as it is added to [applyTo]'s copy, and a call site cannot pass the arguments in the wrong
 * order.
 *
 * The in-app enums are carried as-is and translated at the write (`valueOf(name)`), because
 * `calendar-widget-data` cannot depend on `calendar-ui` and both enums exist only to describe how
 * *that* surface draws. Their names agree by design, so the translation is a lookup, not a mapping
 * table that can drift.
 */
internal data class WidgetSettings(
    val adjustmentDays: Int,
    val pakistanDates: Boolean,
    val monthLengths: HijriMonthLengths,
    val weekendPattern: WeekendPattern,
    val showAdjacentDays: Boolean,
    val showCellBorders: Boolean,
    val dateDisplayMode: DateDisplayMode,
    val primaryDate: PrimaryDate,
)

/**
 * Mirrors the in-app calendar's settings onto the placed widgets.
 *
 * Every widget reads its options from one of two places: its own per-widget store, or — when it has
 * never been configured — the family mirror ([HijriWidgetConfig.loadFamily]), which is a single
 * shared record. A sync therefore writes both: the mirror, so stateless widgets and freshly placed
 * widgets open on the app's settings, and each widget's own options, so one that *was* configured
 * follows the app too. That last part is a deliberate override, which is why the caller gates the
 * whole sync behind a user-visible toggle rather than running it unconditionally.
 *
 * Grid-only options (the date-in-cell mode and which of the two figures is primary) are still written
 * to every widget's options. They are ignored by the strip and the tiles, so the write is inert
 * there, and writing them uniformly is what keeps the mirror a faithful copy of the app's state —
 * a per-kind filter here would be a second, silently diverging set of rules.
 */
internal class WidgetSettingsSync(private val context: Context) {

    private val receivers = listOf(
        HijriCalendarWidgetReceiver::class.java,
        HijriTodayWidgetReceiver::class.java,
        HijriDateWidgetReceiver::class.java,
        GregorianDateWidgetReceiver::class.java,
        HijriDualDateWidgetReceiver::class.java,
        HijriCompactDateWidgetReceiver::class.java,
    )

    fun sync(settings: WidgetSettings) {
        runBlocking {
            val overrideTable = monthLengthsToMap(settings.monthLengths)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val gridAppWidgetIds = appWidgetManager?.getAppWidgetIds(ComponentName(context, HijriCalendarWidgetReceiver::class.java)) ?: intArrayOf()
            var synced = 0

            HijriWidgetRefreshLog.d(
                "sync:start",
                "inAppOverrides=${overrideTable.size} adjustmentDays=${settings.adjustmentDays} pakistan=${settings.pakistanDates} " +
                    "weekend=${settings.weekendPattern} adjacent=${settings.showAdjacentDays} borders=${settings.showCellBorders} " +
                    "mode=${settings.dateDisplayMode} primary=${settings.primaryDate} grids=${gridAppWidgetIds.size}",
            )

            val currentFamily = HijriWidgetConfig.loadFamily(context)
            val updatedFamily = settings.applyTo(currentFamily)
            HijriWidgetConfig.saveFamilyOptions(context, updatedFamily, caller = "sync")

            if (appWidgetManager != null) {
                for (receiver in receivers) {
                    val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, receiver))
                    for (appWidgetId in ids) {
                        val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
                            ?: continue
                        runCatching {
                            // Skip widgets with no stored options: they follow the family mirror
                            // (already updated above). Writing them a copy would pin them.
                            val own = HijriWidgetConfig.loadOwn(context, glanceId) ?: return@runCatching
                            HijriWidgetConfig.saveWidgetOptions(
                                context = context,
                                glanceId = glanceId,
                                options = settings.applyTo(own),
                                caller = "sync",
                            )
                            synced++
                        }
                    }
                }
            }

            HijriWidgetRefreshLog.d(
                "sync:end",
                "familyLanguage=${updatedFamily.language} familyOverrides=${updatedFamily.monthLengthOverrides.size} synced=$synced",
            )

            HijriWidgetRefresher.refreshAllAsync(
                context = context,
                reason = "in-app settings",
                bypassGate = true,
                bypassDedupe = true,
            )
            kotlinx.coroutines.delay(250)
        }
    }
}

/**
 * [settings] written onto [options], leaving every option the in-app calendar has no opinion about
 * (language, numerals, week start, pin, theme) exactly as it was.
 *
 * The enum translation is by **name**, never ordinal, and falls back to the option's current value
 * for a name the widget schema does not have: a host on a newer `calendar-ui` must not lose the whole
 * write because one rendering mode has not been mirrored into `calendar-widget-data` yet.
 */
private fun WidgetSettings.applyTo(options: WidgetOptions) = options.copy(
    adjustmentDays = adjustmentDays,
    source = if (pakistanDates) WidgetSource.PAKISTAN else WidgetSource.CALCULATION,
    monthLengthOverrides = monthLengthsToMap(monthLengths),
    weekendPattern = weekendPattern,
    showAdjacentDays = showAdjacentDays,
    showCellBorders = showCellBorders,
    dateDisplayMode = WidgetDateDisplayMode.entries.firstOrNull { it.name == dateDisplayMode.name }
        ?: options.dateDisplayMode,
    datePrimary = WidgetDatePrimary.entries.firstOrNull { it.name == primaryDate.name } ?: options.datePrimary,
)
