package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.HijriEvent
import com.muazdev.hijricalendar.core.HijriEventLanguage
import com.muazdev.hijricalendar.core.HijriEvents
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.widgetdata.GridTypography
import com.muazdev.hijricalendar.widgetdata.HijriDayWidgetData
import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.HijriYearMonth
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WeekStart
import com.muazdev.hijricalendar.widgetdata.WeekendPattern
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.buildHijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.offsetHijriMonth
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData

public const val HIJRI_DEEP_LINK_TODAY: String = "hijricalendar://today"

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
public class HijriCalendarWidget : GlanceAppWidget() {

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
        val colors = WidgetColors.DEFAULT
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
            val selectedDay = HijriWidgetConfig.decodeSelectedDay(prefs)
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
                showAdjacentDays = data.showAdjacentDays,
                showCellBorders = data.showCellBorders,
                selectedDay = selectedDay,
                // Resolved here, where the options live: the footer must name an observance in the
                // *widget's* language and calendar space, and this is the only place that knows both.
                selectedEventName = selectedDay
                    ?.let { eventFor(it, options)?.name(eventLanguage(options.language)) },
                colors = colors,
                language = options.language,
                actions = WidgetActions(
                    open = openAction,
                    prev = prevAction,
                    next = nextAction,
                    reset = actionRunCallback<HijriWidgetTodayResetCallback>(),
                ),
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
        val colors = WidgetColors.DEFAULT
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null)
            HijriWidgetRoot(
                monthData = data.monthData,
                todayHijri = data.todayHijri,
                todayEpochDay = data.todayEpochDay,
                layoutRtl = data.layoutRtl,
                showAdjacentDays = data.showAdjacentDays,
                showCellBorders = data.showCellBorders,
                selectedDay = null,
                selectedEventName = null,
                colors = colors,
                language = options.language,
                // The picker preview is non-interactive by construction (WG-12's grouping makes
                // that one value rather than four nulls a call site has to remember).
                actions = WidgetActions(),
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
    /**
     * Carried through the render data rather than read from [options] inside the composable,
     * alongside [layoutRtl] for the same reason: the grid must paint exactly what the projection
     * that produced [monthData] was configured for, and the two cannot drift because neither is
     * re-read from the options at compose time.
     *
     * It is deliberately **not** in [HijriWidgetRenderCache]'s keys. The projection's output does
     * not depend on it — [HijriMonthWidgetData.days] is the padded month either way — so folding it
     * into `MonthKey` would invalidate 42 cells of cached projection on a change that cannot alter
     * a single one of them.
     */
    val showAdjacentDays: Boolean,
    /**
     * Carried alongside [showAdjacentDays] for the same reason: the grid must paint exactly what the
     * options configured, and neither flag belongs in the render cache's keys — the projection's cells
     * are identical whether or not a divider is drawn.
     */
    val showCellBorders: Boolean,
    /**
     * The day the user tapped, or `null` (FD-09). Read from the same reactive preferences snapshot as
     * the viewed month, so a tap and the render it triggers cannot disagree about what is selected —
     * and so a render that races a tap sees the newest value rather than the one it started with.
     */
    val selectedDay: HijriDaySelection?,
)

/**
 * Builds the projection for [options]. Used by both [HijriCalendarWidget.provideGlance] and the
 * settings-screen and picker previews, so the preview cannot drift from the real widget.
 */
internal fun buildRenderData(
    context: Context,
    options: WidgetOptions,
    viewedMonth: HijriYearMonth?,
): HijriWidgetRenderData {
    val todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay()
    val layoutRtl = computeLayoutRtl(context, options.language)
    // Through [HijriWidgetRenderCache.render], which is what the live widget uses (WG-11). This used
    // to call the projection builders directly, so the preview and the widget shared a *function*
    // but not a *pipeline* — the live widget's grid was cached and the preview's was not, which is
    // precisely the difference the cache's KDoc claimed could not exist.
    return HijriWidgetRenderCache.render(
        glanceId = HijriWidgetRenderCache.PREVIEW_CACHE_ID,
        options = options,
        viewedMonth = viewedMonth,
        todayEpochDay = todayEpochDay,
        layoutRtl = layoutRtl,
    )
}

/**
 * Glance rows are plain horizontal LinearLayouts, so on an RTL-locale device the platform already
 * mirrors child order. The widget's direction must follow its own language, not the device, so the
 * projection is pre-reversed whenever the two disagree; the net visual order is then always the
 * language's. On an LTR device this is just `language.isRtl`.
 */
internal fun computeLayoutRtl(context: Context, language: WidgetLanguage): Boolean = resolveLayoutRtl(
    deviceRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
    language = language,
)

/**
 * The XOR on its own, so it can be tested without a `Configuration` (WG-16).
 *
 * The split is deliberate and not just about the test: reading the device's layout direction is
 * plumbing that the platform owns, while the *decision* — whether the projection must pre-reverse
 * to cancel a mirroring the platform will already apply — is the logic, and it is the half that is
 * easy to get wrong. The interesting row is [deviceRtl] = `true` with an [WidgetLanguage.URDU]
 * widget, where the answer is `false`: the platform is already mirroring the `LinearLayout` rows, so
 * reversing as well would undo it. `language.isRtl` on its own — the obvious implementation — is
 * wrong in both mixed rows.
 */
