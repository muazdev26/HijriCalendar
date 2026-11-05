plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("hijri.publish")
}

kotlin {
    // Not a KMP module, so this cannot come from the `hijri.multiplatform.library` convention.
    // See docs/issues/CORE-03-api-stability.md.
    explicitApi()
}

android {
    namespace = project.findProperty("android.namespace") as? String
        ?: "com.muazdev.hijricalendar.widget.glance"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Glance resolves an ActionCallback at tap time *by name*: `AsyncRequestWorker` reads the
        // class name Glance baked into the widget's PendingIntent and calls
        // `Class.forName(name).getDeclaredConstructor().newInstance()`. Glance's own consumer rule
        // (`-keep public class * extends ActionCallback`) keeps the *class*, but not its no-arg
        // constructor — on a minified consumer those callbacks are kept and their constructors are
        // not, so every widget navigation tap dies with `NoSuchMethodException` and the widget
        // silently freezes. Shipping the constructor rule here means the consumer does not have to
        // rediscover this on a device.
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // (consumerProguardFiles lives in defaultConfig above, where a library declares it; the
    // constructor rule is `consumer-rules.pro`.)

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":calendar-widget-data"))
    // Public config/refresh API exposes GlanceId, so consumers on the compile classpath need Glance
    api(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    // Month-length overrides persist to DataStore Preferences so the app and its widgets — one
    // package, one process — render from the same table. See HijriMonthLengthsRepository.
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose UI is `api`, not `implementation`, and the distinction is load-bearing rather than
    // stylistic: the four public live-preview composables take `androidx.compose.ui.Modifier` and
    // `androidx.compose.ui.unit.DpSize` in their signatures and are annotated
    // `androidx.compose.runtime.Composable`. `implementation` publishes at *runtime* scope, so a
    // consumer's compile classpath would not resolve those types and the documented integration
    // path — a settings screen calling `HijriWidgetLivePreview` — would not compile. Nothing in
    // this repo can catch that: the sample app has its own Compose dependencies, so its classpath
    // is complete and CI stays green; the defect exists only in the published POM.
    //
    // The BOM must move with them, or a consumer resolving a different Compose version would
    // align this module's `api` artifacts against a train it did not choose.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)

    // Internal only: nothing public in this module names a material3, foundation, activity-compose
    // or ViewModel type. `foundation` backs the `Box`/`background` placeholder the previews paint
    // before the first composition lands, and `material3` is used by the `providePreview` trees —
    // both private implementation detail. A consumer building its own settings screen declares
    // whatever *it* composes with.
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

// `StaticPreviewLayoutTest` (WG-14) reads `src/main/res/layout/*.xml` off disk rather than off the
// classpath, because a `previewLayout` is inflated by the framework at runtime and never reaches a
// unit test's resources.
//
// That has a consequence Gradle will not infer: the layout XMLs are not an input to the test task,
// so editing one does not re-run the test. Verified by re-injecting the original four-row drift and
// watching `testDebugUnitTest` report UP-TO-DATE -- the gate would have sat dark in CI while the
// file it exists to guard was edited. Declaring them as inputs is what makes it a gate.
//
// `PathSensitivity.RELATIVE` so a checkout at a different absolute path does not invalidate.
tasks.withType<Test>().configureEach {
    inputs.files(fileTree("src/main/res/layout") { include("**/preview_layout.xml") })
        .withPropertyName("staticPreviewLayouts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
