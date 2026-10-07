package com.muazdev.hijricalendar.widget.glance

import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionRunCallback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loading state's action suppression.
 *
 * This is the one part of the month-step that is a pure function, and it is the part with a rule
 * worth pinning: **while a month is loading, nothing in the widget responds to a tap.**
 *
 * It matters because the alternative is not "the grid is briefly unresponsive" — it is a *wrong*
 * answer. The arrows stay live, a second tap arrives while the first step is still deciding which
 * month it lands on, and the second tap resolves against a base the first is about to replace. That
 * skips a month, and the user pressed one arrow once per month they meant to move.
 */
class WidgetLoadingActionsTest {

    // A real action rather than a stub: `actionRunCallback` is pure data construction, so it
    // works off-device, and a hand-rolled fake would only prove the test can compare references.
    private fun anAction(): Action = actionRunCallback<HijriWidgetNextMonthCallback>()

    private val live = WidgetActions(
        open = anAction(),
        prev = anAction(),
        next = anAction(),
        reset = anAction(),
        refresh = anAction(),
    )

    @Test
    fun aFullyWiredWidgetIsInteractive() {
        assertFalse(
            "a widget with every action wired must be reported as interactive, or `isNonInteractive` " +
                "is doing its opposite job",
            live.isNonInteractive,
        )
    }

    @Test
    fun loadingRemovesEveryActionIncludingTheOnesAddedLater() {
        val loading = live.whileLoading()

        assertTrue(loading.isNonInteractive)
        // Named individually rather than trusted to `isNonInteractive`: that flag is a conjunction,
        // so a single forgotten field would leave this value non-null while the flag still read true
        // — which is exactly the bug the test exists to catch.
        assertTrue("the tap-through open action survived the loading state", loading.open == null)
        assertTrue("prev survived the loading state", loading.prev == null)
        assertTrue("next survived the loading state", loading.next == null)
        assertTrue("the today reset survived the loading state", loading.reset == null)
        assertTrue("the refresh button survived the loading state", loading.refresh == null)
    }

    @Test
    fun loadingDoesNotMutateTheWidgetItWasDerivedFrom() {
        live.whileLoading()

        assertFalse(
            "`whileLoading` must return a new value, not edit the caller's: `provideGlance` holds the " +
                "wired actions for the whole composition, so an in-place clear would leave the widget " +
                "dead after its first navigation step",
            live.isNonInteractive,
        )
    }

    @Test
    fun aPreviewIsNonInteractiveForTheSameReason() {
        // The previews pass an all-null WidgetActions and get the same "cannot be tapped" answer the
        // loading state does — one value, two situations, which is what the grouping is for.
        assertTrue(WidgetActions().isNonInteractive)
        assertTrue(WidgetActions().whileLoading().isNonInteractive)
    }
}
