package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import java.io.IOException

/**
 * Seeds the process-global override table from the durable DataStore.
 *
 * A process woken by an alarm (or one that never ran the app this session) has an empty in-memory
 * table, so the grid would render the calculated calendar even though the user forced 30-day
 * months in settings. The app and its widgets are one package and share one DataStore file, so
 * reading it here — at the top of every render — gives both the same view of the overrides.
 *
 * Idempotent and best-effort: a read failure keeps the current (possibly empty) table and the
 * render proceeds.
 */
internal object HijriMonthOverridesSeed {

    @Suppress("DEPRECATION")
    suspend fun seed(context: Context) {
        try {
            HijriMonthOverrides.replaceAll(HijriMonthLengthsRepository.shared(context).load())
        } catch (e: IOException) {
            HijriWidgetRefreshLog.e("month-overrides", "seed from DataStore failed; using in-memory table", e)
        }
    }
}
