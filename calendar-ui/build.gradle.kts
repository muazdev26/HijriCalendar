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
            implementation(libs.material.icons.extended)
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:${libs.versions.composeMultiplatform.get()}")
        }
    }
}
