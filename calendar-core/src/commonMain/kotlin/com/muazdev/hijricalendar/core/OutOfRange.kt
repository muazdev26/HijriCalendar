package com.muazdev.hijricalendar.core

import kotlinx.datetime.DateTimeArithmeticException

/**
 * Runs [block], returning null only when it fails because a date falls **outside the supported
 * calendar range**, and reporting everything else.
 *
 * This exists so "no answer" has exactly one meaning. Before it, eight sites used
 * `catch (_: Exception)`, which made an arithmetic slip or a missing month-table entry
 * indistinguishable from a date that legitimately predates the calendar — an internal bug
 * rendered as a grid of disabled cells.
 *
 * ## Why two exception types
 *
 * The upstream libraries do not agree on how to report an out-of-range date, and neither type
 * is a subclass of the other:
 *
 * - `hijrah-datetime` uses `require`/explicit `throw`, i.e. `IllegalArgumentException`
 * - `hijrah-datetime`'s `safeApi` wraps failures in `kotlinx.datetime.DateTimeArithmeticException`,
 *   which extends `RuntimeException`, **not** `IllegalArgumentException`
 *
 * Catching only `IllegalArgumentException` would therefore let half of them escape; catching
 * `Exception` is what got us here. These two clauses are the exact set that means "out of range".
 *
 * Everything else — `IllegalStateException`, `NoSuchElementException` from a `getValue` on the
 * month table, `ArithmeticException` — propagates. Those are defects, and the whole point of
 * this change is that a defect must not read as a missing date.
 *
 * [onFailure] receives the throwable for logging. Callers on per-cell hot paths should pass
 * nothing: one unresolvable grid cell out of 42 is expected at the range edges and a log line
 * per cell would swamp the real signal.
 */
internal inline fun <T> orNullIfOutOfRange(
    onFailure: (Throwable) -> Unit = {},
    block: () -> T,
): T? = try {
    block()
} catch (e: IllegalArgumentException) {
    onFailure(e)
    null
} catch (e: DateTimeArithmeticException) {
    onFailure(e)
    null
}
