plugins { `java-library` }

dependencies {
    implementation(project(":copymapper-core"))
    implementation(libs.javapoet)

    testImplementation(libs.compile.testing)
    testImplementation(libs.lombok)
}
