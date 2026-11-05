plugins {
    alias(libs.plugins.android.library)
}

// This consumer compiles against `hijri-calendar-core` by published coordinate.
//
// **No Compose plugin and no Compose dependency, on purpose** (review-remediation 04). This module
// used to apply `org.jetbrains.kotlin.plugin.compose` purely to drag a newer Kotlin onto the
// classpath, and carried `androidx.compose.runtime` to satisfy it — so the gate that is supposed to
// prove "the data model does not need Compose" was itself built with Compose. The Kotlin version
// now comes from the buildscript classpath (see the root build script), leaving the honest
// statement: a consumer of `hijri-calendar-core` compiles the date model with no Compose artifact
// anywhere on its classpath. If core ever leaks a Compose type into a public signature, the compile
// below fails.

val libraryVersion = providers.gradleProperty("libraryVersion").orNull
    ?: error("pass -PlibraryVersion=<the version hijri.publish stamps>")

android {
    namespace = "com.muazdev.hijricalendar.consumercheck.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// AGP 9 bundles Kotlin 2.2.x, which cannot read this library's 2.4.0 metadata ("the binary version
// of its metadata is 2.4.0, expected version is 2.2.0"). consumer-core deliberately applies no
// Kotlin or Compose plugin beyond AGP's built-in support — that is the point of the module — so the
// version check is suppressed instead of pulling in a newer toolchain. This gates the POM's *scope
// claims*, not compiler compatibility; a real consumer is expected to track the library's toolchain.
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    // The ONE dependency. kotlinx-datetime (`CalendarDay.localDate`), hijrahdatetime
    // (`HijrahDate`) and the serialization/immutable-collections types core's signatures name must
    // arrive transitively from this POM — that is the scope claim being checked, so listing them
    // here would defeat the gate rather than strengthen it.
    implementation("com.muazdev.hijricalendar:hijri-calendar-core:$libraryVersion")
}
