plugins {
    `kotlin-dsl`
}

group = "com.muazdev.hijricalendar.buildlogic"

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.composeCompiler.gradlePlugin)
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.vanniktech.publish.gradlePlugin)
}
