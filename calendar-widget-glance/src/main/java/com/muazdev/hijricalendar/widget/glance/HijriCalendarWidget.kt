package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.widget.glance.R
import com.muazdev.hijricalendar.widgetdata.HijriDayWidgetData
import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization
import com.muazdev.hijricalendar.widgetdata.buildHijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.offsetHijriMonth
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData

const val HIJRI_DEEP_LINK_TODAY = "hijricalendar://today"

/**
 * The Hijri home-screen widget family.
 *
 * Large sizes render the full 42-day Hijri month grid of the currently viewed month. Header
 * arrows move the grid forward/back one Hijri month in place and tapping the month name returns
 * it to following today; the choice is remembered per widget instance. Compact sizes render a
 * "today" card. All rendering data comes from the `calendar-widget-data` projection, so the grid
 * lines up exactly with the in-app calendar.
 *
 * Per-widget config (options + viewed month) lives in Glance's preferences state store. The
 * composable reads it through [currentState], so every `UpdateGlanceState` event — which Glance
 * processes by re-reading the store (AppWidgetSession.processEvent) and bumping a
 * `neverEqualPolicy` snapshot state that backs the `LocalState` composition local — recomposes
 * with the newest values. That makes navigation writes visible even when the tap lands on a
 * still-open session whose composition captured earlier values, which is the guarantee the
 * fire-and-forget `update()` API alone cannot provide (see `HijriWidgetRenderQueue`).
 */
class HijriCalendarWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(104.dp, 110.dp), // compact today card
            DpSize(200.dp, 160.dp), // narrow medium
            DpSize(260.dp, 280.dp), // wide/large grid
        ),
    )

    /**
     * Picker previews are composed at the large grid size so the picker shows the month grid
     * (not the compact card that the widget's own minimum size would render).
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(DpSize(260.dp, 280.dp)),
    )

    @OptIn(ExperimentalGlanceApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // One-shot, non-reactive peek for things that must not wait for the live state read: port
        // any legacy flat-file config and decide whether the Pakistan century table must be warm
        // before a render can build the grid. Reading state here also means the first composition
        // below sees the migrated store.
        HijriWidgetConfig.migrateLegacyIfNeeded(context, id)
        val peeked = HijriWidgetConfig.decodeOptions(
            getAppWidgetState(context, HijriWidgetConfig.PREFS, id),
        ) ?: HijriWidgetConfig.DEFAULTS
        if (peeked.source.pakistan) {
            // A render can race the app's warm-up coroutine (process restart on a widget tap);
            // join it instead of building the century table inline on this composition.
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
        val openAction = actionStartActivity(openAppIntent(context))
        val prevAction = actionRunCallback<HijriWidgetPrevMonthCallback>()
        val nextAction = actionRunCallback<HijriWidgetNextMonthCallback>()
        val resetAction = actionRunCallback<HijriWidgetTodayResetCallback>()

        provideContent {
            // Reactive read: recomposes whenever Glance observes a state change for this widget,
            // so a tap that lands on an open session still renders the newest month.
            val prefs = currentState<Preferences>()
            val options = HijriWidgetConfig.decodeOptions(prefs) ?: HijriWidgetConfig.DEFAULTS
            val viewedMonth = HijriWidgetConfig.decodeViewed(prefs)
            val data = HijriWidgetRenderCache.render(
                glanceId = id.toString(),
                options = options,
                viewedMonth = viewedMonth,
                todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
                layoutRtl = computeLayoutRtl(context, options.language),
            )
            HijriWidgetRoot(
                monthData = data.monthData,
                todayHijri = data.todayHijri,
                todayEpochDay = data.todayEpochDay,
                layoutRtl = data.layoutRtl,
                colors = colors,
                openAction = openAction,
                prevAction = prevAction,
                nextAction = nextAction,
                resetAction = resetAction,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: renders today's grid with the family's current options
     * (language, numerals, source) through the same pipeline as the live widget, with all actions
     * null so the preview is non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     */
    @OptIn(ExperimentalGlanceApi::class)
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null)
            HijriWidgetRoot(
                monthData = data.monthData,
                todayHijri = data.todayHijri,
                todayEpochDay = data.todayEpochDay,
                layoutRtl = data.layoutRtl,
                colors = colors,
                openAction = null,
                prevAction = null,
                nextAction = null,
                resetAction = null,
            )
        }
    }
}

