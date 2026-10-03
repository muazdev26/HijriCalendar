plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.muazdev.hijricalendar.consumercheck"
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
    // The ONE dependency. Coordinates mirror `hijri.publish` + each module's
    // `publishing.artifact`; they are written out rather than shared with the main build so this
    // build cannot accidentally pick up a project dependency or an extra configuration.
    //
    // `hijri-calendar-widget-data` and `hijri-calendar-core` are deliberately NOT listed. They
    // must arrive transitively through the widget-glance POM — which is also what the gate checks,
    // since a missing transitive `compile` entry shows up here as an unresolved import.
    implementation("com.muazdev.hijricalendar:hijri-calendar-widget-glance:2.0.0")
}