// A standalone build that uses the BookMap copybook plugin from the source of this repository.
// Run it from the repository root: ./gradlew -p bookmap-examples-gradle-plugin build
pluginManagement {
    // The plugin pt.dazz.bookmap.copybook comes from the root build.
    includeBuild("..")
}

// The pt.dazz.bookmap libraries come from the root build too.
includeBuild("..")

rootProject.name = "bookmap-examples-gradle-plugin"