internal fun resolveLayoutRtl(deviceRtl: Boolean, language: WidgetLanguage): Boolean =
    language.isRtl != deviceRtl

/**
 * The one place the grid month is resolved: viewed (on-widget navigation) > config-pinned > today.
 * The arrows and the month-name reset therefore override the configured pin until the user returns
 * to following today.
 *
 * This existed as three copies of the same two-line `?:` chain — in the projection, in the render
 * cache key, and in the navigation stepper — and the copies disagreed with the iOS renderer about
 * one input. A half-set pin (see [WidgetOptions.pinned]) is representable in the stored schema, and
 * a chain that reads `pinnedYear` and `pinnedMonth` independently pairs a stored year with *today's*
 * month: a grid labelled 1447 painting this year's days, with no error anywhere. `options.pinned`
 * is a unit, so there is no way to take half of it.
 *
 * @return the Hijri year and month to render, or `null` when neither a viewed month, a pin, nor
 *   today's date is available.
 */
internal fun resolveGridMonth(
    options: WidgetOptions,
    viewedMonth: HijriYearMonth?,
    todayHijri: TodayHijriWidgetData?,
): HijriYearMonth? = viewedMonth
    ?: options.pinned
    ?: todayHijri?.let { HijriYearMonth(year = it.hijriYear, month = it.hijriMonth) }

/**
 * Whether the grid is currently showing [year]-[month] (FD-09).
 *
 * Read through the same [resolveGridMonth] the render path uses, so "should this tap move the grid" and
 * "what month does the grid draw" cannot disagree about a half-set pin — which is exactly the WD-03
 * defect the single resolver exists to prevent.
 */
internal fun isShowingMonth(
    options: WidgetOptions,
    viewedMonth: HijriYearMonth?,
    todayHijri: TodayHijriWidgetData?,
    year: Int,
    month: Int,
): Boolean = resolveGridMonth(options, viewedMonth, todayHijri)?.let { it.year == year && it.month == month }
    ?: false

/**
 * Builds the month projection for an already-resolved grid month. The source, language, digit style
 * and reading direction all come from [options], so every re-render path produces the same grid.
 */
internal fun buildMonthData(
    options: WidgetOptions,
    viewedMonth: HijriYearMonth?,
    todayHijri: TodayHijriWidgetData?,
    layoutRtl: Boolean,
): HijriMonthWidgetData? {
    val (year, month) = resolveGridMonth(options, viewedMonth, todayHijri) ?: return null
    return buildHijriMonthWidgetData(
        hijriYear = year,
        hijriMonth = month,
        options = options,
        // From the widget's own options (FD-03). This used to be the literal
        // `WeekDay.WEEKEND_DAYS`, so every widget shaded Friday and Saturday and nobody could change
        // it — which is right for the Pakistan calendar the library also supports, and wrong for a
        // user whose weekend is not Friday and Saturday.
        weekendDays = options.weekendPattern.toWeekDays(),
        // The projection is pre-reversed for the device's mirroring, so the net visual order is
        // the option language's — which is what `layoutRtl` already encodes.
        rightToLeft = layoutRtl,
    )
}

/**
 * Per-instance projection cache so a render that recomposes the same month (navigation tap,
 * covered background refresh, midnight pass) reuses the computed grid and "today" instead of
 * re-running the Hijri math every time. Keys cover everything that affects the output —
 * including the month-length table, so an override change invalidates both projections even when
 * the displayed month is unchanged.
 *
 * The override key is the *options' own* table, not the process-wide global: the projection reads
 * [WidgetOptions.overridesTable], so keying on `HijriMonthOverrides.currentRevision` alone used to
 * invalidate the cache on a change that could not affect the output (and to miss one that could,
 * when a widget carried its own map). See [overrideCacheKey] for the shape.
 *
 * **Bounded**, because a key is not just the widget id: it also carries the anchor day, the
 * resolved month, the adjustment, the week start, the digit style, both languages, the source, the
 * reading direction and the override table. The cache used to be a pair of unbounded `HashMap`s
 * whose KDoc claimed "at most one month + one today per widget instance" — true when the key *was*
 * the id, wrong from the moment the key grew, and nothing forced it to be re-examined (WG-04). The
 * entries are large: a `HijriMonthWidgetData` is 42 cells of two `String`s each. And nothing in
 * this module ever learns that a widget was removed — Glance deletes its own DataStore and says
 * nothing — so an LRU is the only design that can be correct; a per-instance map cleaned on removal
 * has no removal signal to hang off.
 *
 * **Thread-safety** is now [LruCache]'s own, per access, rather than one monitor held across the
 * whole operation. That is the point of the `get`/build/`put` shape below: a build must never run
 * under the cache's lock. It used to, which meant a real widget's render could be serialised behind
 * a settings preview's cold Pakistan century-table build — a Glance composition can be the main
 * thread on some devices, so that is a frame-time hazard in the host app, not just this module.
 *
 * The settings preview shares this cache too, under a stable synthetic id ([PREVIEW_CACHE_ID]), so
 * "the preview cannot drift from the widget" is true of the path and not just of the function
 * (WG-11). It used to key on the fresh random id that `GlanceAppWidget.compose()` mints per call —
 * an entry no future read could ever hit, so the preview path grew the cache without bound and
 * returned nothing for the cost.
 */
