import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Render-ready projection API shared by the native widget renderers (Glance RemoteViews
// on Android, SwiftUI WidgetKit on iOS). Pure Kotlin, no Compose. Deliberately NOT
// published ("hijri.publish" is not applied); it is an internal adapter over
// calendar-core, and JitPack's publish command only touches calendar-core/calendar-ui.
kotlin {
    android {
        namespace = project.findProperty("android.namespace") as? String
            ?: "com.muazdev.hijricalendar.${project.name.replace("-", ".")}"
        compileSdk = 36
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
            baseName = "WidgetCalendar"
            isStatic = true
            binaryOption("bundleId", "com.muazdev.hijricalendar.widgetdata")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            api(projects.calendarCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}