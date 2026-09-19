package com.muazdev.hijricalendar.sample.widget

import android.content.Context
import androidx.core.content.edit
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.widgetdata.NumeralStyle

/**
 * Per-widget configuration persisted in [SharedPreferences], keyed by the Glance id. Also
 * stores the global "last day we refreshed" marker used to catch up after boot/time changes.
 */
internal object HijriWidgetConfig {

    private const val PREFS_NAME = "hijri_widget_config"
    private const val RUNTIME_PREFS = "hijri_widget_runtime"

    private const val KEY_ADJUSTMENT_DAYS = "adjustment_days"
    private const val KEY_NUMERAL_STYLE = "numeral_style"
    private const val KEY_FIRST_DAY = "first_day_of_week"
    private const val KEY_PINNED_YEAR = "pinned_year"
    private const val KEY_PINNED_MONTH = "pinned_month"

    private const val KEY_LAST_UPDATE_EPOCH_DAY = "last_update_epoch_day"

    data class WidgetOptions(
        val adjustmentDays: Int,
        val numeralStyle: NumeralStyle,
        val firstDayOfWeekIndex: Int,
        val pinnedYear: Int?,
        val pinnedMonth: Int?,
    )

    /**
     * The widget is shown unless the app explicitly disables it; -1 is the "not pinned"
     * sentinel for both year and month.
     */
    private const val NOT_PINNED = -1

    fun load(context: Context, glanceId: String): WidgetOptions {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetOptions(
            adjustmentDays = prefs.getInt("$KEY_ADJUSTMENT_DAYS$glanceId", 0),
            numeralStyle = NumeralStyle.entries.getOrElse(
                prefs.getInt("$KEY_NUMERAL_STYLE$glanceId", NumeralStyle.WESTERN.ordinal)
            ) { NumeralStyle.WESTERN },
            firstDayOfWeekIndex = prefs
                .getInt("$KEY_FIRST_DAY$glanceId", WeekDay.DEFAULT_FIRST_DAY.index)
                .coerceIn(0, 6),
            pinnedYear = prefs.getInt("$KEY_PINNED_YEAR$glanceId", NOT_PINNED).let { if (it == NOT_PINNED) null else it },
            pinnedMonth = prefs.getInt("$KEY_PINNED_MONTH$glanceId", NOT_PINNED).let { if (it == NOT_PINNED) null else it },
        )
    }

    fun save(context: Context, glanceId: String, options: WidgetOptions) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt("$KEY_ADJUSTMENT_DAYS$glanceId", options.adjustmentDays)
            putInt("$KEY_NUMERAL_STYLE$glanceId", options.numeralStyle.ordinal)
            putInt("$KEY_FIRST_DAY$glanceId", options.firstDayOfWeekIndex)
            putInt("$KEY_PINNED_YEAR$glanceId", options.pinnedYear ?: NOT_PINNED)
            putInt("$KEY_PINNED_MONTH$glanceId", options.pinnedMonth ?: NOT_PINNED)
        }
    }

    /** Marks the widget family as freshly updated for the current local calendar day. */
    fun markUpdatedNow(context: Context, epochDay: Long) {
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_UPDATE_EPOCH_DAY, epochDay)
        }
    }

    /** True when the widget family already reflects the given local calendar day. */
    fun isFreshFor(context: Context, epochDay: Long): Boolean {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_EPOCH_DAY, Long.MIN_VALUE) >= epochDay
    }
}