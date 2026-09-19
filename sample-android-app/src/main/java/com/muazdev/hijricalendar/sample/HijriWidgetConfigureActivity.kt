package com.muazdev.hijricalendar.sample

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.sample.widget.HijriCalendarWidgetReceiver
import com.muazdev.hijricalendar.sample.widget.HijriWidgetConfig
import com.muazdev.hijricalendar.widgetdata.NumeralStyle

/**
 * Optional configuration screen shown by the launcher when a widget is added (hosts honoring
 * `configuration_optional` may skip it) or when the user long-presses and picks "Settings".
 * Persists per-widget options and then asks the widget provider to re-render.
 */
class HijriWidgetConfigureActivity : ComponentActivity() {

    private val appWidgetId: Int by lazy {
        intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val glanceId: String by lazy {
        runCatching {
            GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId).toString()
        }.getOrNull() ?: appWidgetId.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HijriWidgetConfigScreen(
                        initial = HijriWidgetConfig.load(this, glanceId),
                        onCancel = { finish() },
                        onSave = { options ->
                            HijriWidgetConfig.save(this, glanceId, options)
                            sendUpdateAndFinish()
                        },
                    )
                }
            }
        }
    }

    private fun sendUpdateAndFinish() {
        val updateIntent = Intent(this, HijriCalendarWidgetReceiver::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        sendBroadcast(updateIntent)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

@Composable
private fun HijriWidgetConfigScreen(
    initial: HijriWidgetConfig.WidgetOptions,
    onCancel: () -> Unit,
    onSave: (HijriWidgetConfig.WidgetOptions) -> Unit,
) {
    var adjustmentDays by rememberSaveable { mutableIntStateOf(initial.adjustmentDays) }
    var numeralStyle by rememberSaveable {
        mutableStateOf(initial.numeralStyle)
    }
    var firstDayIndex by rememberSaveable { mutableIntStateOf(initial.firstDayOfWeekIndex) }
    var pinnedYear by rememberSaveable { mutableStateOf(initial.pinnedYear) }
    var pinnedMonth by rememberSaveable { mutableStateOf(initial.pinnedMonth) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(text = "Hijri Calendar Widget", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "The widget rolls over automatically at local midnight. " +
                "Use the adjustment to align the grid with local moon sighting.",
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionTitle("Moon-sighting adjustment")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (-2..2).forEach { value ->
                OutlinedButton(
                    onClick = { adjustmentDays = value },
                ) {
                    Text(if (value > 0) "+$value" else value.toString())
                }
            }
        }
        Text(
            text = offsetLabel(adjustmentDays),
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Numerals")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NumeralStyle.entries.forEach { style ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = numeralStyle == style,
                        onClick = { numeralStyle = style },
                    )
                    Text(style.name.replace('_', ' '))
                }
            }
        }

        SectionTitle("First day of week")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeekDay.entries.forEachIndexed { index, day ->
                OutlinedButton(
                    onClick = { firstDayIndex = index },
                ) {
                    Text(day.shortName)
                }
            }
        }

        SectionTitle("Fixed month (optional)")
        Text(
            text = "Defaults to the current Hijri month. Pick a year and month to pin the grid.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { pinnedYear = if (pinnedYear == null) 1447 else null },
            ) {
                Text(pinnedYear?.toString() ?: "Auto year")
            }
            OutlinedButton(
                onClick = { pinnedMonth = if (pinnedMonth == null) 9 else null },
            ) {
                Text(pinnedMonth?.toString() ?: "Auto month")
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.End),
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = {
                    onSave(
                        HijriWidgetConfig.WidgetOptions(
                            adjustmentDays = adjustmentDays,
                            numeralStyle = numeralStyle,
                            firstDayOfWeekIndex = firstDayIndex,
                            pinnedYear = pinnedYear,
                            pinnedMonth = pinnedMonth,
                        ),
                    )
                },
            ) { Text("Save") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

private fun offsetLabel(offset: Int): String = when (offset) {
    0 -> "Umm al-Qura"
    else -> if (offset > 0) "Show Hijri ${offset} day(s) ahead" else "Show Hijri ${-offset} day(s) behind"
}