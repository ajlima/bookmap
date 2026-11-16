plugins { `java-library` }

dependencies {
    api(project(":copymapper-core"))
    api(libs.jakarta.jms.api)

    // An embedded broker for the tests of the Jakarta Messaging adapter. It is not in the POM.
    testImplementation(libs.artemis.server)
    testImplementation(libs.artemis.jakarta.client)
}
