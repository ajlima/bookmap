plugins { `java-library` }

dependencies {
    implementation(project(":bookmap-copybook"))
}

val mainClass = "pt.dazz.bookmap.copybook.cli.Main"

tasks.jar {
    manifest { attributes("Main-Class" to mainClass) }
}

// An executable JAR with all dependencies: java -jar bookmap-copybook-cli-<version>-all.jar
val allJar by tasks.registering(Jar::class) {
    archiveClassifier = "all"
    manifest { attributes("Main-Class" to mainClass) }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class",
        "META-INF/versions/*/module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("assemble") { dependsOn(allJar) }

plugins.withId("com.vanniktech.maven.publish") {
    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach { artifact(allJar) }
        }
    }
}
