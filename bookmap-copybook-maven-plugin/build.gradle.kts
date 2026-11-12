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
