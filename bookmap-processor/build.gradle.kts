plugins { `java-library` }

dependencies {
    implementation(project(":bookmap-core"))
    implementation(libs.javapoet)

    testImplementation(libs.compile.testing)
    testImplementation(libs.lombok)
}
