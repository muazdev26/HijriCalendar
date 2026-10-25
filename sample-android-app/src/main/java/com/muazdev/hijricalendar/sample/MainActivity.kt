package com.muazdev.hijricalendar.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muazdev.hijricalendar.shared.CalendarScreen
import com.muazdev.hijricalendar.shared.UrduCalendarLabels
import com.muazdev.hijricalendar.widget.glance.HIJRI_DEEP_LINK_TODAY
import com.muazdev.hijricalendar.widgetdata.WeekendPattern
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is applied per *chosen* theme, not per device theme, so forcing Dark on a light
        // phone does not leave dark status-bar icons on a dark app bar. Re-applied when the mode changes.
        applySampleSystemBars(SampleThemeStore.read(this))
        val deepLinkJumpsToToday = intent?.data?.toString()?.startsWith(HIJRI_DEEP_LINK_TODAY) == true
        setContent {
            // Held here rather than in the ViewModel: the theme belongs to the app shell, and the
            // widget activities read the same stored value. `remember`, not `rememberSaveable`: the
            // store is the source of truth, so a recreated activity re-reads what was last written.
            var themeMode by remember { mutableStateOf(SampleThemeStore.read(this)) }
            HijriSampleTheme(themeMode) {
                val viewModel: CalendarViewModel = koinViewModel()

                // In-app settings changes are mirrored onto the placed widgets (point 4). The
                // ViewModel has no Context by design, so the write/render pair is wired here, next
                // to the callbacks that carry the values out of the shared screen.
                val widgetSync = remember { WidgetSettingsSync(applicationContext) }

                // Widget deep link (hijricalendar://today) → jump the calendar to today.
                LaunchedEffect(deepLinkJumpsToToday) {
                    if (deepLinkJumpsToToday) viewModel.goToToday()
                }

                CalendarApp(
                    viewModel = viewModel,
                    widgetSync = widgetSync,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        SampleThemeStore.write(this, mode)
                        applySampleSystemBars(mode)
                    },
                    onAddWidgetsClick = {
                        startActivity(Intent(this, WidgetCatalogActivity::class.java))
                    },
                )
            }
        }
    }
}

@Composable
private fun CalendarApp(
    viewModel: CalendarViewModel,
    widgetSync: WidgetSettingsSync,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAddWidgetsClick: () -> Unit,
) {
    val wiring = rememberWidgetSyncWiring(viewModel, widgetSync)
    val syncIfEnabled = wiring.syncIfEnabled
    LaunchedEffect(Unit) {
        syncIfEnabled()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CalendarTopBar(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onAddWidgetsClick = onAddWidgetsClick,
            )
        },
    ) { innerPadding ->
        CalendarScreen(
            state = viewModel.state,
            dateDisplayMode = viewModel.dateDisplayMode,
            onDateDisplayModeChange = viewModel::onDateDisplayModeChange,
            primaryDate = viewModel.primaryDate,
            onPrimaryDateChange = { primary ->
                viewModel.onPrimaryDateChange(primary)
                syncIfEnabled()
            },
            onJumpToToday = viewModel::goToToday,
            labels = UrduCalendarLabels,
            showAdjustmentSelector = true,
            onAdjustmentDaysChange = { days ->
                viewModel.onAdjustmentDaysChange(days)
                syncIfEnabled()
            },
            showPakistanToggle = true,
            onPakistanDatesChange = { pakistan ->
                viewModel.onPakistanDatesChange(pakistan)
                syncIfEnabled()
            },
            showMonthLengthSettings = true,
            onMonthLengthOverridesChanged = {
                viewModel.persistMonthLengthOverrides()
                syncIfEnabled()
            },
            showAdjacentDaysToggle = true,
            onShowAdjacentDaysChange = { show ->
                viewModel.onShowAdjacentDaysChange(show)
                syncIfEnabled()
            },
            showWeekendPatternToggle = true,
            onWeekendPatternChange = { pattern ->
                viewModel.onWeekendPatternChange(pattern)
                syncIfEnabled()
            },
            showCellBordersToggle = true,
            onShowCellBordersChange = { show ->
                viewModel.onShowCellBordersChange(show)
                syncIfEnabled()
            },
            showAutoSyncToggle = true,
            autoSyncToWidget = viewModel.autoSyncToWidget,
            onAutoSyncToWidgetChange = viewModel::onAutoSyncToWidgetChange,
            // Ungated on purpose: "Sync now" is the user asking for it.
            onSyncNow = { widgetSync.sync(wiring.currentWidgetSettings()) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAddWidgetsClick: () -> Unit,
) {
    TopAppBar(
        title = { Text("Hijri Calendar") },
        actions = {
            ThemeModeButton(mode = themeMode, onModeChange = onThemeModeChange)
            TextButton(onClick = onAddWidgetsClick) { Text("Add widget") }
        },
    )
}

/**
 * The widget-mirror wiring every settings callback shares: one settings value read fresh per sync,
 * plus the auto-sync gate.
 *
 * Extracted so `CalendarApp` stays a description of the screen — and inside detekt's method-length
 * budget, which the theme parameters pushed it past.
 *
 * A settings value is read from the ViewModel **when the sync runs**, not when the wiring is created:
 * the screen mutates `state` before invoking a callback, so a callback that writes first and syncs
 * second always mirrors the value the user just chose.
 */
private class WidgetSyncWiring(
    val currentWidgetSettings: () -> WidgetSettings,
    val syncIfEnabled: () -> Unit,
)

@Composable
private fun rememberWidgetSyncWiring(
    viewModel: CalendarViewModel,
    widgetSync: WidgetSettingsSync,
): WidgetSyncWiring {
    val currentWidgetSettings: () -> WidgetSettings = {
        WidgetSettings(
            adjustmentDays = viewModel.state.adjustmentDays,
            pakistanDates = viewModel.state.pakistanDates,
            monthLengths = viewModel.state.monthLengths,
            // `of` answers null for a set the pattern enum does not describe; the sample only ever
            // sets one it does, and the default keeps the mirror honest if a future in-app option
            // does not have a widget twin yet.
            weekendPattern = WeekendPattern.of(viewModel.state.weekendDays)
                ?: WeekendPattern.FRIDAY_SATURDAY,
            showAdjacentDays = viewModel.state.showAdjacentDays,
            showCellBorders = viewModel.state.showCellBorders,
            dateDisplayMode = viewModel.dateDisplayMode,
            primaryDate = viewModel.primaryDate,
        )
    }
    return remember(viewModel, widgetSync) {
        WidgetSyncWiring(
            currentWidgetSettings = currentWidgetSettings,
            syncIfEnabled = {
                if (viewModel.autoSyncToWidget) widgetSync.sync(currentWidgetSettings())
            },
        )
    }
}