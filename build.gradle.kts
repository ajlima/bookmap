plugins {
    base
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.pitest) apply false
}

import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import info.solidsoft.gradle.pitest.PitestPluginExtension

val catalog = the<org.gradle.accessors.dm.LibrariesForLibs>()

// Modules that go to Maven Central, with their POM descriptions.
val publishedModules = mapOf(
    "copymapper-core" to "CopyMapper core: annotations, runtime codecs, client, and transport SPI.",
    "copymapper-processor" to "CopyMapper annotation processor: generates the codecs at compile time.",
    "copymapper-transport-simulator" to "CopyMapper in-memory transport for tests.",
    "copymapper-transport-ctg" to "CopyMapper transport for the CICS Transaction Gateway (ECI).",
    "copymapper-transport-http" to "CopyMapper HTTP transport (binary body), for example for CICS web services.",
    "copymapper-transport-mq" to "CopyMapper transport for IBM MQ (Jakarta Messaging request and reply).",
    "copymapper-spring-boot" to "CopyMapper Spring Boot auto-configuration.",
    "copymapper-copybook" to "CopyMapper copybook generator: parses COBOL copybooks and generates CopyMapper classes.",
    "copymapper-copybook-cli" to "CopyMapper copybook generator command-line program.",
    "copymapper-copybook-gradle-plugin" to "CopyMapper copybook generator Gradle plugin.",
    "copymapper-copybook-maven-plugin" to "CopyMapper copybook generator Maven plugin.",
    "copymapper-runtime" to "CopyMapper runtime: compiles a mapping spec to a layout and moves records between JSON and COBOL bytes.",
)

// Modules with mutation tests (task mutationTest), with the minimum mutation score in percent.
val mutationModules = mapOf(
    "copymapper-core" to 95,
    "copymapper-processor" to 95,
    "copymapper-copybook" to 95,
    "copymapper-runtime" to 95,
)

// Modules that are Gradle plugins. They publish the plugin JAR and the plugin marker artifact.
val gradlePluginModules = setOf("copymapper-copybook-gradle-plugin")

allprojects {
    group = "pt.dazz.copymapper"
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
        "testImplementation"(catalog.archunit)
        "testRuntimeOnly"(catalog.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    if (name != "copymapper-examples") {
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

        // The cyclomatic complexity of each method (config/pmd/complexity.xml). Only the main code.
        apply(plugin = "pmd")
        extensions.configure<PmdExtension> {
            toolVersion = catalog.versions.pmd.get()
            ruleSets = emptyList()
            ruleSetFiles = rootProject.files("config/pmd/complexity.xml")
            sourceSets = listOf(the<SourceSetContainer>()["main"])
            isConsoleOutput = true
        }
    }

    mutationModules[name]?.let { minimumScore ->
        apply(plugin = "info.solidsoft.pitest")
        extensions.configure<PitestPluginExtension> {
            pitestVersion = catalog.versions.pitest.asProvider().get()
            junit5PluginVersion = catalog.versions.pitest.junit5.get()
            targetClasses = setOf("pt.dazz.copymapper.*")
            // The architecture tests read the class files. A mutation does not change their result.
            excludedTestClasses = setOf("*.ArchitectureTest")
            threads = Runtime.getRuntime().availableProcessors()
            outputFormats = setOf("HTML", "XML")
            timestampedReports = false
            mutationThreshold = minimumScore
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
                if (project.name == "copymapper-copybook-maven-plugin") {
                    packaging = "maven-plugin"
                }
                this.name.set(project.name)
                description.set(moduleDescription)
                inceptionYear.set("2026")
                url.set("https://github.com/dazz-pt/copymapper")
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
                    url.set("https://github.com/dazz-pt/copymapper")
                    connection.set("scm:git:https://github.com/dazz-pt/copymapper.git")
                    developerConnection.set("scm:git:ssh://git@github.com/dazz-pt/copymapper.git")
                }
            }
        }
    }
}

// The mutation tests are slow. The build task does not run them. CI runs them in a separate check.
tasks.register("mutationTest") {
    group = "verification"
    description = "Runs the mutation tests (PIT) of " + mutationModules.keys.joinToString() + "."
    dependsOn(mutationModules.keys.map { ":$it:pitest" })
}

// A report of the size of each module: the source files, the classes, and the lines of the main code.
// The report is in build/reports/module-size.md. The task gives no limit.
val sizeModules = subprojects.filter { it.name != "copymapper-examples" && it.file("src/main/java").isDirectory }
val moduleSize = tasks.register("moduleSize") {
    group = "verification"
    description = "Reports the source files, classes, and lines of the main code of each module."
    val sources = sizeModules.associate { module ->
        module.name to module.fileTree("src/main/java") { include("**/*.java") }
    }
    val classes = sizeModules.associate { module ->
        module.name to module.fileTree(module.layout.buildDirectory.dir("classes/java/main")) {
            include("**/*.class")
        }
    }
    dependsOn(sizeModules.map { "${it.path}:classes" })
    sources.values.forEach { inputs.files(it) }
    val report = layout.buildDirectory.file("reports/module-size.md")
    outputs.file(report)
    doLast {
        val anonymous = Regex("""\$\d+$""")
        val rows = sources.keys.sorted().map { module ->
            val files = sources.getValue(module).files
            val lines = files.map { it.readLines() }
            // A code line is not blank and is not only a comment. The count is approximate.
            val codeLines = lines.sumOf { fileLines ->
                fileLines.map { it.trim() }.count { line ->
                    line.isNotEmpty() && !line.startsWith("//") && !line.startsWith("/*") &&
                        !line.startsWith("*")
                }
            }
            // A named class, interface, enum, record, or annotation. Not an anonymous class.
            val classCount = classes.getValue(module).files.count { file ->
                val name = file.nameWithoutExtension
                name != "package-info" && name != "module-info" && !anonymous.containsMatchIn(name)
            }
            val largest = files.zip(lines).maxByOrNull { it.second.size }
            listOf(module, files.size, classCount, lines.sumOf { it.size }, codeLines,
                largest?.let { "${it.first.name} (${it.second.size})" } ?: "-")
        }
        val header = listOf("Module", "Source files", "Classes", "Lines", "Code lines", "Largest file (lines)")
        val table = (listOf(header, header.map { "---" }) + rows)
            .joinToString("\n") { row -> row.joinToString(" | ", "| ", " |") }
        report.get().asFile.writeText(table + "\n")
        logger.lifecycle(table)
    }
}

tasks.named("build") {
    dependsOn(moduleSize)
}
