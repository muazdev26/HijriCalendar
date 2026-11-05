import com.muazdev.hijricalendar.buildlogic.GeneratePakistanFixesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("hijri.publish")
}

// The published data model: no Compose UI, no Compose Gradle plugin, no Compose compiler.
//
// This module deliberately does **not** use the `hijri.multiplatform.library` convention (review-
// remediation 04). The convention applies `org.jetbrains.compose` and the Compose compiler plugin
// and adds foundation/material3/components-resources to commonMain, which made every consumer of
// the date model — a server, a CLI, a plain-JVM app — pull the whole Compose UI stack, and made the
// published `calendar-core` ABI depend on `androidx.compose.*` types. The targets below are the same
// ones the convention sets up; only the Compose wiring is gone.
//
// The one Compose dependency that stays is `androidx.compose.runtime` (see `commonMain` below): the
// value types are `@Immutable` and [com.muazdev.hijricalendar.core.HijriCalendarState] holds its
// fields in snapshot state, which is what makes the grid recompose. It is `implementation` — no
// runtime type appears in a public signature — so it is not on a consumer's compile classpath.
kotlin {
    explicitApi()

    val generatePakistanFixes = tasks.register<GeneratePakistanFixesTask>("generatePakistanFixes") {
        // The JSON is the one source of truth for the Pakistan fix table; the generated Kotlin
        // constant is what `PakistanHijriCalendar.FIXES` exposes.
        inputFile.set(file("src/commonMain/resources/pakistan-fixes.json"))
        outputDir.set(layout.buildDirectory.dir("generated/pakistanFixes/kotlin"))
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
            // The iOS app target links this framework (and re-exports calendar-widget-data through
            // it), so the name is part of the Swift `import calendar` the Xcode project uses.
            baseName = "calendar"
            isStatic = true
            binaryOption("bundleId", "com.muazdev.hijricalendar.${project.name}")
        }
    }

    sourceSets {
        getByName("commonMain") {
            kotlin.srcDir(generatePakistanFixes)
        }
        commonMain.dependencies {
            // `api`: LocalDate is in CalendarDay's public surface, so a consumer that reads
            // `CalendarDay.localDate` must be able to see kotlinx-datetime. As `implementation` the
            // type was on the classpath at runtime but invisible to the compiler — the scope bug
            // the consumer-resolution gate exists to catch (review-remediation 01).
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.collections.immutable)
            api(libs.hijrah.datetime)
            implementation(libs.compose.multiplatform.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
