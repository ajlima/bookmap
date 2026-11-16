// A standalone build that compiles a CopyMapper program to a GraalVM native image and runs it.
// Run it from the repository root: ./gradlew -p copymapper-native-test nativeRun
// The task nativeRun needs GraalVM for JDK 21. Set GRAALVM_HOME to its directory.
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

// The pt.dazz.copymapper libraries come from the root build.
includeBuild("..")

rootProject.name = "copymapper-native-test"
