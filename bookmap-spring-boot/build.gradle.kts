plugins { `java-library` }

dependencies {
    api(project(":bookmap-core"))
    api(platform(libs.spring.boot.bom))
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Generates META-INF/spring-configuration-metadata.json at compile time.
    // The annotationProcessor configuration is not in the published POM or Gradle module metadata.
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-test")
    // Reads the generated metadata in the tests, as an IDE does.
    testImplementation("org.springframework.boot:spring-boot-configuration-metadata")
}

// The configuration processor merges META-INF/additional-spring-configuration-metadata.json
// into the generated metadata. It reads the file from the output of processResources.
tasks.named<JavaCompile>("compileJava") {
    inputs.files(tasks.named("processResources"))
}
