plugins { `java-library` }

dependencies {
    api(project(":copymapper-core"))
    testAnnotationProcessor(project(":copymapper-processor"))
}
