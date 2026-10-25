package com.muazdev.hijricalendar.sample

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * Which palette the sample draws, and how to switch it.
 *
 * The sample used to wrap every screen in a bare `MaterialTheme { }`, which is Material's **light**
 * scheme regardless of the device setting — so `HijriCalendarColors` (all of it derived from
 * `MaterialTheme.colorScheme`) could never be seen in a dark palette, and a night-mode bug in the
 * library was untestable on this app. That is the whole reason this file exists.
 *
 * The three modes are the testing affordance, not a product feature: [DARK] on a light device (and
 * vice versa) is the only way to compare both palettes without toggling the whole phone, and
 * [SYSTEM] is what a real host app would ship.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** The palette [mode] resolves to, for callers outside a composition (the system bars). */
fun ThemeMode.resolveDark(resources: Resources): Boolean {
    val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return when (this) {
        ThemeMode.SYSTEM -> nightMode == Configuration.UI_MODE_NIGHT_YES
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/**
 * The chosen mode, app-wide.
 *
 * `SharedPreferences` rather than the ViewModel: three activities draw the calendar family (the
 * calendar itself, the widget catalog and the per-widget settings screens reached from a launcher
 * pin), only the first owns the ViewModel, and a theme that changed when you opened the catalog
 * would be worse than no theme at all.
 */
object SampleThemeStore {
    private const val PREFS = "hijri_sample_theme"
    private const val KEY = "theme_mode"

    fun read(context: Context): ThemeMode {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    fun write(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode.name)
            .apply()
    }
}

/**
 * The sample's Material theme: [lightColorScheme] / [darkColorScheme], with no dynamic colour.
 *
 * Deliberately **not** dynamic (wallpaper) colour: the point of this app is to exercise the library's
 * palette derivations (`HijriCalendarDefaults.colors()` maps `primary`, `secondaryContainer` and
 * `error` straight through), and a scheme that changed per device would make two screenshots
 * incomparable.
 */
@Composable
fun HijriSampleTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    // `isSystemInDarkTheme()` inside the composition, so a device night-mode change recomposes under
    // SYSTEM; `resolveDark` is the same rule for the system bars, which live outside it.
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

/**
 * Makes the status/navigation bar icons follow the **chosen** mode rather than the device's.
 *
 * Without this, forcing [ThemeMode.DARK] on a light phone leaves dark status-bar icons over a dark
 * app bar — the icons are decided by `enableEdgeToEdge`'s `detectDarkMode`, which by default reads the
 * system configuration. Call it from `onCreate` and again whenever the mode changes.
 */
fun ComponentActivity.applySampleSystemBars(mode: ThemeMode) {
    val dark = mode.resolveDark(resources)
    // A transparent bar on both sides of API 29, where the scrims stop being used; the values below
    // are the ones `enableEdgeToEdge`'s own defaults use on 26-28.
    val style = SystemBarStyle.auto(
        lightScrim = 0xE6FFFFFF.toInt(),
        darkScrim = 0x801B1B1B.toInt(),
    ) { dark }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

/**
 * The top bar's theme control: a button that opens the three modes, with the active one marked.
 *
 * A drop-down rather than a cycling button because a cycle gives no way to see the other choices, and
 * this control exists to be *tested against* — the tester picks a palette deliberately.
 */
@Composable
fun ThemeModeButton(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text("Theme: ${mode.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { option ->
                val active = option == mode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (active) FontWeight.Bold else null,
                        )
                    },
                    trailingIcon = if (active) {
                        // Text, not `Icons.Filled.Check`: the sample takes no material-icons
                        // dependency, and `calendar-ui` deliberately dropped it (see NavChevron.kt).
                        { Text("\u2713", textAlign = TextAlign.End) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onModeChange(option)
                    },
                )
            }
        }
    }
}
