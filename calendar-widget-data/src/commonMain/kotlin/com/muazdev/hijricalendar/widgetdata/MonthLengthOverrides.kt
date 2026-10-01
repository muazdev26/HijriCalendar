package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.HijriMonthLengths

/**
 * The `(year, month)`-keyed month-length overrides a widget carries, and the encodings the two
 * native storage layers need for them.
 *
 * **Why this is a widget option at all.** `HijriMonthLengths` is an in-memory, mutable table; the
 * process-wide default in [com.muazdev.hijricalendar.core.HijriMonthOverrides] is *not* persisted
 * by the library and does not cross a process boundary. That matters most on iOS: the WidgetKit
 * extension runs in its own process from the host app, so an app that sets an override in-process
 * can never reach the extension's copy of the global. Putting the table in [WidgetOptions] — which
 * already travels through the shared app group — is what lets the widget agree with the app at
 * all. See `docs/widgets/WD-01-no-overrides-threading.md`.
 *
 * **The key is `"<year>-<month>"` rather than a packed int** so the JSON is self-describing (a
 * hand-inspected or hand-edited store stays readable) and so [monthLengthsToMap] /
 * [monthLengthsFrom] round-trip without a lossy arithmetic convention. `Pair<Int, Int>` is
 * deliberately absent from the published surface: see `WD-07-alpha-types-in-abi.md`.
 */

/** The map key for [year]/[month], e.g. `monthLengthKey(1447, 9) == "1447-9"`. */
public fun monthLengthKey(year: Int, month: Int): String = "$year-$month"

/** Parses a [monthLengthKey], or `null` when [key] is not `year-month` with a real month. */
internal fun parseMonthLengthKey(key: String): Pair<Int, Int>? {
    val dash = key.indexOf('-')
    if (dash <= 0 || dash == key.lastIndex) return null
    val year = key.substring(0, dash).toIntOrNull()
    val month = key.substring(dash + 1).toIntOrNull()
    return when {
        year == null || month == null -> null
        year <= 0 || month !in 1..12 -> null
        else -> year to month
    }
}

/**
 * Snapshots [lengths] into the `year-month -> 29|30` form [WidgetOptions.monthLengthOverrides]
 * stores. Reading the table is one atomic load, so the returned map is a consistent snapshot.
 */
public fun monthLengthsToMap(lengths: HijriMonthLengths): Map<String, Int> =
    lengths.all().mapKeys { entry -> monthLengthKey(entry.key.first, entry.key.second) }

/**
 * Builds the table [overrides] describes.
 *
 * Tolerant by construction: a key that is not `year-month`, or a value outside 29/30, is dropped
 * rather than thrown on. `WidgetOptionsJson` is `ignoreUnknownKeys`, and the stored JSON is
 * user-reachable through a shared app group, so the decode path has to degrade to a usable widget
 * (WD-09). [HijriMonthLengths]'s own constructor `require`s 29/30 — this is the fence that keeps a
 * hand-edited store from turning into a crash inside a Glance composition.
 */
public fun monthLengthsFrom(overrides: Map<String, Int>): HijriMonthLengths {
    if (overrides.isEmpty()) return HijriMonthLengths()
    return HijriMonthLengths(
        overrides.entries.mapNotNull { (key, length) ->
            if (length !in 29..30) {
                null
            } else {
                parseMonthLengthKey(key)?.let { yearMonth -> yearMonth to length }
            }
        }.toMap(),
    )
}

/**
 * Flattens [overrides] to the single comma-separated `"<year>-<month>:<length>"` string a
 * `rememberSaveable` `listSaver` can carry.
 *
 * A `Map` is not reliably Bundle-safe (see AGENTS.md on `SavedStateHandle`), so the Android saver
 * writes the CSV rather than the map. Same convention as the sample app's own override store.
 */
public fun encodeMonthLengthsCsv(overrides: Map<String, Int>): String =
    overrides.entries.joinToString(",") { (key, length) -> "$key:$length" }

/** The inverse of [encodeMonthLengthsCsv]; `null`, blank and malformed input yield an empty map. */
public fun decodeMonthLengthsCsv(csv: String?): Map<String, Int> {
    if (csv.isNullOrBlank()) return emptyMap()
    return csv.split(',').mapNotNull { entry ->
        val colon = entry.lastIndexOf(':')
        if (colon <= 0 || colon == entry.lastIndex) {
            null
        } else {
            val length = entry.substring(colon + 1).trim().toIntOrNull()
            when {
                length == null || length !in 29..30 -> null
                else -> entry.substring(0, colon).trim() to length
            }
        }
    }.toMap()
}
