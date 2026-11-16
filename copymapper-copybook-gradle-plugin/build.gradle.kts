plugins { `java-gradle-plugin` }

dependencies {
    implementation(project(":copymapper-copybook"))

    testImplementation(testFixtures(project(":copymapper-copybook")))
}

gradlePlugin {
    plugins {
        create("copymapperCopybook") {
            id = "pt.dazz.copymapper.copybook"
            implementationClass = "pt.dazz.copymapper.copybook.gradle.CopyMapperCopybookPlugin"
            displayName = "CopyMapper copybook generator"
            description = "Generates CopyMapper classes from COBOL copybooks."
        }
    }
}

// The TestKit tests compile the generated classes against the copymapper-core JAR.
val coreJar = project(":copymapper-core").tasks.named<Jar>("jar")
tasks.test {
    dependsOn(coreJar)
    systemProperty("copymapper.core.jar", coreJar.get().archiveFile.get().asFile.absolutePath)
}
