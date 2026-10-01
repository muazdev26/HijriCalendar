package com.muazdev.hijricalendar.ui

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the mirroring `NavChevron` took over from `Icons.AutoMirrored` when
 * [UI-13](UI-13-dependency-hygiene.md) removed the icons dependency.
 *
 * `Icons.AutoMirrored.Filled.KeyboardArrowLeft` mirrored **itself** from the ambient layout
 * direction. A drawn glyph does not, so the mirroring became this library's responsibility. The
 * failure mode is nasty: in an Urdu calendar the previous-month chevron points the wrong way, and
 * **every LTR test stays green**, because the header passes logical directions either way and the
 * bug only appears under an RTL layout.
 *
 * The first version of this test compared rendered pixels. It reported a suspiciously perfect
 * 288/288 ink split for *both* directions — which turned out to be a capture-bounds problem, not a
 * glyph problem. Asserting [resolveNavDirection] directly is robust under any pixel ratio, any
 * antialiasing, and any capture semantics.
 */
@OptIn(ExperimentalTestApi::class)
class NavChevronTest {

    // ── the mirroring rule ─────────────────────────────────────────────

    @Test
    fun inLtr_logicalForwardIsPhysicalRight() {
        assertEquals(true, resolveNavDirection(pointingForward = true, rtl = false))
        assertEquals(false, resolveNavDirection(pointingForward = false, rtl = false))
    }

    @Test
    fun inRtl_theDirectionIsInverted() {
        assertEquals(false, resolveNavDirection(pointingForward = true, rtl = true))
        assertEquals(true, resolveNavDirection(pointingForward = false, rtl = true))
    }

    @Test
    fun mirroringIsItsOwnInverse() {
        listOf(true, false).forEach { logical ->
            val ltr = resolveNavDirection(logical, rtl = false)
            val rtl = resolveNavDirection(logical, rtl = true)
            assertEquals(
                !ltr,
                rtl,
                "a logical direction must resolve to opposite physical directions in the two layouts",
            )
        }
    }

    // ── the label survives the move off the icons library ──────────────

    @Test
    fun theChevronCarriesItsContentDescriptionInBothLayouts() = runComposeUiTest {
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides direction,
                ) {
                    androidx.compose.foundation.layout.Box {
                        NavChevron(
                            pointingForward = true,
                            contentDescription = "Next month",
                            color = androidx.compose.ui.graphics.Color.Black,
                        )
                    }
                }
            }
            onNodeWithContentDescription("Next month").assertExists()
        }
    }
}
