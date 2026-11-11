plugins { `java-library` }

dependencies {
    implementation(project(":bookmap-core"))
    compileOnly(libs.lombok)
    // The processor runs before Lombok on purpose: it must detect Lombok by name.
    annotationProcessor(project(":bookmap-processor"))
    annotationProcessor(libs.lombok)

    testImplementation(project(":bookmap-transport-simulator"))
}
