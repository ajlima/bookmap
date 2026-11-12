plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":bookmap-core"))
    api(libs.javapoet)
    implementation(libs.jackson.databind)

    testImplementation(project(":bookmap-processor"))
    testImplementation(libs.compile.testing)
    testImplementation(libs.lombok)
    testImplementation(libs.jimfs)
}

// The test fixtures (test copybooks for the build plugins) are not published.
val javaComponent = components["java"] as AdhocComponentWithVariants
listOf("testFixturesApiElements", "testFixturesRuntimeElements").forEach { name ->
    javaComponent.withVariantsFromConfiguration(configurations[name]) { skip() }
}
// The sources JAR of the root build creates the sources variant of the fixtures later.
configurations.matching { it.name == "testFixturesSourcesElements" }.configureEach {
    javaComponent.withVariantsFromConfiguration(this) { skip() }
}
