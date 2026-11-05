import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // Every public declaration must state its visibility and return type. On a published module
    // this is the cheapest possible guard against an accidental public API: see
    // docs/issues/CORE-03-api-stability.md. (The binary-compatibility validator is applied once at
    // the root instead of per module; applying it here as well is a double-apply error.)
    //
    // Samples reuse this convention for the Compose/iOS wiring but are not published, so strict
    // mode there is pure ceremony. They opt out with `hijri.explicitApi=false`.
    if (project.findProperty("hijri.explicitApi") != "false") {
        explicitApi()
    }

    android {
        namespace = project.findProperty("android.namespace") as? String
            ?: "com.muazdev.hijricalendar.${project.name.replace("-", ".")}"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "calendar"
            isStatic = true
            binaryOption("bundleId", "com.muazdev.hijricalendar.${project.name}")
        }
    }

    // No Compose *dependencies* here on purpose (review-remediation 04). The plugin block above
    // opts the module into the Compose toolchain; which Compose artifacts a module gets is that
    // module's own decision. Injecting foundation/material3/components-resources from the
    // convention meant every module that wanted only `compose.runtime` — calendar-core, before it
    // stopped using this convention — published a POM dragging the whole UI stack behind a data
    // model. Modules that draw declare their Compose deps in their own build script.
}
