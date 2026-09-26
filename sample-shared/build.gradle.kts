import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("hijri.multiplatform.library")
}

kotlin {
    // Re-export calendar-widget-data's API through the `calendar` framework so the iOS app target
    // can `import calendar` and reach `WidgetOptions`/`WidgetOptionsJson`. Without `export` the
    // dependency is compiled and linked but its declarations are absent from the generated
    // framework header, so Swift cannot see them. The convention plugin already created the
    // framework binaries, so configure them instead of declaring new ones.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            export(projects.calendarWidgetData)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: the iOS app target links ONLY the `calendar` framework,
            // and its settings UI needs the widget option schema in Swift. Exporting it here means
            // `import calendar` also vends `WidgetOptions`/`WidgetOptionsJson`, so the app never has
            // to link the `WidgetCalendar` framework too — two static Kotlin/Native frameworks in one
            // binary inject the K/N runtime twice ("runtime assert: runtime injected twice", KT-42254).
            api(projects.calendarWidgetData)
            implementation(projects.calendarCore)
            implementation(projects.calendarUi)
            implementation(libs.hijrah.datetime)
            implementation(libs.kotlinx.datetime)
        }
    }
}
