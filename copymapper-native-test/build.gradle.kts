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
    implementation("pt.dazz.copymapper:copymapper-core")
    implementation("pt.dazz.copymapper:copymapper-transport-simulator")
    annotationProcessor("pt.dazz.copymapper:copymapper-processor")
}

application {
    mainClass = "pt.dazz.copymapper.nativetest.NativeSmokeTest"
}

graalvmNative {
    // Use only the configuration that the CopyMapper JARs contain. This proves that the
    // generated code and copymapper-core need no extra reachability metadata.
    metadataRepository {
        enabled = false
    }
    binaries {
        named("main") {
            imageName = "copymapper-native-test"
        }
    }
}
