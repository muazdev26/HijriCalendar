package com.muazdev.hijricalendar.core

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Runtime, user-supplied month-length overrides: a mutable, thread-safe table that forces a
 * specific (29 or 30 day) length onto a given (year, month).
 *
 * A user override takes precedence over the Pakistan [PakistanHijriCalendar.FIXES] table and the
 * Umm al-Qura calculation. Setting a month's length moves that month's end and re-anchors every
 * later month off it until the next absolute anchor (a Pakistan fix) re-syncs the chain.
 *
 * **Instances are independent.** Two [HijriMonthLengths] can hold different tables and be mutated
 * concurrently without affecting each other, which is what lets two calendars in one process
 * disagree about what a month looked like. For the common single-calendar case, use
 * [HijriMonthOverrides.current] — the process-wide default — so nothing has to be threaded.
 *
 * Concurrency: lock-free. Readers load one immutable map reference and never block; writers
 * build a new map and CAS-publish it, so a reader always sees a complete snapshot and never a
 * half-applied change. There is no `synchronized` in this module by design.
 *
 * Persistence is the app's responsibility, not the library's. Restore a saved table with
 * [replaceAll] before constructing the calendar; see `docs/MONTH-END-LENGTH-OVERRIDES.md` and the
 * sample app for a compact `"year-month:length"` encoding.
 */
@OptIn(ExperimentalAtomicApi::class)
public class HijriMonthLengths(initial: Map<Pair<Int, Int>, Int> = emptyMap()) {

    private val overrides: AtomicReference<Map<Pair<Int, Int>, Int>> =
        AtomicReference(validate(initial))

    /**
     * Monotonic revision, bumped only on an *effective* mutation. A write that does not change
     * the table leaves it alone, so a redundant `setMonthLength` does not invalidate every cache
     * keyed on it. Read this to decide whether anything derived from this table needs rebuilding.
     */
    private val revision: AtomicLong = AtomicLong(if (initial.isEmpty()) 0L else 1L)

    /** See [revision]. */
    public val currentRevision: Long get() = revision.load()

    /** Forces [length] (29 or 30) onto [year]/[month]. Overwrites any existing value. */
    public fun setMonthLength(year: Int, month: Int, length: Int) {
        require(length in 29..30) { "Month length must be 29 or 30, got $length" }
        while (true) {
            val current = overrides.load()
            if (current[year to month] == length) return // No effective change: keep revision.
            val updated = current + (year to month to length)
            if (overrides.compareAndSet(current, updated)) {
                revision.incrementAndFetch()
                return
            }
        }
    }

    /** Removes a previously set override, falling back to the calculated length. */
    public fun clearMonthLength(year: Int, month: Int) {
        while (true) {
            val current = overrides.load()
            if (!current.containsKey(year to month)) return
            val updated = current - (year to month)
            if (overrides.compareAndSet(current, updated)) {
                revision.incrementAndFetch()
                return
            }
        }
    }

    /** Removes every override, falling back to the calculated calendar entirely. */
    public fun clearAll() {
        while (true) {
            val current = overrides.load()
            if (current.isEmpty()) return
            if (overrides.compareAndSet(current, emptyMap())) {
                revision.incrementAndFetch()
                return
            }
        }
    }

    /** The forced length for [year]/[month], or null when the calculation should be used. */
    public fun monthLength(year: Int, month: Int): Int? = overrides.load()[year to month]

    /**
     * Full snapshot of the current overrides, keyed by `(year, month)`. A read is one atomic
     * load, so the returned map is a consistent snapshot even if another thread is writing.
     */
    public fun all(): Map<Pair<Int, Int>, Int> = overrides.load()

    /**
     * Replaces the whole table in one step, for restoring persisted overrides at app startup.
     * A no-op when [source] already equals the current table, so it does not bump the revision.
     */
    public fun replaceAll(source: Map<Pair<Int, Int>, Int>) {
        val validated = validate(source)
        while (true) {
            val current = overrides.load()
            if (current == validated) return
            if (overrides.compareAndSet(current, validated)) {
                revision.incrementAndFetch()
                return
            }
        }
    }

    private fun validate(source: Map<Pair<Int, Int>, Int>): Map<Pair<Int, Int>, Int> =
        source.mapValues { (_, length) ->
            require(length in 29..30) { "Month length must be 29 or 30, got $length" }
            length
        }

    override fun toString(): String = "HijriMonthLengths(revision=$currentRevision, all=${all()})"
}

/**
 * The process-wide default month-length overrides.
 *
 * Every member delegates to [current], a single shared [HijriMonthLengths]. This exists so the
 * common case — one calendar per process, configured from the app's own saved state — needs no
 * plumbing at all. It is deliberately **not** a supertype of [HijriMonthLengths]: code that
 * writes overrides should name the instance it is writing to, not reach for a global.
 *
 * The table is process-global and **not** persisted by the library. It lives for the lifetime of
 * the process; the app is responsible for durable persistence (see [HijriMonthLengths] and
 * `docs/MONTH-END-LENGTH-OVERRIDES.md`).
 *
 * Every function in this module that consults overrides takes an explicit `overrides:
 * HijriMonthLengths` parameter defaulting to `HijriMonthOverrides.current`. Passing an instance
 * is how you scope overrides; omitting it means the process default.
 */
@OptIn(ExperimentalAtomicApi::class)
public object HijriMonthOverrides {

    private val default: HijriMonthLengths = HijriMonthLengths()

    /** The process-wide default table. Pass this to a scoped API to use it explicitly. */
    public val current: HijriMonthLengths get() = default

    /** See [HijriMonthLengths.currentRevision]. */
    public val currentRevision: Long get() = default.currentRevision

    /** See [HijriMonthLengths.monthLength]. */
    public fun monthLength(year: Int, month: Int): Int? = default.monthLength(year, month)

    /** See [HijriMonthLengths.all]. */
    public fun all(): Map<Pair<Int, Int>, Int> = default.all()

    /** See [HijriMonthLengths.setMonthLength]. */
    public fun setMonthLength(year: Int, month: Int, length: Int): Unit =
        default.setMonthLength(year, month, length)

    /** See [HijriMonthLengths.clearMonthLength]. */
    public fun clearMonthLength(year: Int, month: Int): Unit = default.clearMonthLength(year, month)

    /** See [HijriMonthLengths.clearAll]. */
    public fun clearAll(): Unit = default.clearAll()

    /** See [HijriMonthLengths.replaceAll]. */
    public fun replaceAll(source: Map<Pair<Int, Int>, Int>): Unit = default.replaceAll(source)
}
