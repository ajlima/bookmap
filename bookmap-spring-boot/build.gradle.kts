plugins { `java-library` }

dependencies {
    api(project(":bookmap-core"))
    api(platform(libs.spring.boot.bom))
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Generates META-INF/spring-configuration-metadata.json at compile time.
    // The annotationProcessor configuration is not in the published POM or Gradle module metadata.
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // The transports are optional. The user adds the transport module. They are not in the POM.
    compileOnly(project(":bookmap-transport-ctg"))
    compileOnly(project(":bookmap-transport-http"))

    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation(project(":bookmap-transport-ctg"))
    testImplementation(project(":bookmap-transport-http"))
    // The stub of the IBM CTG client library, for the tests of the JavaGatewayConnector bean.
    testImplementation(project(path = ":bookmap-transport-ctg", configuration = "ctgStubElements"))
    // Reads the generated metadata in the tests, as an IDE does.
    testImplementation("org.springframework.boot:spring-boot-configuration-metadata")
}

// The configuration processor merges META-INF/additional-spring-configuration-metadata.json
// into the generated metadata. It reads the file from the output of processResources.
tasks.named<JavaCompile>("compileJava") {
    inputs.files(tasks.named("processResources"))
    // An incremental compile gives only the changed classes to the configuration processor.
    // The processor then writes the metadata without the descriptions of the other classes.
    options.isIncremental = false
}
