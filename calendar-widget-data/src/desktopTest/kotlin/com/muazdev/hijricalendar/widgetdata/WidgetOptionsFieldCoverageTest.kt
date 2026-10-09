package com.muazdev.hijricalendar.widgetdata

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The completeness guard for [WidgetOptionsEqualityTest], on the JVM only.
 *
 * ## Why it is not in `commonTest`
 *
 * It enumerates the class's fields through `WidgetOptions::class.java.declaredFields`, and `java` is
 * not a member of `KClass` on Kotlin/Native — the whole file failed to compile for
 * `iosSimulatorArm64Test`. That is WD-08's own lesson arriving inside the fix for WD-06's symptom:
 * a common-source-set test that only the desktop run exercises compiles happily and is silently
 * absent from the platform whose divergence matters.
 *
 * Moving it here is right rather than a compromise, because of *what* it checks. Every behavioural
 * assertion in [WidgetOptionsEqualityTest] — that a changed field compares unequal, that the pin
 * normalisation does not swallow a real difference — is plain Kotlin that cannot behave differently
 * on either platform, so running it on both platforms adds no confidence. This test checks the
 * *class's shape*, and reflection over a class's own shape is the one thing common Kotlin genuinely
 * cannot do: there is no portable property enumeration, so a common-source-set version would have to
 * name every field by hand, which is exactly the list it is trying to check the completeness of.
 *
 * So the reflection stays, on the one platform that has reflection. What the K/N run gives up is a
 * redundant rerun, not coverage.
 */
class WidgetOptionsFieldCoverageTest {

    /**
     * No declared field is missing from
     * [WidgetOptionsEqualityTest.oneFieldChanged].
     *
     * The guard that makes the rest of that file work: a field added to the class and not to the map
     * fails here, rather than silently going uncompared in `equals` until a user finds it.
     */
    @Test
    fun everyDeclaredFieldIsCovered() {
        val covered = WidgetOptionsEqualityTest().oneFieldChanged.keys
        val declared = WidgetOptions::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("\$") || it == "Companion" || it == "DEFAULTS" }
            .toSet()

        assertEquals(
            emptySet(),
            declared - covered,
            "a new WidgetOptions field must be added to `oneFieldChanged` in " +
                "WidgetOptionsEqualityTest, and to equals and hashCode — an uncompared field makes " +
                "its settings control dead, because the settings screen skips an equal write",
        )
    }
}