/** Intent that opens the app on today, shared by every widget in the family. */
internal fun openAppIntent(context: Context): Intent {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HIJRI_DEEP_LINK_TODAY))
    intent.setPackage(context.packageName)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    return intent
}

/** Everything a render pass needs, so the live widget and the settings preview share one pipeline. */
internal data class HijriWidgetRenderData(
    val todayHijri: TodayHijriWidgetData?,
    val todayEpochDay: Long,
    val monthData: HijriMonthWidgetData?,
    val layoutRtl: Boolean,
)

/**
 * Builds the projection for [options]. Used by both [HijriCalendarWidget.provideGlance] and the
 * settings-screen preview, so the preview cannot drift from the real widget.
 */
internal fun buildRenderData(
    context: Context,
    options: HijriWidgetConfig.WidgetOptions,
    viewedMonth: Pair<Int, Int>?,
): HijriWidgetRenderData {
    val todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay()
    val language = options.language
    // Month names follow `monthNameLanguage` (independent of `language`); digits, days-of-week and
    // reading direction still follow `language`.
    val todayHijri = todayHijriWidgetData(
        anchorEpochDay = todayEpochDay,
        adjustmentDays = options.adjustmentDays,
        localizedHijriMonthNames = WidgetLocalization.hijriMonthNames(options.monthNameLanguage),
        localizedGregorianMonthNames = WidgetLocalization.gregorianMonthNames(options.monthNameLanguage),
        localizedWeekdayNames = WidgetLocalization.weekdayNames(language),
        numeralStyle = options.numeralStyle,
        pakistan = options.source.pakistan,
    )
    val layoutRtl = computeLayoutRtl(context, language)
    val monthData = buildMonthData(options, viewedMonth, todayHijri, layoutRtl)
    return HijriWidgetRenderData(todayHijri, todayEpochDay, monthData, layoutRtl)
}

/**
 * Glance rows are plain horizontal LinearLayouts, so on an RTL-locale device the platform already
 * mirrors child order. The widget's direction must follow its own language, not the device, so the
 * projection is pre-reversed whenever the two disagree; the net visual order is then always the
 * language's. On an LTR device this is just `language.isRtl`.
 */
internal fun computeLayoutRtl(context: Context, language: WidgetLanguage): Boolean {
    val deviceRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    return language.isRtl != deviceRtl
}

/**
 * Resolves the grid month as viewed (on-widget navigation) > config-pinned > today, so the arrows
 * and the month-name reset override the configured pin until the user returns to following today.
 * The source, language, digit style and reading direction all come from [options], so every
 * re-render path produces the same grid.
 */
internal fun buildMonthData(
    options: HijriWidgetConfig.WidgetOptions,
    viewedMonth: Pair<Int, Int>?,
    todayHijri: TodayHijriWidgetData?,
    layoutRtl: Boolean,
): HijriMonthWidgetData? {
    val year = viewedMonth?.first ?: options.pinnedYear ?: todayHijri?.hijriYear ?: return null
    val month = viewedMonth?.second ?: options.pinnedMonth ?: todayHijri?.hijriMonth ?: return null
    val language = options.language
    return buildHijriMonthWidgetData(
        hijriYear = year,
        hijriMonth = month,
        adjustmentDays = options.adjustmentDays,
        firstDayOfWeekIndex = options.firstDayOfWeekIndex,
        numeralStyle = options.numeralStyle,
        pakistan = options.source.pakistan,
        rightToLeft = layoutRtl,
        localizedHijriMonthNames = WidgetLocalization.hijriMonthNames(options.monthNameLanguage),
        localizedGregorianMonthNames = WidgetLocalization.gregorianMonthNames(options.monthNameLanguage),
        localizedWeekdayNames = WidgetLocalization.weekdayNames(language),
    )
}

/**
 * Per-instance projection cache so a render that recomposes the same month (navigation tap,
 * covered background refresh, midnight pass) reuses the computed grid and "today" instead of
 * re-running the Hijri math every time. Keys cover everything that affects the output —
 * including [HijriMonthOverrides.currentRevision], so an override change invalidates both
 * projections even when the displayed month is unchanged.
 *
 * Guarded by an internal monitor: reads can now come from inside the Glance composition (which
 * runs on the session worker and takes no render lock), and the settings preview never touches
 * this cache (it builds through [buildRenderData] instead), so a plain synchronized section is
 * safe. Keeps at most one month + one today per widget instance.
 */
