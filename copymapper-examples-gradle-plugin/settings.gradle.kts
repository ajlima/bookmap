// A standalone build that uses the CopyMapper copybook plugin from the source of this repository.
// Run it from the repository root: ./gradlew -p copymapper-examples-gradle-plugin build
pluginManagement {
    // The plugin pt.dazz.copymapper.copybook comes from the root build.
    includeBuild("..")
}

// The pt.dazz.copymapper libraries come from the root build too.
includeBuild("..")

rootProject.name = "copymapper-examples-gradle-plugin"
