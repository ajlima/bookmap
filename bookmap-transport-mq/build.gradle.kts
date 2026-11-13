plugins { `java-library` }

dependencies {
    api(project(":bookmap-core"))
    api(libs.jakarta.jms.api)
}
