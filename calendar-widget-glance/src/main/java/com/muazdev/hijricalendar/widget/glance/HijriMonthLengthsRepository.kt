package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.muazdev.hijricalendar.core.HijriMonthLengths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * The process-shared, durable home of the user's month-length overrides.
 *
 * **Why this exists** (and why the process-global table is deprecated).
 * [com.muazdev.hijricalendar.core.HijriMonthOverrides] evaporates with the process, so a widget
 * woken by an alarm in a freshly restarted process has no overrides at all — the grid renders the
 * calculated calendar until something seeds the table from memory that no longer exists. The
 * application and its widgets are one package, so a single DataStore Preferences file in the app's
 * `filesDir` is the one store both see identically: the app writes it, an alarm-woken widget reads
 * it, and a process that was killed mid-tap restores from it.
 *
 * **What it stores.** Overrides are keyed as `o.<year>.<month>` with a 29 or 30 length. A full
 * snapshot is read or written atomically by DataStore, so readers never observe a half-applied
 * table.
 */
public class HijriMonthLengthsRepository internal constructor(
    /** The underlying DataStore. Inject one (tests do), or use [shared]. */
    private val dataStore: DataStore<Preferences>,
) {

    /** Every override currently in the store, keyed by `(year, month)`. */
    public suspend fun load(): Map<Pair<Int, Int>, Int> =
        dataStore.data.first().asMap()
            .mapNotNull { (key, value) ->
                val (year, month) = key.name.parseOverrideKey() ?: return@mapNotNull null
                (year to month) to (value as? Int ?: return@mapNotNull null)
            }
            .toMap()

    /** A [HijriMonthLengths] built from the current store, for passing explicitly. */
    public suspend fun loadTable(): HijriMonthLengths = HijriMonthLengths(load())

    /**
     * The current revision counter. Changes only when the stored table changes content.
     * Used as part of cache keys so the render cache invalidates when the stored table changes.
     */
    public suspend fun revision(): Long =
        dataStore.data.first().get(revisionKey) ?: 0L

    /** Forces [length] (29 or 30) onto [year]/[month], overwriting any existing value. */
    public suspend fun setMonthLength(year: Int, month: Int, length: Int) {
        require(length in 29..30) { "Month length must be 29 or 30, got $length" }
        require(month in 1..12) { "Month must be in 1..12, got $month" }
        dataStore.edit { prefs ->
            val current = prefs[overrideKey(year, month)]
            prefs[overrideKey(year, month)] = length
            if (current != length) {
                prefs[revisionKey] = (prefs[revisionKey] ?: 0L) + 1
            }
        }
    }

    /** Removes the override for [year]/[month], leaving no entry behind. */
    public suspend fun clearMonthLength(year: Int, month: Int) {
        dataStore.edit { prefs ->
            val existed = prefs.contains(overrideKey(year, month))
            prefs.remove(overrideKey(year, month))
            if (existed) {
                prefs[revisionKey] = (prefs[revisionKey] ?: 0L) + 1
            }
        }
    }

    /** Removes every override in the store. */
    public suspend fun clearAll() {
        dataStore.edit { prefs ->
            val hadOverrides = prefs.asMap().any { (key, _) -> key.name.startsWith("o.") }
            prefs.clear()
            if (hadOverrides) {
                prefs[revisionKey] = (prefs[revisionKey] ?: 0L) + 1
            }
        }
    }

    /** Replaces the whole store in one step — for migrating callers with their own CSV overrides. */
    public suspend fun replaceAll(overrides: Map<Pair<Int, Int>, Int>) {
        overrides.forEach { (key, length) ->
            require(length in 29..30) { "Month length must be 29 or 30, got $length" }
            require(key.second in 1..12) { "Month must be in 1..12, got ${key.second}" }
        }
        dataStore.edit { prefs ->
            val currentMap = load()
            prefs.clear()
            overrides.forEach { (key, length) -> prefs[overrideKey(key.first, key.second)] = length }
            if (currentMap != overrides) {
                prefs[revisionKey] = (prefs[revisionKey] ?: 0L) + 1
            }
        }
    }

    private val revisionKey = longPreferencesKey("revision")

    private fun overrideKey(year: Int, month: Int) =
        intPreferencesKey("o.$year.${month.toString().padStart(2, '0')}")

    private fun String.parseOverrideKey(): Pair<Int, Int>? {
        if (!startsWith("o.")) return null
        val parts = removePrefix("o.").split('.')
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        return if (parts.size == 2 && year != null && month != null) year to month else null
    }

    public companion object {
        /** The DataStore file name — one store, shared by every component in the package. */
        public const val FILE_NAME: String = "hijri_month_overrides.preferences_pb"

        /**
         * The scope the DataStore uses for its background I/O. Never cancelled: the file handles a
         * process owns for its lifetime.
         */
        private val dataStoreScope: CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var cached: HijriMonthLengthsRepository? = null

        /**
         * The process-wide repository backed by the application's `filesDir`, created on first use.
         * DataStore permits exactly one instance per file per process, so callers get this rather
         * than their own — the app and its widgets all share the same object.
         */
        public fun shared(context: Context): HijriMonthLengthsRepository =
            cached ?: synchronized(this) {
                cached ?: run {
                    val file = context.applicationContext.preferencesDataStoreFile(FILE_NAME)
                    HijriMonthLengthsRepository(
                        PreferenceDataStoreFactory.create(
                            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                            scope = dataStoreScope,
                            produceFile = { file },
                        ),
                    ).also { cached = it }
                }
            }
    }
}
