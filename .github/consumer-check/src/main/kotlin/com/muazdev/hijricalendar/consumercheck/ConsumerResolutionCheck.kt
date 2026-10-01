package com.muazdev.hijricalendar.consumercheck

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.SizeMode
import com.muazdev.hijricalendar.widget.glance.HIJRI_DEEP_LINK_TODAY
import com.muazdev.hijricalendar.widget.glance.GregorianDateWidget
import com.muazdev.hijricalendar.widget.glance.GregorianDateWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.GregorianDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriCalendarWidget
import com.muazdev.hijricalendar.widget.glance.HijriCalendarWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriDateWidget
import com.muazdev.hijricalendar.widget.glance.HijriDateWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriTodayWidget
import com.muazdev.hijricalendar.widget.glance.HijriTodayWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriTodayWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriWidgetConfig
import com.muazdev.hijricalendar.widget.glance.HijriWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriWidgetPreviewPublisher
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefresher
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshScheduler
import com.muazdev.hijricalendar.widget.glance.PakistanWarmUp
import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.HijriYearMonth
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetOptionsJson
import com.muazdev.hijricalendar.widgetdata.WeekStart
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import com.muazdev.hijricalendar.widgetdata.buildHijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.createWidgetOptions
import com.muazdev.hijricalendar.widgetdata.monthLengthKey
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData

/**
 * The consumer-resolution gate.
 *
 * Nothing here runs. It exists to be *compiled*, by a build whose only library dependency is
 * `com.muazdev.hijricalendar:hijri-calendar-widget-glance`, resolved from a repository containing
 * nothing but this repo's published artifacts. Every declaration therefore resolves only if the
 * published POMs put the artifact it comes from on the **compile** classpath.
 *
 * The class it caught: `calendar-widget-glance` declared `androidx.compose.ui` as
 * `implementation`, which publishes at *runtime* scope, while four `public` composables took
 * `Modifier` and `DpSize`. A consumer adding the dependency and calling the documented integration
 * path got `cannot access class 'androidx.compose.ui.Modifier'`. Nothing in the repository could
 * see it — the sample app has its own Compose dependencies, so its classpath was complete and CI
 * was green. See docs/widgets/glance/WG-01-compose-runtime-scope.md.
 *
 * **Adding to this file is the point.** When a public API gains a parameter or return type from a
 * new dependency, add a line that names it here. If the library declares that dependency
 * `implementation`, this build stops compiling — which is the failure the gate exists to catch.
 */
@Suppress("unused", "FunctionName")
private object ConsumerResolutionCheck {

    /**
     * The headline integration path from AGENTS.md: a settings screen showing a live preview of the
     * widget it is configuring. `DpSize` and `Modifier` come from `androidx.compose.ui`, which is
     * exactly what the original defect withheld from a consumer's compile classpath.
     */
    @Composable
    fun settingsScreenLivePreview(options: WidgetOptions) {
        HijriWidgetLivePreview(
            options = options,
            viewedMonth = null,
            size = DpSize(width = 260.dp, height = 280.dp),
            modifier = Modifier,
        )
        HijriTodayWidgetLivePreview(options = options, size = DpSize(144.dp, 40.dp))
        HijriDateWidgetLivePreview(options = options, size = DpSize(40.dp, 40.dp))
        GregorianDateWidgetLivePreview(options = options, size = DpSize(40.dp, 40.dp))
    }

    /** A compose-typed default argument, the shape a real settings screen uses. */
    @Composable
    fun previewWithDefaultModifier(options: WidgetOptions) {
        HijriWidgetLivePreview(options = options, viewedMonth = null, size = DpSize(260.dp, 280.dp))
    }

    /** The four widget classes and their receivers, plus `SizeMode` from `glance-appwidget`. */
    fun widgetTypesAndSizes(): List<Any> = listOf(
        HijriCalendarWidget(),
        HijriTodayWidget(),
        HijriDateWidget(),
        GregorianDateWidget(),
        HijriCalendarWidgetReceiver(),
        HijriTodayWidgetReceiver(),
        HijriDateWidgetReceiver(),
        GregorianDateWidgetReceiver(),
        HijriCalendarWidget().sizeMode,
        SizeMode.Single,
        HIJRI_DEEP_LINK_TODAY,
    )

    /** `GlanceId` in the public config API — the reason `glance-appwidget` is already `api`. */
    suspend fun publicConfigApi(context: android.content.Context, glanceId: GlanceId) {
        val options: WidgetOptions = HijriWidgetConfig.load(context, glanceId)
        HijriWidgetConfig.save(context, glanceId, options)
        val saver = HijriWidgetConfig.widgetOptionsSaver()

        // `WidgetOptions` members, including the ones added by WD-01.
        val pinned: Boolean = options.isPinned
        val weekStart: WeekStart = options.effectiveWeekStart
        val month: Int = options.firstDayOfWeekIndexValue
        val table = options.overridesTable()
        val key: String = monthLengthKey(1448, 3)
        val withOverride = options.copy(monthLengthOverrides = mapOf(key to 30))
        val resolved = options.resolveGridMonth(today = HijriYearMonth(1447, 1))
        // WD-07: the nullable overload is what lets a renderer say "I do not know today" without
        // inventing a fallback month.
        val maybeResolved = options.resolveGridMonthOrNull(today = null)
        val label = options.hijriMonthName(9)

        // `calendar-widget-data`'s wire format, which must arrive transitively.
        val encoded: String = WidgetOptionsJson.encode(options)
        val decoded: WidgetOptions = WidgetOptionsJson.decode(encoded)
        val built: WidgetOptions = createWidgetOptions(
            language = WidgetLanguage.URDU,
            source = WidgetSource.CALCULATION,
            pinsMonth = true,
            pinnedYear = 1447,
            pinnedMonth = 9,
            overridesCsv = "1448-3:30",
        )

        // The projections, including the `overrides` parameter WD-01 added.
        val grid: HijriMonthWidgetData? = buildHijriMonthWidgetData(
            hijriYear = 1447,
            hijriMonth = 9,
            options = built,
        )
        val flatGrid = buildHijriMonthWidgetData(
            hijriYear = 1447,
            hijriMonth = 9,
            adjustmentDays = 0,
            overrides = table,
        )
        val today: TodayHijriWidgetData? = todayHijriWidgetData(
            anchorEpochDay = 20_000L,
            options = built,
        )
    }

    /** The refresh/scheduling surface a host app drives from its own settings screen. */
    suspend fun refreshApi(context: android.content.Context) {
        HijriWidgetRefresher.refreshAllAsync(context, reason = "settings")
        HijriWidgetRefresher.refreshInstanceAsync(context, glanceId = null, reason = "settings")
        HijriWidgetRefreshScheduler.schedule(context)
        HijriWidgetPreviewPublisher.publishIfDueAsync(context)
        PakistanWarmUp.ensureWarm()
    }
}