internal object HijriWidgetRenderCache {

    /**
     * A stable synthetic id for every non-widget render path (the settings live preview and the
     * Android 15+ `providePreview` trees).
     *
     * One id for all of them is deliberate. The cache key already carries the month, the language,
     * the source and everything else that differentiates those projections, so the id only has to be
     * *stable* — using `GlanceAppWidget.compose()`'s per-call random id made every entry
     * unreachable, and using the widget kind instead would have fragmented a cache that has no
     * reason to fragment.
     */
    internal const val PREVIEW_CACHE_ID: String = "preview"

    /**
     * How many projections to keep. Generous for the realistic case — a user places a handful of
     * widgets, and the settings screen churns one more — while staying bounded on a device that has
     * none. Roughly a dozen widgets' worth of month + today.
     */
    internal const val MAX_CACHE_ENTRIES: Int = 32

    /** Total entries currently held across both caches. For tests, which assert the bound. */
    internal val cachedEntryCountForTest: Int get() = todayCache.size() + monthCache.size()

    /**
     * Everything about a [WidgetOptions]'s month lengths that can change the projection: the
     * widget's own override map, plus the process global's revision **only when that map is
     * empty** — because that is the only case where the global is the table being read.
     *
     * Structural, not a hash. A single-entry `Map`'s `hashCode()` is `key.hashCode() xor
     * value.hashCode()`, so two *different* override tables collide routinely (`{"1448-3" to 30}`
     * and `{"1448-4" to 29}` do), and a collision here would hand a widget back a stale grid. A
     * `HashMap` resolves collisions with `equals`, so storing the map is exact where a digest is
     * not; `0L` in the unused branch keeps the two cases from masking each other.
     */
    private data class OverrideKey(
        val overrides: Map<String, Int>,
        val globalRevision: Long,
    )

    private fun overrideCacheKey(options: WidgetOptions): OverrideKey = OverrideKey(
        overrides = options.monthLengthOverrides,
        globalRevision = if (options.monthLengthOverrides.isEmpty()) {
            HijriMonthOverrides.currentRevision
        } else {
            0L
        },
    )

    private data class TodayKey(
        val glanceId: String,
        val anchorEpochDay: Long,
        val adjustmentDays: Int,
        val language: WidgetLanguage,
        val monthNameLanguage: WidgetLanguage,
        val numeralStyle: NumeralStyle,
        val pakistan: Boolean,
        val overrides: OverrideKey,
    )

    private data class MonthKey(
        val glanceId: String,
        val year: Int,
        val month: Int,
        val adjustmentDays: Int,
        val weekStart: WeekStart,
        val numeralStyle: NumeralStyle,
        val language: WidgetLanguage,
        val monthNameLanguage: WidgetLanguage,
        val pakistan: Boolean,
        val rightToLeft: Boolean,
        val overrides: OverrideKey,
        /**
         * FD-03. **This key omitted the weekend set for one commit**, and the symptom was that the
         * weekend setting appeared to do nothing: a render after the change served a month built with
         * the previous set, so the shaded columns stayed exactly where they were while the UI reported
         * a new choice.
         *
         * This is precisely the trap the class KDoc warns about — "keys cover everything that affects
         * the output" — and the reason this field is written out by name rather than folded into
         * something coarser. The three options that do **not** appear here are the ones that cannot
         * alter a single cell: [WidgetOptions.showAdjacentDays] and [WidgetOptions.showCellBorders] are
         * applied by the renderer to an unchanged projection, and a selection is widget state, not an
         * option.
         */
        val weekendPattern: WeekendPattern,
    )

    private val todayCache = LruCache<TodayKey, TodayHijriWidgetData>(MAX_CACHE_ENTRIES)
    private val monthCache = LruCache<MonthKey, HijriMonthWidgetData>(MAX_CACHE_ENTRIES)

    /**
     * Same contract as [buildRenderData]: resolves the grid month as viewed > pinned > today and
     * returns the projections needed by [HijriWidgetRoot]. Uses [buildMonthData] for the grid so
     * the live widget and the settings preview can never drift.
     */
    fun render(
        glanceId: String,
        options: WidgetOptions,
        viewedMonth: HijriYearMonth?,
        todayEpochDay: Long,
        layoutRtl: Boolean,
    ): HijriWidgetRenderData {
        val todayHijri = today(glanceId, options, todayEpochDay)
        val overrideKey = overrideCacheKey(options)
        val monthKey = resolveGridMonth(options, viewedMonth, todayHijri)?.let { (year, month) ->
            MonthKey(
                glanceId,
                year,
                month,
                options.adjustmentDays,
                options.effectiveWeekStart,
                options.numeralStyle,
                options.language,
                options.effectiveMonthNameLanguage,
                options.source.pakistan,
                layoutRtl,
                overrideKey,
                options.weekendPattern,
            )
        }
        val monthData = if (monthKey == null) {
            null
        } else {
            // Read and write around the build, never across it (WG-04c). Two threads may build the
            // same month concurrently — a wasted build, never a wrong result, since
            // `HijriMonthWidgetData` is immutable — and that is strictly better than serialising
            // every widget's render in the process on one monitor. The earlier `synchronized` also
            // held this across a cold Pakistan century-table build, so a settings preview could
            // block a real widget's render behind it.
            monthCache.get(monthKey)
                ?: buildMonthData(options, viewedMonth, todayHijri, layoutRtl)
                    ?.also { monthCache.put(monthKey, it) }
        }
        return HijriWidgetRenderData(
            todayHijri = todayHijri,
            todayEpochDay = todayEpochDay,
            monthData = monthData,
            layoutRtl = layoutRtl,
            showAdjacentDays = options.showAdjacentDays,
            showCellBorders = options.showCellBorders,
            selectedDay = null,
        )
    }

