plugins { `java-gradle-plugin` }

dependencies {
    implementation(project(":bookmap-copybook"))
}

gradlePlugin {
    plugins {
        create("bookmapCopybook") {
            id = "pt.dazz.bookmap.copybook"
            implementationClass = "pt.dazz.bookmap.copybook.gradle.BookmapCopybookPlugin"
            displayName = "BookMap copybook generator"
            description = "Generates BookMap classes from COBOL copybooks."
        }
    }
}

// The TestKit tests compile the generated classes against the bookmap-core JAR.
val coreJar = project(":bookmap-core").tasks.named<Jar>("jar")
tasks.test {
    dependsOn(coreJar)
    systemProperty("bookmap.core.jar", coreJar.get().archiveFile.get().asFile.absolutePath)
}
