package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.monthLengthsFrom

/**
 * The month-length table a widget render uses, with the revision that identifies it.
 *
 * Resolution order: the widget's own map if it has one, otherwise the shared DataStore. The
 * process-global table is never read here.
 */
internal class HijriResolvedOverrides(
    val table: HijriMonthLengths,
    /** Changes whenever the table's source changes. Used as part of cache keys. */
    val revision: Long,
    /** True when the widget's own map supplied the table, so the cache key must use the map. */
    val fromWidgetOptions: Boolean,
)

/**
 * Resolve the explicit month-length table for a widget render.
 *
 * If [options] carries its own [WidgetOptions.monthLengthOverrides], use that.
 * Otherwise, load from the shared [HijriMonthLengthsRepository]. The process-global
 * [com.muazdev.hijricalendar.core.HijriMonthOverrides] is never read.
 */
internal suspend fun resolveHijriOverrides(
    context: Context,
    options: WidgetOptions,
): HijriResolvedOverrides {
    if (options.monthLengthOverrides.isNotEmpty()) {
        return HijriResolvedOverrides(
            table = monthLengthsFrom(options.monthLengthOverrides),
            revision = 0L,
            fromWidgetOptions = true,
        )
    }
    val repository = HijriMonthLengthsRepository.shared(context)
    return HijriResolvedOverrides(
        table = repository.loadTable(),
        revision = repository.revision(),
        fromWidgetOptions = false,
    )
}
