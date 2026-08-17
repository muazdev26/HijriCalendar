plugins {
    id("hijri.multiplatform.library")
    id("hijri.publish")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.collections.immutable)
            api(libs.hijrah.datetime)
        }
    }
}