    /**
     * Cached "today" projection shared by the month grid and the Today strip: keyed by
     * everything that affects the output (anchor day, adjustment, language, numerals, source and
     * [WidgetOptions.overridesTable]), so a recompose of an unchanged day skips the Hijri
     * math — including a cold Pakistan century-table build.
     */
    fun today(
        glanceId: String,
        options: WidgetOptions,
        todayEpochDay: Long,
    ): TodayHijriWidgetData? {
        val key = TodayKey(
            glanceId,
            todayEpochDay,
            options.adjustmentDays,
            options.language,
            options.effectiveMonthNameLanguage,
            options.numeralStyle,
            options.source.pakistan,
            overrideCacheKey(options),
        )
        // Same read-write-build-write shape as [render] — see there for why the build is outside
        // the cache's own lock (WG-04c).
        return todayCache.get(key)
            ?: todayHijriWidgetData(anchorEpochDay = todayEpochDay, options = options)
                ?.also { todayCache.put(key, it) }
    }
}

/**
 * The widget's palette, as **resource ids** rather than resolved colours (FD-07).
 *
 * Each member is a `ColorProvider` built from an `@ColorRes`, so what Glance serialises into the
 * `RemoteViews` is the resource reference and the launcher resolves it against **its own**
 * configuration at bind time. A night-mode switch then re-resolves ten colours inside the existing
 * view: no re-compose, no invalidation, no placeholder, no restart.
 *
 * The previous shape resolved everything eagerly — `context.getColor(R.color.x)` — and handed Glance
 * a literal int. A number carries no idea of where it came from, so the launcher could not re-resolve
 * it, and a night-mode switch had to invalidate the whole view to change anything. That is the restart
 * the report describes, and no amount of `updatePeriodMillis` removes it: re-rendering is the restart.
 * A `uiMode` broadcast receiver would only make the rebuild *faster*.
 *
 * Because a `ColorProvider` cannot carry an alpha (Glance 1.2 has only the `Color` and `Int` factories),
 * the three dimmed day figures are real colour resources with their own night variants rather than
 * `primaryText.copy(alpha = …)`. See `values/colors.xml`.
 *
 * This takes no `Context` at all now — that is the point, and it is why the members are `val`s on a
 * class rather than something computed per render.
 */
@Suppress("LongParameterList")
internal class WidgetColors(
    val background: ColorProvider,
    val accent: ColorProvider,
    val primaryText: ColorProvider,
    val secondaryText: ColorProvider,
    /** A day of a neighbouring Hijri month, when `showAdjacentDays` paints them (FD-02). */
    val outOfMonthDay: ColorProvider,
    /** The Gregorian day under an in-month Hijri figure. */
    val gregorianDay: ColorProvider,
    /** The Gregorian day under a neighbouring month's figure. */
    val outOfMonthGregorianDay: ColorProvider,
    val weekendText: ColorProvider,
    val todayBackground: ColorProvider,
    val onTodayText: ColorProvider,
    /** The hairline between cells, when `showCellBorders` is on (FD-04). */
    val cellBorder: ColorProvider,
    /**
     * The ring around a tapped day (FD-09).
     *
     * A ring, not the today fill: a day can be **both** today and selected, and filling it would make
     * the two indistinguishable. A ring reads as "you chose this" where a fill reads as "this is
     * today", which is a different statement and has to survive their overlap.
     */
    val selectedDay: ColorProvider,
) {
    companion object {
        val DEFAULT: WidgetColors = WidgetColors(
            background = ColorProvider(R.color.widget_background),
            accent = ColorProvider(R.color.widget_accent),
            primaryText = ColorProvider(R.color.widget_text_primary),
            secondaryText = ColorProvider(R.color.widget_text_secondary),
            outOfMonthDay = ColorProvider(R.color.widget_day_out_of_month),
            gregorianDay = ColorProvider(R.color.widget_day_gregorian_sub),
            outOfMonthGregorianDay = ColorProvider(R.color.widget_day_out_faint),
            weekendText = ColorProvider(R.color.widget_weekend_text),
            todayBackground = ColorProvider(R.color.widget_today_background),
            onTodayText = ColorProvider(R.color.widget_on_today),
            cellBorder = ColorProvider(R.color.widget_cell_border),
            selectedDay = ColorProvider(R.color.widget_selected_day),
        )
    }
}

