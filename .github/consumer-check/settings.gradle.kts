// Throwaway consumer for the resolution gate — see `.github/scripts/check-consumer-resolution.sh`
// and docs/widgets/glance/WG-01-compose-runtime-scope.md.
//
// This is a SEPARATE Gradle build. It must not become a project of the main build: it has to
// resolve the *published* artifacts from a local repository, with no project dependency and no
// transitive access to the main build's configurations. A `project(":calendar-widget-glance")`
// dependency would hand it the main build's `implementation` dependencies for free and the gate
// would pass no matter what the POM says.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// The main build's catalog, so the gate cannot drift onto a different AGP/Kotlin/Compose train
// than the one it is checking. `libs.versions.toml` is self-contained, so reusing it wholesale
// costs nothing.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
    repositories {
        // First, and before google()/mavenCentral(): the point of the gate is that everything
        // below resolves from *this* repository plus transitive POM metadata. If a dependency
        // leaked out of the library's compile scope it would simply be absent here, which is the
        // failure we want.
        maven {
            name = "consumerCheck"
            url = uri(rootDir.resolve("../../build/consumer-repo"))
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "consumer-resolution-check"