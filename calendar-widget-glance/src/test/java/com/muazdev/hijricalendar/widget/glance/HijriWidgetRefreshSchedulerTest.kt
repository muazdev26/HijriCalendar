package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import android.content.ContextWrapper
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WG-18: the daily backstop and the exact midnight alarm exist **only while a widget is placed**.
 *
 * The report this pins down is user-visible rather than subtle: install the library (or the sample
 * app), never add a widget, and Android still showed a pending alarm — the status-bar next-alarm
 * indicator and an entry under Settings > Alarms & reminders — because `schedule()` armed on every
 * `Application.onCreate` without asking whether anything was on a home screen.
 *
 * The decision is asserted through the seam triple (`placedAction` / `activateAction` /
 * `deactivateAction`) rather than through `AppWidgetManager`, `WorkManager` and `AlarmManager`,
 * all of which are stubbed in a local unit test: the behaviour under test is which branch runs, and
 * the platform calls cannot even be observed here.
 */
class HijriWidgetRefreshSchedulerTest {

    @Test
    fun aPlacedWidgetActivatesTheDailyWorkerAndTheMidnightAlarm() {
        val recorded = Recorded()
        withSeams(placed = true, recorded = recorded) {
            HijriWidgetRefreshScheduler.reconcile(context)
        }

        assertEquals(1, recorded.activated, "a placed widget must (re)arm the family's scheduling")
        assertEquals(0, recorded.deactivated, "a placed widget must not cancel anything")
    }

    @Test
    fun noPlacedWidgetCancelsTheDailyWorkerAndTheMidnightAlarm() {
        val recorded = Recorded()
        withSeams(placed = false, recorded = recorded) {
            HijriWidgetRefreshScheduler.reconcile(context)
        }

        assertEquals(0, recorded.activated, "an app with no widget must not schedule a wake-up alarm")
        assertEquals(
            1,
            recorded.deactivated,
            "an app with no widget must release the alarm/worker a previous install state may have armed",
        )
    }

    @Test
    fun theMidnightReArmIsSkippedWhenNothingIsPlaced() {
        val notPlaced = Recorded()
        withSeams(placed = false, recorded = notPlaced) {
            HijriWidgetRefreshScheduler.armMidnightAlarmIfPlaced(context)
        }
        assertEquals(0, notPlaced.armed, "a stale receiver must not resurrect the alarm")

        val afterPlaced = Recorded()
        withSeams(placed = true, recorded = afterPlaced) {
            HijriWidgetRefreshScheduler.armMidnightAlarmIfPlaced(context)
        }
        assertEquals(1, afterPlaced.armed, "a placed widget must keep rolling over at midnight")
    }

    @Test
    fun presenceFailsOpenWhenThePlatformQueryIsUnavailable() {
        // On the JVM `AppWidgetManager.getInstance` is a stub returning null. Failing open is the
        // documented direction: wrongly arming costs one alarm the next lifecycle event
        // reconciles, whereas wrongly answering "no widget" would silently stop every placed widget
        // from rolling over at midnight.
        assertTrue(HijriWidgetPresence.anyPlaced(context))
    }

    private class Recorded {
        var activated = 0
        var deactivated = 0
        var armed = 0
    }

    // ── exact-alarm fallback (issue 09) ──────────────────────────────────

    @Test
    fun belowApi31TheAlarmIsAlwaysExact() {
        // canScheduleExactAlarms() does not exist before API 31, so the permission model cannot
        // deny anything; a `false` here is what a naive caller would pass on an older device.
        assertEquals(AlarmKind.EXACT, alarmKindFor(sdkInt = 30, canScheduleExactAlarms = false))
    }

    @Test
    fun deniedExactAccessFromApi31FallsBackToInexact() {
        assertEquals(AlarmKind.INEXACT, alarmKindFor(sdkInt = 31, canScheduleExactAlarms = false))
        assertEquals(AlarmKind.INEXACT, alarmKindFor(sdkInt = 34, canScheduleExactAlarms = false))
    }

    @Test
    fun grantedExactAccessStaysExact() {
        assertEquals(AlarmKind.EXACT, alarmKindFor(sdkInt = 31, canScheduleExactAlarms = true))
        assertEquals(AlarmKind.EXACT, alarmKindFor(sdkInt = 34, canScheduleExactAlarms = true))
    }