/**
 * The action for one day cell (FD-09), or `null` when the widget cannot be tapped.
 *
 * Null in three cases, all of them deliberate:
 *
 * - `actions.isNonInteractive` — every preview renders the same tree with no actions, so a picker
 *   preview and the settings live preview stay untappable without each remembering to suppress this.
 * - `paint = false` — a cell the widget is hiding must not become an **invisible tap target**. It
 *   keeps its slot for alignment and stays empty; making it tappable would put a button where the user
 *   sees nothing.
 * - no callback when the widget is on its compact today card, where there are no cells at all; the
 *   caller never reaches here in that case, and the `null` is belt and braces.
 */
private fun selectActionFor(
    actions: WidgetActions,
    year: Int,
    month: Int,
    day: Int,
    paint: Boolean,
): Action? = if (actions.isNonInteractive || !paint) {
    null
} else {
    actionRunCallback<HijriWidgetSelectDayCallback>(
        actionParametersOf(
            ACTION_YEAR to year,
            ACTION_MONTH to month,
            ACTION_DAY to day,
        ),
    )
}

/**
 * Applies [clickable] only when an action is present. The settings preview renders the same tree
 * with all actions `null`, so a preview tap cannot navigate or mutate the real widget.
 */
private fun GlanceModifier.clickableWhen(action: Action?): GlanceModifier =
    if (action != null) this.clickable(action) else this

@Composable
@Suppress("LongParameterList")
internal fun HijriWidgetRoot(
    monthData: HijriMonthWidgetData?,
    todayHijri: TodayHijriWidgetData?,
    todayEpochDay: Long,
    layoutRtl: Boolean,
    showAdjacentDays: Boolean,
    showCellBorders: Boolean,
    selectedDay: HijriDaySelection?,
    /**
     * The observance on [selectedDay], already named in the widget's language — or `null` when the
     * selected day carries none.
     *
     * Resolved by the caller rather than here because this is where the *projection's* language and
     * calendar space live. A footer re-deriving them would be a second place to get the space wrong,
     * and the two answers would differ on a Pakistan-calendar widget.
     */
    selectedEventName: String?,
    colors: WidgetColors,
    // The widget's own language, for the chrome's accessibility labels (WG-12). Not derivable from
    // `monthData`: a widget that fell back to the today card has no month projection at all, and its
    // labels still have to be in the right language.
    language: WidgetLanguage,
    actions: WidgetActions,
) {
    val size = LocalSize.current
    val useCompact = size.width < 180.dp || size.height < 200.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .clickableWhen(actions.open)
            .padding(10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (useCompact || monthData == null) {
            TodayCard(today = todayHijri, colors = colors, language = language)
        } else {
            MonthGrid(
                month = monthData,
                todayEpochDay = todayEpochDay,
                layoutRtl = layoutRtl,
                showAdjacentDays = showAdjacentDays,
                showCellBorders = showCellBorders,
                selectedDay = selectedDay,
                selectedEventName = selectedEventName,
                colors = colors,
                language = language,
                actions = actions,
            )
        }
    }
}

