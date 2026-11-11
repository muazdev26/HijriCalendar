plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// This consumer compiles against `hijri-calendar-compose` by published coordinate.
// This is the artifact the README tells people to install, so it must be covered by the gate.
// The library publishes Compose as `api`, so the consumer gets it transitively.

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
    implementation(platform(libs.androidx.compose.bom))
}
