plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("hijri.publish")
}

android {
    namespace = project.findProperty("android.namespace") as? String
        ?: "com.muazdev.hijricalendar.widget.glance"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":calendar-widget-data"))
    // Public config/refresh API exposes GlanceId, so consumers on the compile classpath need Glance
    api(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose UI for configuration activity and live previews
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