@Composable
private fun TodayCard(
    today: TodayHijriWidgetData?,
    colors: WidgetColors,
    language: WidgetLanguage,
) {
    if (today == null) {
        Text(
            // Not `getString(R.string.hijri_widget_unavailable)`: that resolves against the
            // *device* locale, so an Urdu widget on an English phone showed an English fallback.
            // Every other string this widget renders is resolved from `options.language`, and this
            // was the one that was not.
            text = WidgetLocalization.ChromeLabels.monthUnavailable(language),
            style = TextStyle(color = colors.secondaryText, fontSize = 12.sp),
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
                color = colors.accent,
                fontSize = dayFontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = "${today.hijriMonthName} ${today.hijriYear}",
            style = TextStyle(
                color = colors.primaryText,
                fontSize = monthFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = today.gregorianDate,
            style = TextStyle(color = colors.secondaryText, fontSize = gregorianFontSize, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

/**
 * The grid's header row: the two month arrows flanking the centred title line.
 *
 * Split out of [MonthGrid] when WG-12 threaded the widget's language into it for the chrome's
 * accessibility labels and pushed the function past detekt's length limit. It is a natural seam —
 * the header is the only part of the grid with a layout that depends on the reading direction, and
 * keeping the two mirroring branches together makes them easy to compare.
 */
@Composable
private fun MonthHeader(
    month: HijriMonthWidgetData,
    layoutRtl: Boolean,
    colors: WidgetColors,
    language: WidgetLanguage,
    actions: WidgetActions,
) {
    val prevAvailable = offsetHijriMonth(
        month.hijriYear, month.hijriMonth, HijriWidgetNavigation.STEP_PREVIOUS,
    ) != null
    val nextAvailable = offsetHijriMonth(
        month.hijriYear, month.hijriMonth, HijriWidgetNavigation.STEP_NEXT,
    ) != null

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (layoutRtl) {
            // In RTL "next" sits on the left and points left; "previous" sits on the right and
            // points right, matching the app's AutoMirrored header arrows.
            NavigationArrow(
                resId = R.drawable.ic_arrow_left,
                enabled = nextAvailable,
                action = actions.next,
                color = colors.primaryText,
                contentDescription = WidgetLocalization.ChromeLabels.nextMonth(language),
            )
            MonthTitle(
                month = month,
                resetAction = actions.reset,
                colors = colors,
                language = language,
            )
            NavigationArrow(
                resId = R.drawable.ic_arrow_right,
                enabled = prevAvailable,
                action = actions.prev,
                color = colors.primaryText,
                contentDescription = WidgetLocalization.ChromeLabels.previousMonth(language),
            )
        } else {
            NavigationArrow(
                resId = R.drawable.ic_arrow_left,
                enabled = prevAvailable,
                action = actions.prev,
                color = colors.primaryText,
                contentDescription = WidgetLocalization.ChromeLabels.previousMonth(language),
            )
            MonthTitle(
                month = month,
                resetAction = actions.reset,
                colors = colors,
                language = language,
            )
            NavigationArrow(
                resId = R.drawable.ic_arrow_right,
                enabled = nextAvailable,
                action = actions.next,
                color = colors.primaryText,
                contentDescription = WidgetLocalization.ChromeLabels.nextMonth(language),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun MonthGrid(
    month: HijriMonthWidgetData,
    todayEpochDay: Long,
    layoutRtl: Boolean,
    showAdjacentDays: Boolean,
    showCellBorders: Boolean,
    selectedDay: HijriDaySelection?,
    selectedEventName: String?,
    colors: WidgetColors,
    language: WidgetLanguage,
    actions: WidgetActions,
) {
    // One measurement for the whole grid, so every cell resolves the same sizes from the same number
    // rather than each re-deriving from its own slot.
    // The selection only marks a day in the month actually on screen (FD-09). Without this a stored
    // day from a month the user has navigated away from would mark whatever cell happens to have the
    // same day number in the new month — silently moving the mark, which is worse than dropping it.
    val selectionVisible = selectedDay != null &&
        selectedDay.isInMonth(month.hijriYear, month.hijriMonth)

    val cellSize = LocalSize.current.width / CalendarMonth.DAYS_IN_WEEK
    val hijriSize = GridTypography.hijriSizeSp(cellSize.value).sp
    val gregorianSize = GridTypography.gregorianSizeSp(cellSize.value).sp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        MonthHeader(
            month = month,
            layoutRtl = layoutRtl,
            colors = colors,
            language = language,
            actions = actions,
        )

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            month.weekdayHeaders.forEach { name ->
                Text(
                    text = name,
                    modifier = GlanceModifier.defaultWeight(),
                    // **Bold, and unconditionally.** The weight is a rendering choice with no access
                    // to the name's script, so it cannot be "bold for Urdu" — and the Urdu weekday
                    // names (`جمعرات`, `بدھ`) are exactly the ones that read as weak at 10sp Medium,
                    // where a longer word at a lighter weight disappears into the row above it. Same
                    // size, so the header does not change height.
                    style = TextStyle(
                        color = colors.secondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        // [HijriMonthWidgetData.weeksToRender] and [HijriMonthWidgetData.paintsDay] are the shared
        // definition of the grid's shape, so this composable, calendar-ui's MonthGrid and the Swift
        // grid cannot disagree about the row count or about which column the 1st sits under.
        //
        // A horizontal rule above the first row and between every row after it, plus the vertical
        // rules each cell draws on its trailing edge. Both directions were asked for: a grid with only
        // verticals divides the columns but leaves the rows to be counted by eye, which is most of the
        // work undone. One rule per row rather than a border per cell, for the reason on
        // [CellDivider].
        val weeks = month.weeksToRender(showAdjacentDays)
        weeks.forEach { week ->
            // Each row is a **Column carrying its own share of the height**, with the rule *inside* it.
            //
            // That is not tidiness — it is the whole fix. The rules used to be siblings of the rows in
            // this grid's Column, so six extra 1dp fixed-height children sat in the same pool the
            // `defaultWeight()` rows draw from. Turning the setting on therefore stole height from
            // *every* row at once, the rows overflowed the widget, and the bottom ones — the last days
            // of the month — were pushed out of view entirely. Making the widget taller did not help,
            // because the deficit was proportional.
            //
            // Nesting the rule means the 1dp comes out of that row's own allocation, so no row can be
            // squeezed to nothing by a sibling and the grid keeps all five or six rows at any size.
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (showCellBorders) RowDivider(colors)
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    week.forEachIndexed { column, cell ->
                        DayCell(
                            cell = cell,
                            todayEpochDay = todayEpochDay,
                            colors = colors,
                            hijriSize = hijriSize,
                            gregorianSize = gregorianSize,
                            // A rule after each column but the last (FD-04): one per column per row,
                            // not a border on all 42 cells, because Glance's cost is per view.
                            dividerAfter = showCellBorders && column < week.lastIndex,
                            // Whether this cell is inside a divided grid. Not derivable from
                            // `dividerAfter` alone: the first column has no rule on its *leading*
                            // edge either, so "has a trailing rule" is not the same question.
                            cellHasDividers = showCellBorders,
                            // `cell.isCurrentMonth` as well as the day number: without it a selection
                            // from another month would mark the same-numbered cell here, which is why
                            // `selectionVisible` is checked above *and* the cell's own month is.
                            isSelected = selectionVisible && cell.isCurrentMonth &&
                                cell.hijriDay == selectedDay!!.day,
                            // FD-09: the cell's own action. Null in every preview, because
                            // `WidgetActions` is null there — so "this preview cannot be tapped"
                            // stays one value rather than a fifth thing each preview must remember.
                            selectAction = selectActionFor(
                                actions,
                                month.hijriYear,
                                month.hijriMonth,
                                cell.hijriDay,
                                // A cell the widget is hiding must not become an invisible target.
                                paint = month.paintsDay(cell, showAdjacentDays),
                            ),
                            // Blank rather than removed: the cell keeps its slot so the 1st stays in
                            // its column under the right weekday heading.
                            paint = month.paintsDay(cell, showAdjacentDays),
                        )
                    }
                }
            }
        }

        EventFooter(
            // Resolved above, where the options are; a footer must not re-derive the calendar space.
            selectedEventName = selectedEventName,
            isLaidOut = selectedDay != null,
            colors = colors,
        )
    }
}

/**
 * One line naming the observance on the tapped day (FD-09).
 *
 * ## Why the line is reserved rather than conditional
 *
 * The footer is laid out **whenever a day is selected**, whether or not that day carries an
 * observance, and renders empty in the second case. A grid whose height changes depending on *which
 * day you tapped* is unusable: the thing you are aiming at moves under your finger. So the height is a
 * constant of "something is selected" and only the text varies.
 *
 * ## Why it is omitted on the compact layout
 *
 * `HijriWidgetRoot` picks the today card below 180dp wide, and there are no cells to tap there, so
 * there is nothing for a footer to describe. Adding a third size tier for it would be more machinery
 * than the feature is worth.
 *
 * ## Why it is empty rather than absent without a selection
 *
 * With no day selected the line is not laid out at all. A permanently-reserved blank strip at the
 * bottom of every widget is a cost every user pays for a feature most taps never use.
 */
@Composable
private fun EventFooter(
    selectedEventName: String?,
    isLaidOut: Boolean,
    colors: WidgetColors,
) {
    if (!isLaidOut) return

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            // Empty rather than absent when the day carries no observance — see the KDoc.
            text = selectedEventName.orEmpty(),
            style = TextStyle(
                color = colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

/**
 * The observance on [selection], resolved in the widget's own calendar space.
 *
 * `null` for a month the projection cannot build, which is the same degradation the grid itself makes
 * rather than a separate failure: a footer that named an observance for a month the grid cannot draw
 * would be worse than a blank line.
 */
internal fun eventFor(selection: HijriDaySelection, options: WidgetOptions): HijriEvent? {
    val month = buildHijriMonthWidgetData(
        hijriYear = selection.year,
        hijriMonth = selection.month,
        options = options,
    ) ?: return null

    // Matched by day number **and** in-month, because a month can hold two days with the same number
    // only if the projection is wrong — and a footer that marked the wrong one would be untraceable.
    val cell = month.days.firstOrNull { it.isCurrentMonth && it.hijriDay == selection.day }
    return cell?.let { HijriEvents.forDate(selection.month, it.hijriDay) }
}

/** Maps a widget's language onto the events table's, which is a `calendar-core` concept (FD-08). */
internal fun eventLanguage(language: WidgetLanguage): HijriEventLanguage = when (language) {
    WidgetLanguage.URDU -> HijriEventLanguage.URDU
    WidgetLanguage.ENGLISH -> HijriEventLanguage.ENGLISH
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
    language: WidgetLanguage,
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .clickableWhen(resetAction)
            .semantics { contentDescription = WidgetLocalization.ChromeLabels.goToCurrentMonth(language) },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                // Era-appended by the projection (FD-05): a Hijri year is four digits that look
                // exactly like a Gregorian one, and `1447 · September 2026` leaves a reader guessing
                // which calendar each number belongs to. The Gregorian half of this line carries its
                // own marker inside `gregorianMonthTitle`.
                text = "${month.hijriMonthName} ${month.hijriYearText}",
                style = TextStyle(
                    color = colors.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(
                text = " · ",
                style = TextStyle(
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(
                text = month.gregorianMonthTitle,
                style = TextStyle(
                    color = colors.secondaryText,
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
    color: ColorProvider,
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
                // A disabled arrow is dimmed. That used to be `color.copy(alpha = 0.35f)` on a
                // resolved colour; a `ColorProvider` cannot carry an alpha, and re-resolving one per
                // render is exactly what FD-07 removed — so the dimmed state is its own resource
                // (`widget_arrow_dimmed`) and therefore follows night mode for free.
                if (enabled) color else ColorProvider(R.color.widget_arrow_dimmed)
            ),
            modifier = GlanceModifier.size(40.dp)
        )
    }
}

/**
 * The two weights a day cell's own text is drawn at.
 *
 * Reported as "the days in the grid is not bold as well", right after the weekday header was made
 * bold. The Hijri figure was *already* `Bold` — which is why the report was confusing — so the real
 * reason a cell read as unbolded is that the Gregorian digit underneath it had no weight at all. The
 * eye weighs the pair, not the larger line: one bold number over a plain one reads as plain. Giving
 * the digit `Medium` is the change that makes the cell read as bold, and it costs no height.
 *
 * These are named constants rather than literals inline at the two `Text` calls so that
 * [GridCellWeightsTest] can assert the values the renderer *actually uses*. A test that re-declares
 * the weights it is checking would only be testing itself, and a `@Composable` cannot be called from
 * a JVM unit test — so the seam has to be the constant, not the composition.
 *
 * Deliberately not shared with [MonthHeader] or [MonthTitle]: those are separate rows with their own
 * hierarchy, and folding every weight in this file into one table would make a change to any one of
 * them look like a change to all of them.
 */
internal object GridCellWeights {
    /** The day figure — the cell's hero. */
    val HIJRI_DAY: FontWeight = FontWeight.Bold

    /** The Gregorian sub-digit, subordinated to the figure above it. */
    val GREGORIAN_DAY: FontWeight = FontWeight.Medium
}

// Suppressed on both counts: a cell's nine inputs are all independent renderer concerns, and grouping
// them into a bag would only move the list somewhere a reader has to open. The body is two branches
// (painted or blank) and a colour selection each.
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun RowScope.DayCell(
    cell: HijriDayWidgetData,
    todayEpochDay: Long,
    colors: WidgetColors,
    hijriSize: TextUnit,
    gregorianSize: TextUnit,
    dividerAfter: Boolean,
    cellHasDividers: Boolean,
    isSelected: Boolean,
    selectAction: Action?,
    paint: Boolean,
) {
    val isToday = paint && cell.gregorianEpochDay == todayEpochDay

    // A cell the widget is configured not to show keeps its slot so the rest of the week stays under
    // the right weekday headings, and draws nothing at all — not a dimmed digit, not the today fill.
    // It still draws its divider, or the row would stop short of the grid's right edge. There is no
    // action on a cell yet (FD-09); when day taps land, a hidden cell must stay untappable rather than
    // becoming an invisible target.
    if (!paint) {
        Row(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {}
            if (dividerAfter) CellDivider(colors)
        }
        return
    }

    // Today is a filled highlight like the in-app selected day: the accent container with its
    // "on-today" content colour. Everything else matches the app's precedence: out-of-month >
    // weekend > regular.
    val hijriColor = when {
        isToday -> colors.onTodayText
        !cell.isCurrentMonth -> colors.outOfMonthDay
        cell.isWeekend -> colors.weekendText
        else -> colors.primaryText
    }
    val gregorianColor = when {
        isToday -> colors.onTodayText
        !cell.isCurrentMonth -> colors.outOfMonthGregorianDay
        else -> colors.gregorianDay
    }

    Row(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .clickableWhen(selectAction)
            .padding(start = 1.dp, end = if (dividerAfter) 1.5.dp else 1.dp, top = 1.dp, bottom = 1.dp),
    ) {
        Box(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            // The selection badge, in the cell's top corner (FD-09).
            //
            // A **badge, not a ring or a fill**, and the choice is forced by three facts: Glance 1.2
            // has no border modifier at all; a `ColorProvider` cannot carry an alpha, so a tinted ring
            // is not expressible either; and today's mark is already a filled circle. A corner badge
            // composes with that fill instead of competing with it, so a day that is both today and
            // selected reads as today-with-a-badge rather than as one or the other.
            if (isSelected) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Horizontal.End,
                    verticalAlignment = Alignment.Vertical.Top,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .padding(3.dp)
                            .size(6.dp)
                            .background(colors.selectedDay)
                            .cornerRadius(3.dp),
                    ) {}
                }
            }
            if (isToday) {
                Box(
                    // With dividers on, the fill **covers the whole cell** and loses its rounded
                    // shape. An inset pill floating between a grid of rules looks like a badge laid
                    // on top of the grid rather than part of it; a block that fills the cell sits
                    // between the rules, which is what the rules are there to describe. With dividers
                    // off there is no grid to be part of, so the pill stays — it is the only thing
                    // marking the day at all.
                    modifier = if (cellHasDividers) {
                        GlanceModifier.fillMaxSize().background(colors.todayBackground)
                    } else {
                        GlanceModifier
                            .fillMaxSize()
                            .padding(1.dp)
                            .background(colors.todayBackground)
                            .cornerRadius(14.dp)
                    },
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
                        color = hijriColor,
                        fontSize = hijriSize,
                        fontWeight = GridCellWeights.HIJRI_DAY,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = cell.gregorianDayText,
                    style = TextStyle(
                        color = gregorianColor,
                        fontSize = gregorianSize,
                        fontWeight = GridCellWeights.GREGORIAN_DAY,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
            }
        }
        if (dividerAfter) CellDivider(colors)
    }
}

/**
 * A full-width horizontal rule, above the first row of days and between each pair of rows (FD-04).
 *
 * The counterpart to [CellDivider]. Together they make the grid a *grid*: verticals alone divide the
 * columns but leave the reader counting rows by eye, which is most of the work the divider was meant
 * to do.
 *
 * One rule per row — at most six — rather than a top and bottom edge on all 42 cells.
 */
@Composable
private fun RowDivider(colors: WidgetColors) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.cellBorder),
    ) {}
}

/**
 * The hairline between two cells (FD-04).
 *
 * One rule per column per row rather than a border on all 42 cells: Glance's cost is per view, and a
 * `RemoteViews` with 42 extra `Box`es is a heavier thing for the launcher to bind than the month needs
 * to be. Visually identical, and it is why the option is a render-time flag rather than something the
 * projection had to carry per cell.
 */
@Composable
private fun RowScope.CellDivider(colors: WidgetColors) {
    Box(
        modifier = GlanceModifier
            .width(1.dp)
            .fillMaxHeight()
            .background(colors.cellBorder),
    ) {}
}
