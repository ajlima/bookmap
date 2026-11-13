plugins { `java-library` }

dependencies {
    api(project(":bookmap-core"))
    testAnnotationProcessor(project(":bookmap-processor"))
}
