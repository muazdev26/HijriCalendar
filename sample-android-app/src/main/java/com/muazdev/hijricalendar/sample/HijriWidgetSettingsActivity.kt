package com.muazdev.hijricalendar.sample

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.muazdev.hijricalendar.widget.glance.HijriWidgetConfig
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Base for every per-widget configuration activity owned by the sample app. The library ships no
 * configure activity; a host app registers one per widget in its provider infos and builds it
 * against the public `calendar-widget-glance` API ([HijriWidgetConfig], [HijriWidgetRefresher],
 * and the live-preview composables).
 *
 * Every control applies and persists immediately — there is no Save step; leaving the screen is a
 * plain dismiss. A live preview at the top re-renders the actual widget (same shared projection
 * and Glance layout) on every touch, and each change is pushed to the real widget through
 * [HijriWidgetRefresher] so the home screen updates in real time.
 *
 * During an add-to-home flow the host passes [AppWidgetManager.EXTRA_APPWIDGET_ID]; options are
 * saved to that instance (and re-seeded into the family mirror by [HijriWidgetConfig.save]).
 * Without a widget id (catalog "Settings" launch) the family mirror is written directly so the
 * strip and 1x1 tiles pick up the choices.
 */
abstract class HijriWidgetSettingsActivity : ComponentActivity() {

    abstract val kind: WidgetKind

    private val appWidgetId: Int by lazy {
        intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val glanceId: GlanceId? by lazy {
        runCatching { GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId) }.getOrNull()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A dismiss (back) is a valid completion: settings were already applied on touch.
        onBackPressedDispatcher.addCallback(this) { finishWithResult() }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The stored options live in Glance's preferences state store and are read
                    // asynchronously; hold a null "not loaded yet" slot until the config has been read.
                    var loaded by remember { mutableStateOf<HijriWidgetConfig.WidgetOptions?>(null) }
                    LaunchedEffect(appWidgetId) {
                        loaded = resolveInitial()
                    }
                    loaded?.let { initial ->
                        HijriWidgetSettingsScreen(
                            kind = kind,
                            initial = initial,
                            onApply = { options -> apply(options) },
                            onRefreshNow = {
                                HijriWidgetRefresher.refreshAllAsync(
                                    this@HijriWidgetSettingsActivity,
                                    reason = "settings-refresh-now",
                                    bypassGate = true,
                                    bypassDedupe = true,
                                )
                            },
                            onDone = { finishWithResult() },
                        )
                    }
                }
            }
        }
    }

    private suspend fun resolveInitial(): HijriWidgetConfig.WidgetOptions =
        glanceId?.let { HijriWidgetConfig.load(this, it) }
            ?: HijriWidgetConfig.loadFamily(this)

    private fun apply(options: HijriWidgetConfig.WidgetOptions) {
        scope.launch {
            val id = glanceId
            if (id != null) {
                val previous = HijriWidgetConfig.load(this@HijriWidgetSettingsActivity, id)
                val pinnedChanged =
                    previous.pinnedYear != options.pinnedYear ||
                            previous.pinnedMonth != options.pinnedMonth
                HijriWidgetConfig.save(this@HijriWidgetSettingsActivity, id, options)
                // A pinned month is an explicit "show this month"; drop any transient on-widget
                // navigation state so the grid honours the new pin (a no-op for the strip and the
                // 1x1 tiles, which have no navigation state).
                if (pinnedChanged) {
                    HijriWidgetConfig.clearViewedMonth(this@HijriWidgetSettingsActivity, id)
                }
            } else {
                // No widget id yet (launched from the catalog without targeting an instance)
                // — persist the family mirror directly so the strip and tiles follow the choices.
                HijriWidgetConfig.saveFamily(this@HijriWidgetSettingsActivity, options)
            }
            // Re-render the whole family (the edited widget plus its siblings, which follow the
            // family options mirror) so every widget updates immediately.
            HijriWidgetRefresher.refreshAllAsync(
                this@HijriWidgetSettingsActivity,
                reason = "settings-apply",
                bypassGate = true,
                bypassDedupe = true,
            )
        }
    }

    private fun finishWithResult() {
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

/** Grid widget configuration screen, pointed at by the sample's `res/xml` grid widget-info. */
class HijriWidgetConfigureActivity : HijriWidgetSettingsActivity() {
    override val kind: WidgetKind get() = WidgetKind.GRID
}

/** Today-strip configuration screen, pointed at by the sample's `res/xml` today widget-info. */
class HijriTodayWidgetConfigureActivity : HijriWidgetSettingsActivity() {
    override val kind: WidgetKind get() = WidgetKind.TODAY
}

/** 1x1 Hijri-date tile configuration screen, pointed at by the sample's `res/xml` tile widget-info. */
class HijriDateWidgetConfigureActivity : HijriWidgetSettingsActivity() {
    override val kind: WidgetKind get() = WidgetKind.HIJRI_DATE
}

/** 1x1 Gregorian-date tile configuration screen, pointed at by the sample's `res/xml` tile widget-info. */
class GregorianDateWidgetConfigureActivity : HijriWidgetSettingsActivity() {
    override val kind: WidgetKind get() = WidgetKind.GREGORIAN_DATE
}