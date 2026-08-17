plugins {
    id("hijri.multiplatform.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.calendarCore)
            implementation(projects.calendarUi)
            implementation(libs.hijrah.datetime)
            implementation(libs.kotlinx.datetime)
        }
    }
}
