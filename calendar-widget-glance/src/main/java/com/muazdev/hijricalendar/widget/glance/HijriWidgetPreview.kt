package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import com.muazdev.hijricalendar.widgetdata.HijriYearMonth
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import kotlinx.coroutines.CancellationException

/**
 * The Context a Glance composition must run against, normalized to the application so a settings
 * screen's Activity cannot be retained.
 *
 * Every `GlanceAppWidget.compose(...)` opens a layout state store whose file producer captures the
 * Context it was given, and Glance caches those stores in a process-global map (`GlanceState`) that
 * is only ever pruned when a real widget is deleted. `compose()` also mints a fresh random *fake*
 * app-widget id per call, so each preview composition that ran against an Activity added another
 * permanent entry holding that Activity — the settings screen leaked its Activity on every visit.
 * The application Context resolves the same library resources, and the resulting [RemoteViews] are
 * still applied to the real Activity-bound view, so nothing about the preview changes.
 */
private fun Context.glanceComposeContext(): Context = applicationContext

/**
 * A non-interactive clone of [HijriCalendarWidget] used only for the settings-screen live preview.
 * It renders from an explicit [WidgetOptions] instead of persisted per-widget
 * config, so the preview reflects in-progress edits, and it passes no actions to
 * [HijriWidgetRoot], so tapping the preview cannot open the app or mutate the real widget.
 */
internal class HijriCalendarWidgetPreview(
    private val options: WidgetOptions,
    private val viewedMonth: HijriYearMonth?,
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (options.source.pakistan) {
            // The preview composes on the main dispatcher; never let the first Pakistan render
            // build the century table inline here either.
            PakistanWarmUp.ensureWarm()
        }
        val data = buildRenderData(context, options, viewedMonth)
        val colors = WidgetColors.from(context)
        provideContent {
            HijriWidgetRoot(
                monthData = data.monthData,
                todayHijri = data.todayHijri,
                todayEpochDay = data.todayEpochDay,
                layoutRtl = data.layoutRtl,
                showAdjacentDays = data.showAdjacentDays,
                colors = colors,
                language = options.language,
                // Non-interactive by construction: the settings preview shows what the widget will
                // look like, and tapping it must not open the app or move the real widget's month.
                actions = WidgetActions(),
            )
        }
    }
}

/**
 * Live preview of the actual widget: composes the real Glance tree (same shared projection and
 * layout as the home-screen widget) and hosts the resulting [RemoteViews] in an [AndroidView].
 * Recomposed — and re-composed — whenever [options], [viewedMonth] or [size] change, so every
 * settings touch updates the preview.
 *
 * Public so a host app's settings screen can show what the widget will look like live, with the
 * same [WidgetOptions] its controls edit, without the settings screen and the
 * real widget ever drifting apart.
 */
