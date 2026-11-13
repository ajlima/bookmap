// A standalone build that compiles a BookMap program to a GraalVM native image and runs it.
// Run it from the repository root: ./gradlew -p bookmap-native-test nativeRun
// The task nativeRun needs GraalVM for JDK 21. Set GRAALVM_HOME to its directory.
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

// The pt.dazz.bookmap libraries come from the root build.
includeBuild("..")

rootProject.name = "bookmap-native-test"
