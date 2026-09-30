package com.muazdev.hijricalendar.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the rule behind [pageJump], which is the whole of
 * [UI-09-pager-effect-sync](UI-09-pager-effect-sync.md).
 *
 * It lives here rather than in a composable test because the decision cannot be observed through
 * the pager: `HorizontalPager` composes the same nodes whether it jumps or animates, so the only
 * difference is the scroll offset over time, and a Compose test clock driven in 16ms steps showed no
 * reliable window between the two. An earlier version of the composable test asserted a settle
 * budget and passed with the animation restored — it was worth nothing.
 */
class PageJumpTest {

    @Test
    fun onePage_animate() {
        assertEquals(PageJump.ANIMATE, pageJump(500, 501))
        assertEquals(PageJump.ANIMATE, pageJump(501, 500))
    }

    @Test
    fun noMovement_jumpsRatherThanAnimating() {
        // Equal pages: nothing to do, and animating to where you already are is a wasted frame.
        assertEquals(PageJump.JUMP, pageJump(500, 500))
    }

    @Test
    fun twoPages_jumps() {
        assertEquals(PageJump.JUMP, pageJump(500, 502))
        assertEquals(PageJump.JUMP, pageJump(502, 500))
    }

    @Test
    fun aLongJump_jumps() {
        // goToToday, or a restored month disagreeing with a restored page. This is the case the
        // ticket is about: animating it scrolls through every month in between.
        assertEquals(PageJump.JUMP, pageJump(500, 640))
        assertEquals(PageJump.JUMP, pageJump(640, 500))
    }

    @Test
    fun theWholeWindow_jumps() {
        assertEquals(PageJump.JUMP, pageJump(0, 3611))
        assertEquals(PageJump.JUMP, pageJump(3611, 0))
    }

    @Test
    fun extremePages_doNotOverflow() {
        // pageOffset is computed from caller-supplied bounds, so be sure abs() of the extremes
        // cannot wrap and accidentally classify as ANIMATE.
        assertEquals(PageJump.JUMP, pageJump(Int.MIN_VALUE, Int.MAX_VALUE))
        assertEquals(PageJump.JUMP, pageJump(Int.MAX_VALUE, Int.MIN_VALUE))
    }
}
