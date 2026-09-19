package com.muazdev.hijricalendar.core

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Runtime, user-supplied month-length overrides for the Hijri calendar.
 *
 * A user override forces a specific (29 or 30 day) length onto a given (year, month),
 * taking precedence over the Pakistan [PakistanHijriCalendar.FIXES] table and the
 * Umm al-Qura calculation. Setting a month's length moves that month's end and
 * re-anchors every later month off it until the next absolute anchor (a Pakistan fix)
 * re-syncs the chain.
 *
 * Overrides are global (library-wide) and immutable-swapped, so readers always observe a
 * complete snapshot; readers never block writers and writers never block each other
 * (lock-free CAS loops). They persist for the lifetime of the process; the app is
 * responsible for durable persistence (see `docs/MONTH-END-LENGTH-OVERRIDES.md` and the
 * sample app).
 */
@OptIn(ExperimentalAtomicApi::class)
object HijriMonthOverrides {

    private val overrides: AtomicReference<Map<Pair<Int, Int>, Int>> =
        AtomicReference(emptyMap())
    private val revision: AtomicLong = AtomicLong(0L)

    /** Monotonic revision bumped on every effective mutation; used to invalidate derived caches. */
    val currentRevision: Long get() = revision.load()

    /** Forces [length] (29 or 30) onto [year]/[month]. Overwrites any existing value. */
    fun setMonthLength(year: Int, month: Int, length: Int) {
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
    fun clearMonthLength(year: Int, month: Int) {
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
    fun clearAll() {
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
    fun monthLength(year: Int, month: Int): Int? = overrides.load()[year to month]

    /** Full snapshot of the current overrides, keyed by `(year, month)`. */
    fun all(): Map<Pair<Int, Int>, Int> = overrides.load()

    /**
     * Replaces the whole table in one step (used to restore persisted overrides at
     * app startup, e.g. from `docs/MONTH-END-LENGTH-OVERRIDES.md` sample persistence).
     */
    fun replaceAll(source: Map<Pair<Int, Int>, Int>) {
        val validated = source.mapValues { (_, length) ->
            require(length in 29..30) { "Month length must be 29 or 30, got $length" }
            length
        }
        while (true) {
            val current = overrides.load()
            if (current == validated) return
            if (overrides.compareAndSet(current, validated)) {
                revision.incrementAndFetch()
                return
            }
        }
    }
}