internal object HijriWidgetRenderCache {

    private val cacheLock = Any()

    private data class TodayKey(
        val glanceId: String,
        val anchorEpochDay: Long,
        val adjustmentDays: Int,
        val language: WidgetLanguage,
        val monthNameLanguage: WidgetLanguage,
        val numeralStyle: NumeralStyle,
        val pakistan: Boolean,
        val overridesRevision: Long,
    )

    private data class MonthKey(
        val glanceId: String,
        val year: Int,
        val month: Int,
        val adjustmentDays: Int,
        val firstDayOfWeekIndex: Int,
        val numeralStyle: NumeralStyle,
        val language: WidgetLanguage,
        val monthNameLanguage: WidgetLanguage,
        val pakistan: Boolean,
        val rightToLeft: Boolean,
        val overridesRevision: Long,
    )

    private val todayCache = HashMap<TodayKey, TodayHijriWidgetData?>()
    private val monthCache = HashMap<MonthKey, HijriMonthWidgetData?>()

    /**
     * Same contract as [buildRenderData]: resolves the grid month as viewed > pinned > today and
     * returns the projections needed by [HijriWidgetRoot]. Uses [buildMonthData] for the grid so
     * the live widget and the settings preview can never drift.
     */
    fun render(
        glanceId: String,
        options: HijriWidgetConfig.WidgetOptions,
        viewedMonth: Pair<Int, Int>?,
        todayEpochDay: Long,
        layoutRtl: Boolean,
    ): HijriWidgetRenderData {
        val todayHijri = today(glanceId, options, todayEpochDay)
        val revision = HijriMonthOverrides.currentRevision
        val monthYear = viewedMonth?.first ?: options.pinnedYear ?: todayHijri?.hijriYear
        val monthNumber = viewedMonth?.second ?: options.pinnedMonth ?: todayHijri?.hijriMonth
        val monthKey = if (monthYear == null || monthNumber == null) {
            null
        } else {
            MonthKey(
                glanceId,
                monthYear,
                monthNumber,
                options.adjustmentDays,
                options.firstDayOfWeekIndex,
                options.numeralStyle,
                options.language,
                options.monthNameLanguage,
                options.source.pakistan,
                layoutRtl,
                revision,
            )
        }
        val monthData = if (monthKey == null) {
            null
        } else {
            synchronized(cacheLock) {
                monthCache.getOrPut(monthKey) {
                    buildMonthData(options, viewedMonth, todayHijri, layoutRtl)
                }
            }
        }
        return HijriWidgetRenderData(todayHijri, todayEpochDay, monthData, layoutRtl)
    }

    /**
     * Cached "today" projection shared by the month grid and the Today strip: keyed by
     * everything that affects the output (anchor day, adjustment, language, numerals, source and
     * [HijriMonthOverrides.currentRevision]), so a recompose of an unchanged day skips the Hijri
     * math — including a cold Pakistan century-table build.
     */
    fun today(
        glanceId: String,
        options: HijriWidgetConfig.WidgetOptions,
        todayEpochDay: Long,
    ): TodayHijriWidgetData? {
        val key = TodayKey(
            glanceId,
            todayEpochDay,
            options.adjustmentDays,
            options.language,
            options.monthNameLanguage,
            options.numeralStyle,
            options.source.pakistan,
            HijriMonthOverrides.currentRevision,
        )
        synchronized(cacheLock) {
            return todayCache.getOrPut(key) {
                todayHijriWidgetData(
                    anchorEpochDay = todayEpochDay,
                    adjustmentDays = options.adjustmentDays,
                    localizedHijriMonthNames = WidgetLocalization.hijriMonthNames(options.monthNameLanguage),
                    localizedGregorianMonthNames = WidgetLocalization.gregorianMonthNames(options.monthNameLanguage),
                    localizedWeekdayNames = WidgetLocalization.weekdayNames(options.language),
                    numeralStyle = options.numeralStyle,
                    pakistan = options.source.pakistan,
                )
            }
        }
    }
}

/**
 * Day/night aware widget colors, resolved from resources once per render so Glance views are
 * correct whether the widget is drawn in light or dark mode.
 */
