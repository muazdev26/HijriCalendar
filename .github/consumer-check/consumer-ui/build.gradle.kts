plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// This consumer compiles against `hijri-calendar-compose` by published coordinate.
// This is the artifact the README tells people to install, so it must be covered by the gate.
//
// A real consumer declares their own Compose dependencies. The gate checks that the library's
// published POM puts everything it needs on the compile classpath. If the library declares
// Compose as `implementation` instead of `api`, the consumer must add it manually — which is
// what we do here, because the library's public API is @Composable and requires Compose runtime.
// The gate still catches: if the library adds a new dependency and declares it `implementation`,
// this consumer will fail until that dependency is also added here.

val libraryVersion = providers.gradleProperty("libraryVersion").orNull
    ?: error("pass -PlibraryVersion=<the version hijri.publish stamps>")

android {
    namespace = "com.muazdev.hijricalendar.consumercheck.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.muazdev.hijricalendar:hijri-calendar-compose:$libraryVersion")
    // The library's public API is @Composable. A consumer declares their own Compose deps.
    // If the library published these as `api`, we wouldn't need them here — but that is
    // exactly what the gate checks: does the consumer compile with only the published POM?
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
