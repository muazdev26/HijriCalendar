package com.muazdev.hijricalendar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.muazdev.hijricalendar.sample.widget.HIJRI_DEEP_LINK_TODAY
import com.muazdev.hijricalendar.sample.widget.HijriWidgetRefreshGate
import com.muazdev.hijricalendar.shared.CalendarScreen
import com.muazdev.hijricalendar.shared.UrduCalendarLabels
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        HijriWidgetRefreshGate.setAppForeground(true)
    }

    override fun onStop() {
        HijriWidgetRefreshGate.setAppForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val viewModel: CalendarViewModel = koinViewModel()

                // Widget deep link (hijricalendar://today) → jump the calendar to today.
                val deepLinkJumpsToToday = remember(intent) {
                    intent?.data?.toString()?.startsWith(HIJRI_DEEP_LINK_TODAY) == true
                }
                LaunchedEffect(deepLinkJumpsToToday) {
                    if (deepLinkJumpsToToday) viewModel.goToToday()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CalendarScreen(
                        state = viewModel.state,
                        dateDisplayMode = viewModel.dateDisplayMode,
                        onDateDisplayModeChange = viewModel::onDateDisplayModeChange,
                        onJumpToToday = viewModel::goToToday,
                        labels = UrduCalendarLabels,
                        showAdjustmentSelector = true,
                        onAdjustmentDaysChange = viewModel::onAdjustmentDaysChange,
                        showPakistanToggle = true,
                        onPakistanDatesChange = viewModel::onPakistanDatesChange,
                        showMonthLengthSettings = true,
                        onMonthLengthOverridesChanged = viewModel::persistMonthLengthOverrides,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}