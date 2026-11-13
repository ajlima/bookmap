plugins {
    application
    id("org.graalvm.buildtools.native") version "1.1.18"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("pt.dazz.bookmap:bookmap-core")
    implementation("pt.dazz.bookmap:bookmap-transport-simulator")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor")
}

application {
    mainClass = "pt.dazz.bookmap.nativetest.NativeSmokeTest"
}

graalvmNative {
    // Use only the configuration that the BookMap JARs contain. This proves that the
    // generated code and bookmap-core need no extra reachability metadata.
    metadataRepository {
        enabled = false
    }
    binaries {
        named("main") {
            imageName = "bookmap-native-test"
        }
    }
}