internal class WidgetColors(
    val background: Color,
    val accent: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val weekendText: Color,
    val todayBackground: Color,
    val onTodayText: Color,
) {
    companion object {
        fun from(context: Context) = WidgetColors(
            background = context.widgetColor(R.color.widget_background),
            accent = context.widgetColor(R.color.widget_accent),
            primaryText = context.widgetColor(R.color.widget_text_primary),
            secondaryText = context.widgetColor(R.color.widget_text_secondary),
            weekendText = context.widgetColor(R.color.widget_weekend_text),
            todayBackground = context.widgetColor(R.color.widget_today_background),
            onTodayText = context.widgetColor(R.color.widget_on_today),
        )
    }
}

private fun Context.widgetColor(@ColorRes resId: Int): Color = Color(getColor(resId))

/**
 * Applies [clickable] only when an action is present. The settings preview renders the same tree
 * with all actions `null`, so a preview tap cannot navigate or mutate the real widget.
 */
private fun GlanceModifier.clickableWhen(action: Action?): GlanceModifier =
    if (action != null) this.clickable(action) else this

@Composable
internal fun HijriWidgetRoot(
    monthData: HijriMonthWidgetData?,
    todayHijri: TodayHijriWidgetData?,
    todayEpochDay: Long,
    layoutRtl: Boolean,
    colors: WidgetColors,
    openAction: Action?,
    prevAction: Action?,
    nextAction: Action?,
    resetAction: Action?,
) {
    val size = LocalSize.current
    val useCompact = size.width < 180.dp || size.height < 200.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .clickableWhen(openAction)
            .padding(10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (useCompact || monthData == null) {
            TodayCard(today = todayHijri, colors = colors)
        } else {
            MonthGrid(
                month = monthData,
                todayEpochDay = todayEpochDay,
                layoutRtl = layoutRtl,
                colors = colors,
                prevAction = prevAction,
                nextAction = nextAction,
                resetAction = resetAction,
            )
        }
    }
}

