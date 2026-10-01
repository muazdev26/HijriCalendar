package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The header's navigation chevron, drawn rather than imported.
 *
 * ## Why not `material-icons-extended` or `-core`
 *
 * This library used to depend on `org.jetbrains.compose.material:material-icons-extended` for
 * exactly two icons — `Icons.AutoMirrored.Filled.KeyboardArrowLeft` and its `Right`. That is roughly
 * five thousand icons of dependency in a published artifact, and it was pinned to **1.7.3** while
 * the rest of Compose resolved from **1.12.0**: five minors of one release train apart, which is
 * the kind of thing that surfaces in a consumer as an unexplained `NoSuchMethodError`.
 *
 * Swapping `extended` for `core` does not fix it, because the JetBrains icons artifacts stopped
 * being published at the 1.7.x line — `org.jetbrains.compose.material:material-icons-core:1.12.0`
 * does not exist. So there was no version-aligned icons artifact to move to. See
 * `docs/issues/UI-13-dependency-hygiene.md`.
 *
 * A chevron is two lines of geometry that will never need redrawing. Drawing it costs about a dozen
 * lines and removes a dependency from a published artifact, which is the better trade even if the
 * icon had been available at the right version.
 *
 * ## Mirroring
 *
 * [pointingForward] is a **logical** direction, not a literal "right", and the glyph mirrors itself
 * in RTL — which is precisely what `Icons.AutoMirrored` did, and what a first draft of this file
 * silently stopped doing. The header passes "previous" and "next" as logical directions and knows
 * nothing about layout; without the mirroring below, an Urdu calendar's previous-month chevron would
 * point the wrong way while every LTR test stayed green.
 *
 * [resolveNavDirection] is separated out so that is directly testable. See `NavChevronTest`.
 *
 * [contentDescription] is on the glyph, and the enclosing `IconButton` merges descendants — the same
 * arrangement the material icons used, so the action is announced once rather than twice.
 */
@Composable
internal fun NavChevron(
    pointingForward: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    color: Color,
) {
    val forwards = resolveNavDirection(pointingForward, LocalLayoutDirection.current == LayoutDirection.Rtl)
    Canvas(
        modifier = modifier
            .size(24.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val strokeWidth = 2.dp.toPx()
        val inset = strokeWidth * 2f
        val left = inset
        val right = size.width - inset
        val midX = size.width / 2f
        val top = inset
        val bottom = size.height - inset

        // The tip sits 35% past the centre toward the edge it points at; the tail sits on the
        // opposite side of the centre, so the two strokes meet at a point.
        val tipX = when {
            forwards -> midX + (right - midX) * 0.35f
            else -> midX - (midX - left) * 0.35f
        }
        val tailX = when {
            forwards -> midX - (midX - left) * 0.85f
            else -> midX + (right - midX) * 0.85f
        }

        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(tailX, top),
            end = androidx.compose.ui.geometry.Offset(tipX, (top + bottom) / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(tailX, bottom),
            end = androidx.compose.ui.geometry.Offset(tipX, (top + bottom) / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Resolves a **logical** navigation direction into a physical one for [rtl].
 *
 * Mirrors, exactly as `Icons.AutoMirrored` did. In a right-to-left layout "forward" is to the left,
 * so a logical forward chevron points left. The header always passes logical directions, so this is
 * the only place layout direction is consulted for the arrows.
 *
 * Split out from the `Canvas` because the geometry is two lines of arithmetic and this is the part
 * with logic worth asserting — and asserting it directly avoids pixel-comparing a rendered glyph,
 * which is fragile under device pixel ratio and antialiasing.
 */
internal fun resolveNavDirection(pointingForward: Boolean, rtl: Boolean): Boolean =
    if (rtl) !pointingForward else pointingForward
