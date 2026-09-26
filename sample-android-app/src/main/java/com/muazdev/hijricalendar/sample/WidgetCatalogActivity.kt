package com.muazdev.hijricalendar.sample

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import android.appwidget.AppWidgetManager
import androidx.compose.ui.draw.clip
import com.muazdev.hijricalendar.widget.glance.GregorianDateWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.GregorianDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriCalendarWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriDateWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriTodayWidgetLivePreview
import com.muazdev.hijricalendar.widget.glance.HijriTodayWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriWidgetConfig
import com.muazdev.hijricalendar.widget.glance.HijriWidgetLivePreview
import com.muazdev.hijricalendar.widgetdata.WidgetOptions

/**
 * "Add a widget" catalog: lists every home-screen widget the app ships with, shows each one's
 * live preview, and lets the user add it to the home screen with one tap
 * ([AppWidgetManager.requestPinAppWidget], the launcher runs the widget's own configuration
 * activity as part of the pin flow). A [WidgetAddSuccessReceiver] confirms placement.
 */
class WidgetCatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WidgetCatalogScreen(
                    onBack = { finish() },
                    onAdd = ::requestAdd,
                    onSettings = ::openSettings,
                )
            }
        }
    }

    private fun requestAdd(widget: CatalogWidget) {
        val manager = AppWidgetManager.getInstance(this)
        val component = ComponentName(this, widget.provider)
        val accepted = runCatching {
            manager.requestPinAppWidget(
                component,
                null,
                PendingIntent.getBroadcast(
                    this,
                    widget.requestCode,
                    Intent(this, WidgetAddSuccessReceiver::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widget.label),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }.getOrDefault(false)
        if (!accepted) {
            Toast.makeText(
                this,
                "\u201C${widget.label}\u201D — long-press your home screen, tap Widgets and pick it from the list.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun openSettings(widget: CatalogWidget) {
        startActivity(Intent(this, widget.settingsActivity))
    }
}

/** Confirm-only receiver: the `requestPinAppWidget` success callback, fired on a successful place. */
class WidgetAddSuccessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
            ?: "Widget"
        Toast.makeText(context, "$label added to your home screen.", Toast.LENGTH_SHORT).show()
    }
}

private data class CatalogWidget(
    val kind: WidgetKind,
    val provider: Class<out androidx.glance.appwidget.GlanceAppWidgetReceiver>,
    val settingsActivity: Class<out android.app.Activity>,
    val label: String,
    val description: String,
    val sizeHint: String,
    val requestCode: Int,
)

// Provider classes are compile-time references into the glance library; the sample overrides the
// provider infos in res/xml to point each widget at its own configure activity.
private val catalogWidgets = listOf(
    CatalogWidget(
        kind = WidgetKind.GRID,
        provider = HijriCalendarWidgetReceiver::class.java,
        settingsActivity = HijriWidgetConfigureActivity::class.java,
        label = "Hijri Calendar",
        description = "The full month grid with today's highlight, weekend tinting, on-widget month\n" +
                "navigation and a compact today card. Resizable from a single tile up to a large grid.",
        sizeHint = "Resizable \u00B7 4\u00D72 min",
        requestCode = 1,
    ),
    CatalogWidget(
        kind = WidgetKind.TODAY,
        provider = HijriTodayWidgetReceiver::class.java,
        settingsActivity = HijriTodayWidgetConfigureActivity::class.java,
        label = "Hijri Today",
        description = "Today's Hijri and Gregorian dates on one line \u2014 Hijri always on the\n" +
                "right \u2014 sized from a single cell up to the full row.",
        sizeHint = "Resizable \u00B7 1\u00D72 min",
        requestCode = 2,
    ),
    CatalogWidget(
        kind = WidgetKind.HIJRI_DATE,
        provider = HijriDateWidgetReceiver::class.java,
        settingsActivity = HijriDateWidgetConfigureActivity::class.java,
        label = "Hijri Date",
        description = "A fixed 1\u00D71 tile with today's Hijri day and month.",
        sizeHint = "Fixed \u00B7 1\u00D71",
        requestCode = 3,
    ),
    CatalogWidget(
        kind = WidgetKind.GREGORIAN_DATE,
        provider = GregorianDateWidgetReceiver::class.java,
        settingsActivity = GregorianDateWidgetConfigureActivity::class.java,
        label = "Gregorian Date",
        description = "A fixed 1\u00D71 tile with today's Gregorian day and month.",
        sizeHint = "Fixed \u00B7 1\u00D71",
        requestCode = 4,
    ),
)

private val catalogPreviewSizes = mapOf(
    WidgetKind.GRID to DpSize(168.dp, 176.dp),
    WidgetKind.TODAY to DpSize(300.dp, 56.dp),
    WidgetKind.HIJRI_DATE to DpSize(96.dp, 96.dp),
    WidgetKind.GREGORIAN_DATE to DpSize(96.dp, 96.dp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetCatalogScreen(
    onBack: () -> Unit,
    onAdd: (CatalogWidget) -> Unit,
    onSettings: (CatalogWidget) -> Unit,
) {
    // Preview follows what the widgets currently render with (family mirror).
    val context = LocalContext.current
    val options = remember { HijriWidgetConfig.loadFamily(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home-screen widgets") },
                navigationIcon = {
                    androidx.compose.material3.TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(catalogWidgets, key = { it.kind }) { widget ->
                WidgetCatalogCard(
                    widget = widget,
                    options = options,
                    onAdd = { onAdd(widget) },
                    onSettings = { onSettings(widget) },
                )
            }
        }
    }
}

@Composable
private fun WidgetCatalogCard(
    widget: CatalogWidget,
    options: WidgetOptions,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = widget.label, style = MaterialTheme.typography.titleLarge)
            Text(
                text = widget.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = widget.sizeHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                WidgetCatalogPreview(kind = widget.kind, options = options)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.End),
            ) {
                OutlinedButton(onClick = onSettings) { Text("Settings") }
                Button(onClick = onAdd) { Text("Add to home") }
            }
        }
    }
}

@Composable
private fun WidgetCatalogPreview(
    kind: WidgetKind,
    options: WidgetOptions,
) {
    val size = catalogPreviewSizes.getValue(kind)
    val rounded = remember { Modifier.clip(RoundedCornerShape(14.dp)) }
    val modifier = rounded.size(size)
    when (kind) {
        WidgetKind.GRID -> HijriWidgetLivePreview(options, null, size, modifier)
        WidgetKind.TODAY -> HijriTodayWidgetLivePreview(options, size, modifier)
        WidgetKind.HIJRI_DATE -> HijriDateWidgetLivePreview(options, size, modifier)
        WidgetKind.GREGORIAN_DATE -> GregorianDateWidgetLivePreview(options, size, modifier)
    }
}