import kotlinx.validation.ExperimentalBCVApi

plugins {
    // Applied at the root so `apiValidation { }` can be configured once for every module
    // (klib enablement and the ignore list are build-wide, not per-module).
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.vanniktech.publish) apply false
}

apiValidation {
    // KLib validation is opt-in and experimental upstream (0.18.0). It is enabled deliberately:
    // the JVM dump alone would miss the iOS surface, and `HijriCalendarState` / `CalendarMonth`
    // both cross into Swift via the `calendar` and `WidgetCalendar` frameworks.
    @OptIn(ExperimentalBCVApi::class)
    klib {
        enabled = true
    }

    ignoredProjects += listOf(
        // Not published; it reuses the library convention only for the Compose/iOS wiring.
        "sample-shared",
        // Plain `com.android.library` under AGP 9's built-in Kotlin support. The validator does not
        // recognise that Kotlin plugin, so it silently registers no dump task — there is nothing
        // to gate yet. Listed explicitly so the gap is visible rather than looking like coverage.
        // To close it, apply the standalone `org.jetbrains.kotlin.android` plugin here.
        "calendar-widget-glance",
    )
}

subprojects {
    // Applied here rather than per-module so the baseline and config have exactly one definition.
    // `detekt { }` at the root would only ever analyse the (empty) root project.
    apply(plugin = "io.gitlab.arturbosch.detekt")

    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            // The baseline absorbs existing debt; CI then fails only on *new* findings, so
            // violations drain as files are touched for other reasons instead of demanding a
            // big-bang cleanup. See docs/issues/CORE-07-static-analysis.md.
            //
            // One file per project, not one shared file: with a shared path each project's
            // detektBaseline run overwrites the previous one's, silently keeping only the last.
            baseline = rootProject.file("config/detekt/baseline-${project.name}.xml")
            // Kotlin Multiplatform has no src/main: point detekt at every source set rather than
            // letting it default to a JVM layout, which would silently analyse nothing.
            source.setFrom(files("src"))
        }

        dependencies.add("detektPlugins", rootProject.libs.detekt.formatting)
    }
}