@Composable
public fun HijriWidgetLivePreview(
    options: WidgetOptions,
    viewedMonth: HijriYearMonth?,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remoteViews by remember(options, viewedMonth, size) { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(options, viewedMonth, size) {
        try {
            remoteViews = HijriCalendarWidgetPreview(options, viewedMonth)
                .compose(context.glanceComposeContext(), size = size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            HijriWidgetRefreshLog.d("preview", "compose failed: ${error.message}")
        }
    }

    val rendered = remoteViews
    if (rendered == null) {
        Box(modifier = modifier.background(Color.Transparent))
    } else {
        key(rendered) {
            AndroidView(
                modifier = modifier,
                factory = { ctx -> rendered.apply(ctx, null) },
            )
        }
    }
}

internal class HijriTodayWidgetPreview(
    private val options: WidgetOptions
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
        // The stable preview id, never `id.toString()` (WG-04b): `compose()` mints a fresh random
        // fake app-widget id per call, so keying on it inserted an entry no future read could ever
        // hit — the settings screen grew the cache without bound and got nothing for the cost.
        val data = HijriWidgetRenderCache.today(
            glanceId = HijriWidgetRenderCache.PREVIEW_CACHE_ID,
            options = options,
            todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
        )
        provideContent {
            HijriTodayRoot(
                today = data,
                colors = colors,
                openAction = null,
                language = options.language,
                layoutRtl = computeLayoutRtl(context, options.language),
            )
        }
    }
}

@Composable
public fun HijriTodayWidgetLivePreview(
    options: WidgetOptions,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remoteViews by remember(options, size) { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(options, size) {
        try {
            remoteViews = HijriTodayWidgetPreview(options)
                .compose(context.glanceComposeContext(), size = size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            HijriWidgetRefreshLog.d("preview-today", "compose failed: ${error.message}")
        }
    }

    val rendered = remoteViews
    if (rendered == null) {
        Box(modifier = modifier.background(Color.Transparent))
    } else {
        key(rendered) {
            AndroidView(
                modifier = modifier,
                factory = { ctx -> rendered.apply(ctx, null) },
            )
        }
    }
}

/** Non-interactive clone of [HijriDateWidget] for the 1x1 Hijri tile's settings preview. */
internal class HijriDateWidgetPreview(
    private val options: WidgetOptions
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
        // The stable preview id — see the note in `HijriTodayWidgetPreview`.
        val today = HijriWidgetRenderCache.today(
            glanceId = HijriWidgetRenderCache.PREVIEW_CACHE_ID,
            options = options,
            todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
        )
        provideContent {
            DateTileRoot(
                dayText = today?.hijriDayText,
                monthText = today?.hijriMonthName,
                weekdayText = today?.weekdayName,
                colors = colors,
                openAction = null,
                language = options.language,
            )
        }
    }
}

/** Non-interactive clone of [GregorianDateWidget] for the 1x1 Gregorian tile's settings preview. */
internal class GregorianDateWidgetPreview(
    private val options: WidgetOptions
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
        // The stable preview id — see the note in `HijriTodayWidgetPreview`.
        val today = HijriWidgetRenderCache.today(
            glanceId = HijriWidgetRenderCache.PREVIEW_CACHE_ID,
            options = options,
            todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
        )
        provideContent {
            DateTileRoot(
                dayText = today?.gregorianDayText,
                monthText = today?.gregorianMonthName,
                weekdayText = today?.weekdayName,
                colors = colors,
                openAction = null,
                language = options.language,
            )
        }
    }
}

/**
 * Live preview of the 1x1 Hijri date tile: composes the real [DateTileRoot] layout and hosts the
 * resulting [RemoteViews] in an [AndroidView]. Public so a host app's settings screen can show the
 * tile live from the same [WidgetOptions] its controls edit.
 */
@Composable
public fun HijriDateWidgetLivePreview(
    options: WidgetOptions,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remoteViews by remember(options, size) { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(options, size) {
        try {
            remoteViews = HijriDateWidgetPreview(options)
                .compose(context.glanceComposeContext(), size = size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            HijriWidgetRefreshLog.d("preview-hijri-date", "compose failed: ${error.message}")
        }
    }

    val rendered = remoteViews
    if (rendered == null) {
        Box(modifier = modifier.background(Color.Transparent))
    } else {
        key(rendered) {
            AndroidView(
                modifier = modifier,
                factory = { ctx -> rendered.apply(ctx, null) },
            )
        }
    }
}

/**
 * Live preview of the 1x1 Gregorian date tile, mirroring [HijriDateWidgetLivePreview] on the
 * Gregorian side.
 */
@Composable
public fun GregorianDateWidgetLivePreview(
    options: WidgetOptions,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remoteViews by remember(options, size) { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(options, size) {
        try {
            remoteViews = GregorianDateWidgetPreview(options)
                .compose(context.glanceComposeContext(), size = size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            HijriWidgetRefreshLog.d("preview-gregorian-date", "compose failed: ${error.message}")
        }
    }

    val rendered = remoteViews
    if (rendered == null) {
        Box(modifier = modifier.background(Color.Transparent))
    } else {
        key(rendered) {
            AndroidView(
                modifier = modifier,
                factory = { ctx -> rendered.apply(ctx, null) },
            )
        }
    }
}
