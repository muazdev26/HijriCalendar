import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.vanniktech.maven.publish")
}

val publishGroup = project.findProperty("publishing.group") as? String ?: "com.muazdev.hijricalendar"
val publishArtifact = project.findProperty("publishing.artifact") as? String ?: project.name
val publishVersion = project.findProperty("publishing.version") as? String ?: "1.0.0"

val hasSigningCredentials = listOf(
    project.findProperty("signing.gnupg.keyName"),
    project.findProperty("signing.keyId"),
    project.findProperty("signing.key"),
).any { it != null }
val signingEnabled = hasSigningCredentials

mavenPublishing {
    publishToMavenCentral()

    coordinates(publishGroup, publishArtifact, publishVersion)

    pom {
        name.set("HijriCalendar")
        description.set("A Kotlin Multiplatform Hijri Calendar library")
        url.set("https://github.com/muazdev26/HijriCalendar")
        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("muazdev26")
                name.set("Muaz")
            }
        }
        scm {
            url.set("https://github.com/muazdev26/HijriCalendar")
            connection.set("scm:git:git://github.com/muazdev26/HijriCalendar.git")
            developerConnection.set("scm:git:ssh://github.com/muazdev26/HijriCalendar.git")
        }
    }

    if (signingEnabled) {
        signAllPublications()
    } else {
        logger.warn(
            "hijri.publish: signing disabled because no GPG key is configured. " +
                "Artifacts will be UNSIGNED and Maven Central will reject them. " +
                "Configure signing.gnupg.keyName (or signing.keyId) before releasing."
        )
    }
}

// A scratch file repository for the consumer-resolution gate (docs/widgets/glance/WG-01).
//
// `calendar-widget-glance` publishes `androidx.compose.ui` as `api` because its public live-preview
// composables take `Modifier`/`DpSize`, but nothing inside this build can observe that: the sample
// app has its own Compose dependencies, so its compile classpath is complete and every in-repo
// check stays green even when the scope is wrong. Only a *consumer* resolving the published POM
// sees the defect. This repository is where the gate's throwaway consumer resolves from, so it is
// added unconditionally rather than behind a property — the script wipes it on every run, and a
// release run never selects this repository.
extensions.configure<PublishingExtension>("publishing") {
    repositories.maven {
        name = "consumerCheck"
        url = uri(rootProject.layout.buildDirectory.dir("consumer-repo").get().asFile)
    }
}

// The AGP Kotlin Multiplatform plugin overwrites the target publication artifactIds (derived
// from the project name) after the vanniktech plugin's own rename runs, so coordinates() alone
// only renames the root metadata publication. Re-apply the rename once every project has
// finished evaluating so it sticks for every publication.
project.gradle.projectsEvaluated {
    val publishing = project.extensions.findByName("publishing") as? PublishingExtension
        ?: return@projectsEvaluated
    publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
        val current = publication.artifactId
        val renamed = when {
            current == project.name -> publishArtifact
            current.startsWith("${project.name}-") -> current.replaceFirst("${project.name}-", "$publishArtifact-")
            else -> return@forEach
        }
        if (renamed != current) {
            publication.artifactId = renamed
        }
    }
}