    @Test
    fun theRealArmPathInexactWhenExactAccessIsDenied() {
        val armer = RecordingArmer(apiLevel = 34, canScheduleExact = false)

        withArmer(armer) { HijriWidgetRefreshScheduler.armMidnightAlarm(context) }

        assertEquals(0, armer.exactCalls, "a denied exact-alarm access must not call setExact*")
        assertEquals(1, armer.inexactCalls, "the rollover still has to be armed, just inexactly")
    }

    @Test
    fun theRealArmPathExactWhenAccessIsGranted() {
        val armer = RecordingArmer(apiLevel = 34, canScheduleExact = true)

        withArmer(armer) { HijriWidgetRefreshScheduler.armMidnightAlarm(context) }

        assertEquals(1, armer.exactCalls, "a granted access arms the exact midnight boundary")
        assertEquals(0, armer.inexactCalls, "the fallback must not run when exact is available")
    }

    @Test
    fun theRealArmPathStaysExactBelowApi31WhereThePermissionDoesNotExist() {
        val armer = RecordingArmer(apiLevel = 30, canScheduleExact = false)

        withArmer(armer) {
            HijriWidgetRefreshScheduler.armMidnightAlarm(context)
        }

        assertEquals(1, armer.exactCalls, "Android 12 and below has no exact-alarm special access")
        assertEquals(0, armer.inexactCalls, "degrading there would be a needless loss of exactness")
    }

    /** Records the arming call the real `armMidnightAlarmForReal` chooses. */
    private class RecordingArmer(
        private val apiLevel: Int,
        private val canScheduleExact: Boolean,
    ) : MidnightAlarmArmer {
        var exactCalls = 0
        var inexactCalls = 0

        override fun apiLevel(): Int = apiLevel

        override fun canScheduleExactAlarms(): Boolean = canScheduleExact

        override fun armExact(triggerAtMillis: Long) {
            exactCalls++
        }

        override fun armInexact(triggerAtMillis: Long) {
            inexactCalls++
        }
    }

    /**
     * Runs [block] with the real arm path (the default [HijriWidgetRefreshScheduler.armAction]) but
     * a recording [MidnightAlarmArmer], then restores both seams.
     */
    private fun withArmer(armer: MidnightAlarmArmer, block: () -> Unit) {
        val originalArmer = HijriWidgetRefreshScheduler.armerAction
        val originalArm = HijriWidgetRefreshScheduler.armAction
        HijriWidgetRefreshScheduler.armerAction = { armer }
        HijriWidgetRefreshScheduler.armAction = HijriWidgetRefreshScheduler::armMidnightAlarmForReal
        try {
            block()
        } finally {
            HijriWidgetRefreshScheduler.armerAction = originalArmer
            HijriWidgetRefreshScheduler.armAction = originalArm
        }
    }

    /** Replaces every seam under test, runs [block], and restores them in a `finally`. */
    private fun withSeams(placed: Boolean, recorded: Recorded, block: () -> Unit) {
        val originalPlaced = HijriWidgetRefreshScheduler.placedAction
        val originalActivate = HijriWidgetRefreshScheduler.activateAction
        val originalDeactivate = HijriWidgetRefreshScheduler.deactivateAction
        val originalArm = HijriWidgetRefreshScheduler.armAction
        HijriWidgetRefreshScheduler.placedAction = { placed }
        HijriWidgetRefreshScheduler.activateAction = { recorded.activated++ }
        HijriWidgetRefreshScheduler.deactivateAction = { recorded.deactivated++ }
        HijriWidgetRefreshScheduler.armAction = { recorded.armed++ }
        try {
            block()
        } finally {
            HijriWidgetRefreshScheduler.placedAction = originalPlaced
            HijriWidgetRefreshScheduler.activateAction = originalActivate
            HijriWidgetRefreshScheduler.deactivateAction = originalDeactivate
            HijriWidgetRefreshScheduler.armAction = originalArm
        }
    }

    private companion object {
        /**
         * The scheduler never dereferences the context — the seams ignore it, and the fail-open test
         * only needs `getInstance` to answer null. `ContextWrapper` is concrete in the stubbed
         * `android.jar` (`Context` itself is abstract there).
         */
        val context: Context = ContextWrapper(null)
    }
}