@Composable
private fun TodayCard(
    today: TodayHijriWidgetData?,
    colors: WidgetColors,
) {
    if (today == null) {
        Text(
            text = LocalContext.current.getString(R.string.hijri_widget_unavailable),
            style = TextStyle(color = ColorProvider(colors.secondaryText), fontSize = 12.sp),
        )
        return
    }
    // On the 1x1 size every line must still fit, so the card shrinks its type instead of letting
    // the month name get ellipsized or dropped.
    val compactSize = LocalSize.current
    val isTiny = compactSize.height < 130.dp || compactSize.width < 130.dp
    val dayFontSize = if (isTiny) 32.sp else 46.sp
    val monthFontSize = if (isTiny) 11.sp else 14.sp
    val gregorianFontSize = if (isTiny) 9.sp else 11.sp
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = today.hijriDayText,
            style = TextStyle(
                color = ColorProvider(colors.accent),
                fontSize = dayFontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = "${today.hijriMonthName} ${today.hijriYear}",
            style = TextStyle(
                color = ColorProvider(colors.primaryText),
                fontSize = monthFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = today.gregorianDate,
            style = TextStyle(color = ColorProvider(colors.secondaryText), fontSize = gregorianFontSize, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

@Composable
private fun MonthGrid(
    month: HijriMonthWidgetData,
    todayEpochDay: Long,
    layoutRtl: Boolean,
    colors: WidgetColors,
    prevAction: Action?,
    nextAction: Action?,
    resetAction: Action?,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // The arrows' glyphs follow the widget's language; their source order follows
        // [layoutRtl], which already accounts for the platform mirroring on RTL-locale devices.
        // The net visual order is always the language's reading direction.
        val prevAvailable = offsetHijriMonth(
            month.hijriYear, month.hijriMonth, HijriWidgetNavigation.STEP_PREVIOUS,
        ) != null
        val nextAvailable = offsetHijriMonth(
            month.hijriYear, month.hijriMonth, HijriWidgetNavigation.STEP_NEXT,
        ) != null

        // Header: arrows step the grid one Hijri month at a time; the centred title line carries
        // the Hijri month + year and the Gregorian month + year on one line, and tapping it
        // returns the grid to following today.
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            if (layoutRtl) {
                // In RTL "next" sits on the left and points left; "previous" sits on the
                // right and points right, matching the app's AutoMirrored header arrows.
                NavigationArrow(
                    resId = R.drawable.ic_arrow_left,
                    enabled = nextAvailable,
                    action = nextAction,
                    color = colors.primaryText,
                    contentDescription = "Next month",
                )
                MonthTitle(month = month, resetAction = resetAction, colors = colors)
                NavigationArrow(
                    resId = R.drawable.ic_arrow_right,
                    enabled = prevAvailable,
                    action = prevAction,
                    color = colors.primaryText,
                    contentDescription = "Previous month",
                )
            } else {
                NavigationArrow(
                    resId = R.drawable.ic_arrow_left,
                    enabled = prevAvailable,
                    action = prevAction,
                    color = colors.primaryText,
                    contentDescription = "Previous month",
                )
                MonthTitle(month = month, resetAction = resetAction, colors = colors)
                NavigationArrow(
                    resId = R.drawable.ic_arrow_right,
                    enabled = nextAvailable,
                    action = nextAction,
                    color = colors.primaryText,
                    contentDescription = "Next month",
                )
            }
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            month.weekdayHeaders.forEach { name ->
                Text(
                    text = name,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(colors.secondaryText),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        month.days.chunked(7).forEach { week ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                week.forEach { cell ->
                    DayCell(cell = cell, todayEpochDay = todayEpochDay, colors = colors)
                }
            }
        }
    }
}

/**
 * The tappable month title in the header: the Hijri month + year and the Gregorian month + year
 * combined on one centred line (urdu header order, e.g. "محرم ۱۴۴۸ · September 2026"). The three
 * spans are grouped in an inner row centred inside the box, so the *combined* title sits centred
 * between the two arrows in either reading direction. Tapping it returns the grid to following
 * today. Kept as a `RowScope` extension so it can take the header row's remaining width.
 */
@Composable
private fun RowScope.MonthTitle(
    month: HijriMonthWidgetData,
    resetAction: Action?,
    colors: WidgetColors,
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .clickableWhen(resetAction)
            .semantics { contentDescription = "Go to current month" },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = "${month.hijriMonthName} ${month.hijriYear}",
                style = TextStyle(
                    color = ColorProvider(colors.primaryText),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(
                text = " · ",
                style = TextStyle(
                    color = ColorProvider(colors.secondaryText),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(
                text = month.gregorianMonthTitle,
                style = TextStyle(
                    color = ColorProvider(colors.secondaryText),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * One header navigation arrow. At the supported-range edge the arrow is drawn dimmed and is not
 * clickable, so navigation degrades gracefully instead of hitting an invalid month. Sized to the
 * recommended 48.dp touch target (the icon itself is 40.dp) so it stays an easy tap target and
 * visually reads as a distinct control next to the grid text.
 */
@Composable
private fun NavigationArrow(
    resId: Int,
    enabled: Boolean,
    action: Action?,
    color: Color,
    contentDescription: String,
) {
    val modifier = GlanceModifier
        .semantics { this.contentDescription = contentDescription }
        .padding(horizontal = 4.dp, vertical = 4.dp)
    Box(
        modifier = if (enabled) modifier.clickableWhen(action) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(resId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                ColorProvider(if (enabled) color else color.copy(alpha = 0.35f))
            ),
            modifier = GlanceModifier.size(40.dp)
        )
    }
}

@Composable
private fun RowScope.DayCell(
    cell: HijriDayWidgetData,
    todayEpochDay: Long,
    colors: WidgetColors,
) {
    val isToday = cell.gregorianEpochDay == todayEpochDay
    val base = GlanceModifier
        .defaultWeight()
        .fillMaxHeight()
        .padding(horizontal = 1.dp, vertical = 1.dp)

    // Today is a filled highlight like the in-app selected day: the accent container with its
    // "on-today" content colour. Everything else matches the app's precedence: out-of-month >
    // weekend > regular.
    val hijriColor = when {
        isToday -> colors.onTodayText
        !cell.isCurrentMonth -> colors.primaryText.copy(alpha = 0.38f)
        cell.isWeekend -> colors.weekendText
        else -> colors.primaryText
    }
    val gregorianColor = when {
        isToday -> colors.onTodayText.copy(alpha = 0.8f)
        !cell.isCurrentMonth -> colors.primaryText.copy(alpha = 0.24f)
        else -> colors.primaryText.copy(alpha = 0.6f)
    }

    Box(modifier = base) {
        if (isToday) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .background(colors.todayBackground)
                    .cornerRadius(14.dp),
            ) {}
        }
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(
                text = cell.dayText,
                style = TextStyle(
                    color = ColorProvider(hijriColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(
                text = cell.gregorianDayText,
                style = TextStyle(
                    color = ColorProvider(gregorianColor),
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}
