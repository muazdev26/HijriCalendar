plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.zip.ZipFile

android {
    namespace = "com.muazdev.hijricalendar.sample"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.muazdev.hijricalendar.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // The release build is the only place a consumer's R8 run can be observed: widget
            // navigation is driven by class names that Glance resolves reflectively at tap time
            // (`ActionCallbackBroadcastReceiver` reads the callback class out of the intent), so a
            // stripped callback is a *silent* dead button rather than a build error. `debug` never
            // shrinks, which is why this was invisible before. CI assembles this variant.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // A sample has no release keystore; the debug key lets the minified APK be installed on
            // a device for the tap check. Never do this in a shipped app.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// ---------------------------------------------------------------------------------------------
// R8 keeps a class name or it does not, and nothing in the build notices the difference.
//
// Glance resolves an `ActionCallback` *reflectively*: at tap time `AsyncRequestWorker` reads the
// class name that was baked into the widget's `PendingIntent` and instantiates it. If R8 renamed or
// removed any of the five navigation callbacks, the widget still renders and the arrows still look
// tappable — the tap just lands on a class that cannot be found, and the only symptom is a dead
// button in a release build. That is unwitnessable from `debug` (never shrunk) and unwitnessable
// from `assembleRelease` alone (it succeeds either way), which is why this reads R8's own mapping
// back and fails the build.
//
// The names are kept today by *consumer* rules that ship inside the libraries — Glance keeps
// `ActionCallback` classes, WorkManager keeps `ListenableWorker`, the manifest keeps every receiver
// — but none of those rules keeps a *constructor*, so this file is also what notices when R8
// strips one. See calendar-widget-glance/consumer-rules.pro (the tap-time cause) and the sample's
// own proguard-rules.pro.
// ---------------------------------------------------------------------------------------------
val r8MappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.prt")

val widgetRuntimeClassesKeptByR8 = listOf(
    // The six receivers the launcher instantiates by manifest name.
    "com.muazdev.hijricalendar.widget.glance.HijriCalendarWidgetReceiver",
    "com.muazdev.hijricalendar.widget.glance.HijriTodayWidgetReceiver",
    "com.muazdev.hijricalendar.widget.glance.HijriDateWidgetReceiver",
    "com.muazdev.hijricalendar.widget.glance.GregorianDateWidgetReceiver",
    "com.muazdev.hijricalendar.widget.glance.HijriDualDateWidgetReceiver",
    "com.muazdev.hijricalendar.widget.glance.HijriCompactDateWidgetReceiver",
    // The five callbacks a tap resolves by name.
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetPrevMonthCallback",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetNextMonthCallback",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshCallback",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetTodayResetCallback",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetSelectDayCallback",
    // Background entry points: the WorkManager worker is named in its own DB row, the two alarm
    // receivers in an explicit PendingIntent.
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshWorker",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetMidnightAlarmReceiver",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetUpdateReceiver",
    // The launcher entry point.
    "com.muazdev.hijricalendar.sample.MainActivity",
)

// The five callbacks that are reflectively instantiated at tap time — the only classes in the
// list above whose no-arg constructor must outlive shrinking.
val widgetRuntimeCallbacksKeptByR8 = listOf(
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetPrevMonthCallback",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetNextMonthCallback",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshCallback",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetTodayResetCallback",
    "com.muazdev.hijricalendar.widget.glance.HijriWidgetSelectDayCallback",
)

/**
 * Reads R8's own mapping back and fails when a class the widget resolves **by name** was renamed or
 * removed. A dedicated type rather than a `doLast` closure: a closure captures the build script as
 * its receiver, which the configuration cache cannot serialise (and `java.io.File` is shadowed by
 * the `java` extension there anyway).
 */
abstract class VerifyWidgetRuntimeKeptByR8 : DefaultTask() {

    @get:InputFile
    abstract val mappingFile: RegularFileProperty

    @get:Input
    abstract val requiredClasses: ListProperty<String>

    // Glance calls `getDeclaredConstructor().newInstance()` on the callback class, so keeping the
    // class is not enough: the no-arg constructor itself must survive. A class in the mapping says
    // nothing about whether it can be instantiated, which is exactly how the six callbacks were
    // present and every widget tap still died with `NoSuchMethodException`.
    @get:Input
    abstract val requiredNoArgConstructors: ListProperty<String>

    @TaskAction
    fun verify() {
        // AGP 9 no longer writes `mapping.txt`: it writes `mapping.prt`, a ZIP archive with one
        // entry per original class, each in the same text format the old file used (headers
        // `<fqn> -> <name>:` then indented members). Read every entry and concatenate them; the
        // block-splitting below works unchanged on the result.
        val lines = ZipFile(mappingFile.get().asFile).use { zip ->
            zip.entries().toList().flatMap { entry ->
                zip.getInputStream(entry).bufferedReader().readLines()
            }
        }
        // An identity-mapped entry looks like `<fqn> -> <fqn>:`; a renamed one ends in anything else.
        val identityMapped = lines
            .filter { it.endsWith(":") && " -> " in it }
            .map { it.substringBefore(" -> ") }
            .toSet()

        val missing = requiredClasses.get().filterNot { it in identityMapped }
        if (missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("R8 renamed or removed classes the widget resolves by name:")
                    missing.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Widget taps and background refresh would silently do nothing in a")
                    appendLine("release build. Fix the keep rule (see")
                    appendLine("calendar-widget-glance/consumer-rules.pro and")
                    appendLine("sample-android-app/proguard-rules.pro).")
                },
            )
        }

        // Split the mapping into per-class blocks: a header line is a top-level `<fqn> -> <fqn>:`,
        // a member line is anything indented. A constructor is kept when a member line under the
        // class names `<init>`.
        val memberBlocks = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        for (line in lines) {
            if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("#") &&
                " -> " in line && line.endsWith(":")
            ) {
                current = line.substringBefore(" -> ")
                memberBlocks.getOrPut(current) { mutableListOf() }
            } else if (current != null) {
                memberBlocks.getValue(current).add(line)
            }
        }

        val missingConstructor = requiredNoArgConstructors.get().filterNot { fqn ->
            memberBlocks[fqn].orEmpty().any { "<init>" in it }
        }
        if (missingConstructor.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("R8 kept these classes but stripped their no-arg constructor, so")
                    appendLine("Glance's reflective instantiation fails at tap time:")
                    missingConstructor.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("The widget renders, the arrows look tappable, and every tap lands on")
                    appendLine("Class.forName(name).getDeclaredConstructor().newInstance() throwing")
                    appendLine("NoSuchMethodException. Keep the constructors (see")
                    appendLine("calendar-widget-glance/consumer-rules.pro).")
                },
            )
        }
        logger.lifecycle(
            "R8 kept all ${requiredClasses.get().size} widget runtime entry points and " +
                "${requiredNoArgConstructors.get().size} reflective constructors.",
        )
    }
}

val verifyReleaseR8KeepsWidgetCallbacks =
    tasks.register<VerifyWidgetRuntimeKeptByR8>("verifyReleaseR8KeepsWidgetCallbacks") {
        group = "verification"
        description =
            "Fails when a minified release renames or strips a class the widget resolves by name."

        dependsOn("minifyReleaseWithR8")
        mappingFile.set(r8MappingFile)
        requiredClasses.set(widgetRuntimeClassesKeptByR8)
        requiredNoArgConstructors.set(widgetRuntimeCallbacksKeptByR8)
    }

// `assembleRelease` is registered by AGP later in evaluation, so match it lazily rather than by name.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyReleaseR8KeepsWidgetCallbacks)
}

dependencies {
    implementation(project(":sample-shared"))
    implementation(project(":calendar-core"))
    implementation(project(":calendar-ui"))
    implementation(project(":calendar-widget-glance"))
    implementation(libs.hijrah.datetime)
    implementation(libs.kotlinx.datetime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
