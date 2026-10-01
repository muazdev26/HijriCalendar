import org.gradle.api.tasks.testing.Test

plugins {
    id("hijri.multiplatform.library")
    id("hijri.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.calendarCore)
            implementation(libs.hijrah.datetime)
            implementation(libs.kotlinx.datetime)
        }
        // Previews live in androidMain, so the @Preview annotation is an androidMain dependency
        // rather than a commonMain one. It used to be commonMain, which put an annotation from a
        // non-api dependency on 14 public declarations in the published ABI — see UI-07.
        androidMain.dependencies {
            implementation(libs.compose.multiplatform.ui.tooling.preview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Multiplatform composable-test harness. runComposeUiTest resolves to a Skiko-backed
            // desktop runner, so these land in `./gradlew :calendar-ui:desktopTest` — the command
            // AGENTS.md already names as the verification loop — rather than only in an
            // instrumented `connectedAndroidTest` on a device. See UI-02.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(libs.compose.multiplatform.ui.test)
        }
        // compose.uiTest contributes the Skiko *API* (org.jetbrains.skiko:skiko-awt) but not the
        // platform natives, so the first harness run died with
        //   LibraryLoadException: Cannot find libskiko-macos-arm64.dylib.sha256
        // `desktop.currentOs` is the plugin notation for the per-OS skiko-awt-runtime variant
        // (skiko-awt-runtime-macos-arm64 here, -linux-x64 on the ubuntu CI job), so this stays
        // correct on every host without hardcoding an OS. Desktop-only, hence not in commonTest.
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

// The composable tests render through an offscreen surface, but AWT probes for a display unless
// told not to — and the ubuntu CI runner has none. Without this, :calendar-ui:desktopTest passes
// on a developer Mac and fails on CI for a reason that has nothing to do with the calendar.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}
