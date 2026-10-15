package com.muazdev.hijricalendar.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.muazdev.hijricalendar.widget.glance.HIJRI_DEEP_LINK_TODAY
import com.muazdev.hijricalendar.shared.CalendarScreen
import com.muazdev.hijricalendar.shared.UrduCalendarLabels
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val viewModel: CalendarViewModel = koinViewModel()

                // In-app settings changes are mirrored onto the placed widgets (point 4). The
                // ViewModel has no Context by design, so the write/render pair is wired here, next
                // to the callbacks that carry the values out of the shared screen.
                val widgetSync = remember { WidgetSettingsSync(applicationContext) }

                // Widget deep link (hijricalendar://today) → jump the calendar to today.
                val deepLinkJumpsToToday = remember(intent) {
                    intent?.data?.toString()?.startsWith(HIJRI_DEEP_LINK_TODAY) == true
                }
                LaunchedEffect(Unit) {
                    widgetSync.sync(viewModel.state.adjustmentDays, viewModel.state.pakistanDates, viewModel.state.monthLengths)
                }
                LaunchedEffect(deepLinkJumpsToToday) {
                    if (deepLinkJumpsToToday) viewModel.goToToday()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        WidgetCatalogTopBar(onAddWidgetsClick = {
                            startActivity(Intent(this, WidgetCatalogActivity::class.java))
                        })
                    },
                ) { innerPadding ->
                    CalendarScreen(
                        state = viewModel.state,
                        dateDisplayMode = viewModel.dateDisplayMode,
                        onDateDisplayModeChange = viewModel::onDateDisplayModeChange,
                        onJumpToToday = viewModel::goToToday,
                        labels = UrduCalendarLabels,
                        showAdjustmentSelector = true,
                        onAdjustmentDaysChange = { days ->
                            viewModel.onAdjustmentDaysChange(days)
                            widgetSync.sync(days, viewModel.state.pakistanDates, viewModel.state.monthLengths)
                        },
                        showPakistanToggle = true,
                        onPakistanDatesChange = { pakistan ->
                            viewModel.onPakistanDatesChange(pakistan)
                            widgetSync.sync(viewModel.state.adjustmentDays, pakistan, viewModel.state.monthLengths)
                        },
                        showMonthLengthSettings = true,
                        onMonthLengthOverridesChanged = {
                            viewModel.persistMonthLengthOverrides()
                            widgetSync.sync(viewModel.state.adjustmentDays, viewModel.state.pakistanDates, viewModel.state.monthLengths)
                        },
                        showAdjacentDaysToggle = true,
                        showWeekendPatternToggle = true,
                        showCellBordersToggle = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun WidgetCatalogTopBar(onAddWidgetsClick: () -> Unit) {
    TopAppBar(
        title = { Text("Hijri Calendar") },
        actions = {
            TextButton(onClick = onAddWidgetsClick) { Text("Add widget") }
        },
    )
}