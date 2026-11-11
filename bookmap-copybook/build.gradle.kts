plugins { `java-library` }

dependencies {
    api(project(":bookmap-core"))
    api(libs.javapoet)
    implementation(libs.jackson.databind)

    testImplementation(project(":bookmap-processor"))
    testImplementation(libs.compile.testing)
}
