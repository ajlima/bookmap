plugins {
    java
    id("pt.dazz.bookmap.copybook")
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
    annotationProcessor("pt.dazz.bookmap:bookmap-processor")

    testImplementation("pt.dazz.bookmap:bookmap-transport-simulator")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Generate the classes of each copybook in src/main/copybook. The directory of a copybook
// gives its subpackage: orders/ORDER.cpy goes to pt.dazz.bookmap.examples.plugin.orders.
bookmap {
    copybooks {
        packageName = "pt.dazz.bookmap.examples.plugin"
        copyDirs.from("src/main/copylib")
    }
}

tasks.test {
    useJUnitPlatform()
}
