plugins {
    base
    alias(libs.plugins.maven.publish) apply false
}

import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

val catalog = the<org.gradle.accessors.dm.LibrariesForLibs>()

// Modules that go to Maven Central, with their POM descriptions.
val publishedModules = mapOf(
    "bookmap-core" to "BookMap core: annotations, runtime codecs, client, and transport SPI.",
    "bookmap-processor" to "BookMap annotation processor: generates the codecs at compile time.",
    "bookmap-transport-simulator" to "BookMap in-memory transport for tests.",
    "bookmap-transport-ctg" to "BookMap transport for the CICS Transaction Gateway (ECI).",
    "bookmap-spring-boot" to "BookMap Spring Boot auto-configuration.",
    "bookmap-copybook" to "BookMap copybook generator: parses COBOL copybooks and generates BookMap classes.",
    "bookmap-copybook-cli" to "BookMap copybook generator command-line program.",
    "bookmap-copybook-gradle-plugin" to "BookMap copybook generator Gradle plugin.",
    "bookmap-copybook-maven-plugin" to "BookMap copybook generator Maven plugin.",
)

// Modules that are Gradle plugins. They publish the plugin JAR and the plugin marker artifact.
val gradlePluginModules = setOf("bookmap-copybook-gradle-plugin")

allprojects {
    group = "pt.dazz.bookmap"
    // The release workflow passes -PreleaseVersion=X.Y.Z. Local builds use the snapshot version.
    version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"(platform(catalog.junit.bom))
        "testImplementation"(catalog.junit.jupiter)
        "testImplementation"(catalog.assertj.core)
        "testRuntimeOnly"(catalog.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    if (name != "bookmap-examples") {
        apply(plugin = "jacoco")

        extensions.configure<JacocoPluginExtension> {
            toolVersion = catalog.versions.jacoco.get()
        }

        tasks.withType<JacocoReport>().configureEach {
            dependsOn(tasks.withType<Test>())
            // The XML report lists the missed lines and branches for each source file.
            reports.xml.required = true
        }

        tasks.withType<JacocoCoverageVerification>().configureEach {
            dependsOn(tasks.withType<Test>())
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = "1.0".toBigDecimal()
                    }
                    limit {
                        counter = "BRANCH"
                        value = "COVEREDRATIO"
                        minimum = "1.0".toBigDecimal()
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(tasks.withType<JacocoCoverageVerification>())
            dependsOn(tasks.withType<JacocoReport>())
        }
    }

    publishedModules[name]?.let { moduleDescription ->
        apply(plugin = "com.vanniktech.maven.publish")

        // A file repository for the integration tests of the Maven plugin
        // (task publishAllPublicationsToIntegrationTestRepository).
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "integrationTest"
                    url = uri(rootProject.layout.buildDirectory.dir("integration-test-repo"))
                }
            }
        }

        extensions.configure<MavenPublishBaseExtension> {
            // The subprojects block runs before the build file of the module applies
            // java-gradle-plugin. So select the module by name, and wait for the plugin.
            if (name in gradlePluginModules) {
                val publishing = this
                pluginManager.withPlugin("java-gradle-plugin") {
                    publishing.configure(
                        GradlePlugin(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources()))
                }
            } else {
                configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
            }
            publishToMavenCentral(automaticRelease = true)
            // Sign only when a key is present (CI release). Local publishToMavenLocal works without a key.
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }
            coordinates(group.toString(), name, version.toString())
            pom {
                if (project.name == "bookmap-copybook-maven-plugin") {
                    packaging = "maven-plugin"
                }
                this.name.set(project.name)
                description.set(moduleDescription)
                inceptionYear.set("2026")
                url.set("https://github.com/ajlima/bookmap")
                licenses {
                    license {
                        this.name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("ajlima")
                        this.name.set("Alexandre Lima")
                        url.set("https://github.com/ajlima")
                    }
                }
                scm {
                    url.set("https://github.com/ajlima/bookmap")
                    connection.set("scm:git:https://github.com/ajlima/bookmap.git")
                    developerConnection.set("scm:git:ssh://git@github.com/ajlima/bookmap.git")
                }
            }
        }
    }
}
