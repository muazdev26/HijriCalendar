plugins {
    `kotlin-dsl`
    // For @Serializable parsing of pakistan-fixes.json at build time (review-remediation 05).
    alias(libs.plugins.kotlin.serialization)
}

group = "com.muazdev.hijricalendar.buildlogic"

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.composeCompiler.gradlePlugin)
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.vanniktech.publish.gradlePlugin)
    implementation(libs.binary.compatibility.validator.gradlePlugin)
    // Reads pakistan-fixes.json at build time to generate the FIXES table (review-remediation 05).
    implementation(libs.kotlinx.serialization.json)
}
