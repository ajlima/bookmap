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
