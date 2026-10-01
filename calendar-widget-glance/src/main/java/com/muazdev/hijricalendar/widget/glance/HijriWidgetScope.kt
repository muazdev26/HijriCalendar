package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The module's one coroutine scope, with an exception handler that logs.
 *
 * **Why this exists** (WG-07). There was one ad-hoc `CoroutineScope(SupervisorJob() + …)` per call
 * site — the midnight alarm receiver, [HijriWidgetRefresher], [HijriWidgetPreviewPublisher] — and
 * none of them had a [CoroutineExceptionHandler]. A bare `SupervisorJob` with no parent means an
 * uncaught exception in `launch` goes to the thread's default handler, which **on Android kills the
 * process**. Not the coroutine: the app. The midnight receiver was the worst case, because it was
 * the one place calling fallible library code (`refreshAll`, then `armMidnightAlarm`) with no
 * `catch` anywhere in the path — so a `DataStore` I/O failure at 00:00 crashed the host app and
 * silently never re-armed the alarm, which is the one thing that had to happen.
 *
 * A handler here means every such throwable is logged under the module's existing
 * [HijriWidgetRefreshLog] tag and the process survives, which is the difference between a diagnosable
 * bug report and a silent crash.
 *
 * **Lifetime.** This scope lives for the process, which is correct for all three users: the work is
 * bounded, the receivers are one-shot, and [HijriWidgetRefresher]'s catch-up deliberately outlives
 * the activity that scheduled it. Callers that need a `Context` across the suspension point should
 * pass [Context.getApplicationContext] — holding a broadcast or Activity context here is what
 * `goAsync` makes legal but does not make correct.
 */
internal object HijriWidgetScope {

    /**
     * Logs and swallows. The swallowing is the point: an uncaught throwable in a fire-and-forget
     * launch has no caller to propagate to, so the only two useful outcomes are "log it" and "kill
     * the process", and only one of those is ever the right one for a widget refresh.
     *
     * The parameter is deliberately `Throwable`, not `Exception`: [CoroutineExceptionHandler] hands
     * over whatever escaped, and narrowing it here would re-open the hole this object exists to
     * close. `CancellationException` never reaches a handler, so this cannot swallow one.
     */
    @Suppress("TooGenericExceptionCaught")
    private val handler = CoroutineExceptionHandler { _, throwable ->
        HijriWidgetRefreshLog.e(
            "scope",
            "unhandled throwable in a widget coroutine: ${throwable.message}",
            throwable,
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)

    /**
     * Launches [block] on [context] (an application context, see the KDoc) and returns the [Job] so
     * a caller that has a lifetime to tie to can hold it.
     */
    fun launch(context: Context, reason: String, block: suspend (Context) -> Unit): Job =
        scope.launch {
            val appContext = context.applicationContext
            block(appContext)
        }.also { job ->
            job.invokeOnCompletion { cause ->
                if (cause != null) {
                    // `launch` already routes a *failure* to [handler]; this catches the paths a
                    // handler does not see — a `CancellationException` with a cause, and anything a
                    // caller cancels the parent with.
                    HijriWidgetRefreshLog.d("scope", "$reason completed with ${cause::class.simpleName}")
                }
            }
        }
}
