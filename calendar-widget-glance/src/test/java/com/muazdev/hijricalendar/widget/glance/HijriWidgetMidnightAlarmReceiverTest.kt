package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * The receiver's guarantee, and the one thing in the module that had to survive a failure at
 * midnight: the next alarm must exist regardless of what the rollover render does.
 *
 * The re-arm used to be the *second statement* rather than a `finally`, so a single `DataStore` I/O
 * failure at 00:00 left the alarm permanently unarmed. Recovery was then the daily
 * [HijriWidgetRefreshWorker] — so a widget could show yesterday's date for up to a day, and nothing
 * in the logs distinguished "never re-armed" from "skipped because the app was foreground".
 *
 * `rollOver` is tested directly rather than through `onReceive`, because `goAsync()` needs a live
 * receiver while the behaviour under test is entirely inside `rollOver`.
 */
class HijriWidgetMidnightAlarmReceiverTest {

    private val receiver = HijriWidgetMidnightAlarmReceiver()

    /** Drives one rollover with the render replaced by [refresh] and the re-arm recorded. */
    private fun rollOver(
        reason: String = "midnight",
        refresh: suspend () -> Unit,
    ): Pair<Int, Int> {
        var arms = 0
        val originalArm = HijriWidgetRefreshScheduler.armAction
        val originalRefresh = receiver.refreshAction
        HijriWidgetRefreshScheduler.armAction = { arms++ }
        receiver.refreshAction = { _, _ -> refresh() }
        var finishes = 0
        try {
            runBlocking { receiver.rollOver(context, reason) { finishes++ } }
        } finally {
            HijriWidgetRefreshScheduler.armAction = originalArm
            receiver.refreshAction = originalRefresh
        }
        return arms to finishes
    }

    @Test
    fun aSuccessfulRolloverArmsTheNextAlarmAndFinishesTheBroadcast() {
        val (arms, finishes) = rollOver { }
        assertEquals(1, arms, "the next midnight must be armed")
        assertEquals(1, finishes, "goAsync() must always be finished")
    }

    @Test
    fun aFailingRenderStillArmsTheNextAlarm() {
        val attempts = AtomicInteger(0)
        val (arms, finishes) = rollOver {
            attempts.incrementAndGet()
            throw UnsupportedOperationException("DataStore is unhappy")
        }
        assertEquals(1, attempts.get(), "the render must actually have been attempted")
        assertEquals(1, arms, "a failed render must not skip the re-arm")
        assertEquals(1, finishes, "the broadcast must complete even on failure")
    }

    @Test
    fun aFailingReArmStillFinishesTheBroadcast() {
        // The re-arm is the recovery path, so it must not be the thing that leaks a broadcast.
        val originalArm = HijriWidgetRefreshScheduler.armAction
        val originalRefresh = receiver.refreshAction
        HijriWidgetRefreshScheduler.armAction = { throw SecurityException("exact alarms denied") }
        receiver.refreshAction = { _, _ -> }
        var finishes = 0
        try {
            runBlocking { receiver.rollOver(context, "midnight") { finishes++ } }
        } finally {
            HijriWidgetRefreshScheduler.armAction = originalArm
            receiver.refreshAction = originalRefresh
        }
        assertEquals(1, finishes, "a failing re-arm must not skip pendingResult.finish()")
    }

    @Test
    fun aCancellationStillArmsAndFinishes() {
        // `CancellationException` is the one throwable a broad `catch` must not swallow as an error,
        // but the re-arm is still a guarantee: a scope cancelled between the render and the alarm
        // must not leave the device without a midnight boundary.
        val (arms, finishes) = rollOver { throw kotlinx.coroutines.CancellationException("gone") }
        assertEquals(1, arms)
        assertEquals(1, finishes)
    }

    @Test
    fun theRefreshSeesTheMidnightReason() {
        // The reason string is what the whole refresh log is keyed on, so a rollover that passed a
        // different one would be invisible in `adb logcat -s HijriWidgetRefresh`.
        var seen: String? = null
        val originalRefresh = receiver.refreshAction
        receiver.refreshAction = { _, reason -> seen = reason }
        try {
            runBlocking { receiver.rollOver(context, "midnight") {} }
        } finally {
            receiver.refreshAction = originalRefresh
        }
        assertEquals("midnight", seen)
    }

    @Test
    fun theHeartbeatRolloverIsLoggedAsTheHeartbeatNotAsTheBoundary() {
        // Both alarms share one rollover path, so the only thing that tells a device whose exact
        // midnight alarm never fired apart from one that woke on schedule is this string. Passing
        // "midnight" here would make the heartbeat's own wake-up look like a punctual boundary and
        // hide exactly the failure the heartbeat exists to report.
        var seen: String? = null
        val originalRefresh = receiver.refreshAction
        receiver.refreshAction = { _, reason -> seen = reason }
        try {
            runBlocking { receiver.rollOver(context, "heartbeat") {} }
        } finally {
            receiver.refreshAction = originalRefresh
        }
        assertEquals("heartbeat", seen)
    }

    @Test
    fun onlyTheHeartbeatsActionMapsToTheHeartbeatReason() {
        // `PendingIntent` identity is `requestCode` + `filterEquals(intent)`, and the boundary's
        // intent is frozen with no action, so "no action" must keep meaning the boundary.
        assertEquals("midnight", alarmReasonFor(null))
        assertEquals("midnight", alarmReasonFor("com.muazdev.hijricalendar.widget.OTHER"))
        assertEquals("heartbeat", alarmReasonFor(HijriWidgetRefreshScheduler.HEARTBEAT_ACTION))
    }

    private companion object {
        /**
         * The rollover never dereferences the context — `refreshAction` and the re-arm seam both
         * ignore it — and `ContextWrapper` is concrete in the stubbed `android.jar` (`Context` itself
         * is abstract there, so a dynamic proxy cannot stand in for it).
         */
        val context: Context = ContextWrapper(null)
    }
}
