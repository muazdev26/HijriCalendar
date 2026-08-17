package com.muazdev.hijricalendar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.muazdev.hijricalendar.shared.CalendarScreen
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val viewModel: CalendarViewModel = koinViewModel()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CalendarScreen(
                        state = viewModel.state,
                        dateDisplayMode = viewModel.dateDisplayMode,
                        onDateDisplayModeChange = viewModel::onDateDisplayModeChange,
                        onJumpToToday = viewModel::goToToday,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}
