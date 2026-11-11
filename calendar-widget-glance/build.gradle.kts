import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask

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

// `StaticPreviewLayoutTest` reads `src/main/res/layout/*.xml` off disk rather than off the
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

// ── Public-surface gate ──────────────────────────────────────────────────────────────────────────
//
// AGP 9 compiles Kotlin through its built-in support, which registers no plugin id the
// binary-compatibility validator recognises — applying `org.jetbrains.kotlin.android` is rejected
// outright ("no longer required for Kotlin support since AGP 9.0"). The root plugin therefore
// configures nothing here, and this module's public surface was reviewed by a hand-written list of
// declaration *names* instead. A name list cannot see a changed signature, a default that moved, or
// a reordered companion object — the three shapes of break that actually reach a consumer.
//
// These tasks wire the validator's own task types to the release class output, so
// `api/calendar-widget-glance.api` is a real ABI dump and `apiCheck` fails on any of them.
val apiFileName = "${project.name}.api"

// `bcv-rt-jvm-cp` is the declarable half of the validator's own worker classpath;
// `bcv-rt-jvm-cp-resolver` is the resolvable one that extends it and is what the task consumes.
// The validator adds the Kotlin metadata reader to the former only when it sees a Kotlin plugin id,
// and AGP 9's built-in Kotlin is invisible to it for the reason above — so without this dependency
// the dump task succeeds and reports no Kotlin declarations at all, an empty API file that reads as
// coverage.
dependencies.add("bcv-rt-jvm-cp", "org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
val bcvRuntimeClasspath = configurations.named("bcv-rt-jvm-cp-resolver")

// The Kotlin compile output, not the AAR and not the javac output: the AAR's `classes.jar` carries
// the generated `R` classes, which the validator would dump as several hundred public fields.
//
// The copy drops the Compose compiler's `ComposableSingletons$<File>Kt` holders. Those classes are
// public in the bytecode but compiler-generated, and their member names embed a hash of the lambda
// they cache (`getLambda$-1823173490$calendar_widget_glance`) — a hash that changes when anything
// inside that lambda changes. Dumped, they would fail `apiCheck` on an edit that altered no
// signature at all, which is the definition of a gate nobody trusts. Filtering by pattern rather
// than by name means a new composable file needs no build change here.
val apiClasses = tasks.register<Sync>("apiClasses") {
    description = "Stages the release class output for the API dump, minus compiler-generated holders."
    from(tasks.named("compileReleaseKotlin").map { it.outputs.files }) {
        exclude("**/ComposableSingletons*.class")
    }
    into(layout.buildDirectory.dir("api-classes/release"))
}

val apiBuild = tasks.register<KotlinApiBuildTask>("apiBuild") {
    description = "Builds the release ABI dump. Complementary to `apiDump`, do not call directly."
    inputClassesDirs.from(apiClasses.map { it.destinationDir })
    outputApiFile.set(layout.buildDirectory.file("api/$apiFileName"))
    runtimeClasspath.from(bcvRuntimeClasspath)
}

val apiCheck = tasks.register<KotlinApiCompareTask>("apiCheck") {
    group = "verification"
    description = "Checks the release ABI against api/$apiFileName."
    projectApiFile.set(layout.projectDirectory.file("api/$apiFileName"))
    generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
}

tasks.register<Copy>("apiDump") {
    group = "other"
    description = "Writes the release ABI dump to api/$apiFileName."
    from(apiBuild.flatMap { it.outputApiFile })
    into(layout.projectDirectory.dir("api"))
}

// ── R8 callback name check ─────────────────────────────────────────────────────────────────────
//
// Every ActionCallback subclass must be named HijriWidget*Callback so the consumer R8 rule
// (`-keep public class HijriWidget*Callback { <init>(); }`) keeps its no-arg constructor.
// A callback that slips through with a different name is kept as a class but loses its
// constructor, so every tap dies with NoSuchMethodException.
//
// Uses a source scan (regex) rather than class scanning — simpler and catches the problem
// before compilation.
val verifyWidgetCallbackNames = tasks.register("verifyWidgetCallbackNames") {
    description = "Verifies all ActionCallback subclasses match the HijriWidget*Callback naming pattern."
    val sourceDir = layout.projectDirectory.dir("src/main/java")
    inputs.dir(sourceDir).withPropertyName("callbackSources").withPathSensitivity(PathSensitivity.RELATIVE)
    doLast {
        val violations = mutableListOf<String>()

        // Whole-file regex that matches class or object declarations whose supertype list
        // mentions ActionCallback. The [^{]*? part spans newlines (DOTALL) so multi-line
        // supertype lists are matched, and stops at the body's opening brace.
        val declaration = Regex(
            """\b(?:class|object)\s+(\w+)[^{]*?\bActionCallback\b""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )

        sourceDir.asFileTree.matching { include("**/*.kt") }.forEach { file ->
            val content = file.readText()
            declaration.findAll(content).forEach { match ->
                val className = match.groupValues[1]
                if (!className.startsWith("HijriWidget") || !className.endsWith("Callback")) {
                    // Compute line number from the match's start offset
                    val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1
                    violations.add("${file.path}:${lineNumber}: $className does not match HijriWidget*Callback pattern")
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "R8 callback naming violations found:\n" +
                    violations.joinToString("\n")
            )
        }
    }
}

tasks.named("check") { dependsOn(apiCheck, verifyWidgetCallbackNames) }
