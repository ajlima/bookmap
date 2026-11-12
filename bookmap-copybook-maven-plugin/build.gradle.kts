import org.apache.tools.ant.filters.ReplaceTokens

dependencies {
    implementation(project(":bookmap-copybook"))
    implementation(libs.plexus.utils)

    // Maven gives these at run time (scope provided).
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)

    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testImplementation(testFixtures(project(":bookmap-copybook")))
}

// The plugin descriptor is written by hand (src/main/resources/META-INF/maven/plugin.xml).
// PluginDescriptorTest compares it with the fields of GenerateMojo. The build sets the version.
tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("META-INF/maven/plugin.xml") {
        filter<ReplaceTokens>("tokens" to mapOf("pluginVersion" to pluginVersion))
    }
}

// Integration tests: they download Maven 3.6.3 and 3.9.16 and run them on small projects.
// The BookMap artifacts come from a file repository that the build publishes.
val integrationTest: SourceSet = sourceSets.create("integrationTest")
configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

val integrationTestRepository = rootProject.layout.buildDirectory.dir("integration-test-repo")
val integrationTestModules = listOf("bookmap-core", "bookmap-processor", "bookmap-copybook",
    "bookmap-copybook-maven-plugin")

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the plugin with real Maven versions."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    integrationTestModules.forEach {
        dependsOn(":$it:publishAllPublicationsToIntegrationTestRepository")
    }
    systemProperty("bookmap.version", project.version.toString())
    systemProperty("bookmap.repository", integrationTestRepository.get().asFile.absolutePath)
    systemProperty("maven.distributions",
        layout.buildDirectory.dir("maven-distributions").get().asFile.absolutePath)
    systemProperty("maven.localRepository",
        layout.buildDirectory.dir("integration-test-m2").get().asFile.absolutePath)
    // The Maven runs read published files, so do not take the result from a cache.
    outputs.upToDateWhen { false }
    shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(integrationTestTask) }

dependencies {
    "integrationTestImplementation"(testFixtures(project(":bookmap-copybook")))
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter-params")
}
