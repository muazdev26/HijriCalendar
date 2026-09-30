package com.muazdev.hijricalendar.core

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Severity of a [CalendarLogger] message.
 *
 * The library uses [WARN] for a recoverable environment problem (a broken system clock makes
 * "today" unresolvable) and [ERROR] for a fault that indicates a defect in the library itself.
 */
public enum class CalendarLogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Sink for `calendar-core` diagnostics.
 *
 * Implement this and install it on [CalendarLog] to receive the library's internal messages.
 * [cause] is non-null only for failures and carries the original [Throwable].
 */
public fun interface CalendarLogger {
    public fun log(level: CalendarLogLevel, tag: String, message: String, cause: Throwable?)
}

/**
 * Diagnostics seam for `calendar-core`.
 *
 * The library used to write these to standard out, which is unusable for a published artifact:
 * a consumer on Android cannot route `println` to logcat, cannot silence it, and cannot
 * distinguish a benign "today is unresolvable" from a genuine defect.
 *
 * **The default is silence.** A library must be quiet unless the consumer opts in. To capture
 * diagnostics, install a logger once, before using the calendar:
 *
 * ```kotlin
 * // Android
 * CalendarLog.logger = CalendarLogger { level, tag, message, cause ->
 *     Log.println(
 *         when (level) {
 *             CalendarLogLevel.ERROR -> Log.ERROR
 *             CalendarLogLevel.WARN -> Log.WARN
 *             CalendarLogLevel.INFO -> Log.INFO
 *             CalendarLogLevel.DEBUG -> Log.DEBUG
 *         },
 *         tag,
 *         message,
 *     )
 *     cause?.let { Log.println(tag, it.stackTraceToString()) }
 * }
 * ```
 *
 * This is deliberately a plain settable sink rather than an `expect fun`: `calendar-core` has
 * no platform source sets, and adding three of them purely to reach `android.util.Log` would
 * cost more than it buys. Binding the platform logger is the consumer's one line.
 *
 * The sink is read on whatever thread raised the message, so an implementation must be safe to
 * call from any thread and should not block. Messages are only produced on failure paths, so a
 * logger that does real I/O will not be on a hot path.
 */
@OptIn(ExperimentalAtomicApi::class)
public object CalendarLog {

    private val noOp: CalendarLogger = CalendarLogger { _, _, _, _ -> }

    private val sink = AtomicReference<CalendarLogger>(noOp)

    /**
     * The installed logger, or a no-op when none is set. Assign to receive diagnostics; assign a
     * no-op (e.g. `CalendarLog { _, _, _, _ -> }`) to silence again.
     */
    public var logger: CalendarLogger
        get() = sink.load()
        set(value) {
            sink.store(value)
        }

    /** Restores the default silent behaviour. */
    public fun reset() {
        sink.store(noOp)
    }

    /** True when a logger is installed and will receive messages. */
    public val isInstalled: Boolean
        get() = sink.load() !== noOp

    internal fun d(tag: String, message: String, cause: Throwable? = null) =
        sink.load().log(CalendarLogLevel.DEBUG, tag, message, cause)

    internal fun w(tag: String, message: String, cause: Throwable? = null) =
        sink.load().log(CalendarLogLevel.WARN, tag, message, cause)

    internal fun e(tag: String, message: String, cause: Throwable? = null) =
        sink.load().log(CalendarLogLevel.ERROR, tag, message, cause)
}
