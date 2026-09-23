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
import kotlinx.coroutines.CancellationException

/**
 * A non-interactive clone of [HijriCalendarWidget] used only for the settings-screen live preview.
 * It renders from an explicit [HijriWidgetConfig.WidgetOptions] instead of persisted per-widget
 * config, so the preview reflects in-progress edits, and it passes no actions to
 * [HijriWidgetRoot], so tapping the preview cannot open the app or mutate the real widget.
 */
internal class HijriCalendarWidgetPreview(
    private val options: HijriWidgetConfig.WidgetOptions,
    private val viewedMonth: Pair<Int, Int>?,
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
                colors = colors,
                openAction = null,
                prevAction = null,
                nextAction = null,
                resetAction = null,
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
 * same [HijriWidgetConfig.WidgetOptions] its controls edit, without the settings screen and the
 * real widget ever drifting apart.
 */
@Composable
fun HijriWidgetLivePreview(
    options: HijriWidgetConfig.WidgetOptions,
    viewedMonth: Pair<Int, Int>?,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remoteViews by remember(options, viewedMonth, size) { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(options, viewedMonth, size) {
        try {
            remoteViews = HijriCalendarWidgetPreview(options, viewedMonth).compose(context, size = size)
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
    private val options: HijriWidgetConfig.WidgetOptions
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
        val data = HijriWidgetRenderCache.today(
            glanceId = id.toString(),
            options = options,
            todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
        )
        provideContent {
            HijriTodayRoot(
                today = data,
                colors = colors,
                openAction = null,
                layoutRtl = computeLayoutRtl(context, options.language),
            )
        }
    }
}

@Composable
fun HijriTodayWidgetLivePreview(
    options: HijriWidgetConfig.WidgetOptions,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remoteViews by remember(options, size) { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(options, size) {
        try {
            remoteViews = HijriTodayWidgetPreview(options).compose(context, size = size)
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
    private val options: HijriWidgetConfig.WidgetOptions
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
        val today = HijriWidgetRenderCache.today(
            glanceId = id.toString(),
            options = options,
            todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
        )
        provideContent {
            DateTileRoot(
                dayText = today?.hijriDayText,
                monthText = today?.hijriMonthName,
                colors = colors,
                openAction = null,
            )
        }
    }
}

/** Non-interactive clone of [GregorianDateWidget] for the 1x1 Gregorian tile's settings preview. */
internal class GregorianDateWidgetPreview(
    private val options: HijriWidgetConfig.WidgetOptions
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
        val today = HijriWidgetRenderCache.today(
            glanceId = id.toString(),
            options = options,
            todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
        )
        provideContent {
            DateTileRoot(
                dayText = today?.gregorianDayText,
                monthText = today?.gregorianMonthName,
                colors = colors,
                openAction = null,
            )
        }
    }
}

/**
 * Live preview of the 1x1 Hijri date tile: composes the real [DateTileRoot] layout and hosts the
 * resulting [RemoteViews] in an [AndroidView]. Public so a host app's settings screen can show the
 * tile live from the same [HijriWidgetConfig.WidgetOptions] its controls edit.
 */
@Composable
fun HijriDateWidgetLivePreview(
    options: HijriWidgetConfig.WidgetOptions,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remoteViews by remember(options, size) { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(options, size) {
        try {
            remoteViews = HijriDateWidgetPreview(options).compose(context, size = size)
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
fun GregorianDateWidgetLivePreview(
    options: HijriWidgetConfig.WidgetOptions,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remoteViews by remember(options, size) { mutableStateOf<RemoteViews?>(null) }

    LaunchedEffect(options, size) {
        try {
            remoteViews = GregorianDateWidgetPreview(options).compose(context, size = size)
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
