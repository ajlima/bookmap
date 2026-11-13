import java.util.zip.ZipFile

plugins { `java-library` }

// A stub of the IBM CICS TG client API (package com.ibm.ctg.client). It is not published.
// The main code compiles against it, and the tests run with it. Users add the real ctgclient.jar.
val ctgStub: SourceSet = sourceSets.create("ctgStub")

dependencies {
    api(project(":bookmap-core"))
    compileOnly(ctgStub.output)
    testImplementation(ctgStub.output)
    testAnnotationProcessor(project(":bookmap-processor"))
}

tasks.named<JavaCompile>("compileCtgStubJava") {
    options.release = 21
}

// The stub for the tests of other modules, for example bookmap-spring-boot.
// This configuration is not a variant of the java component. Thus, it is not published.
val ctgStubJar = tasks.register<Jar>("ctgStubJar") {
    archiveClassifier = "ctg-stub"
    from(ctgStub.output)
}

configurations.consumable("ctgStubElements") {
    outgoing.artifact(ctgStubJar)
}

// The published jar must not contain IBM classes.
val verifyNoIbmClasses by tasks.registering {
    val jarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(jarFile)
    doLast {
        ZipFile(jarFile.get().asFile).use { zip ->
            val ibm = zip.entries().asSequence().map { it.name }.filter { it.startsWith("com/ibm/") }.toList()
            check(ibm.isEmpty()) { "The jar contains IBM classes: $ibm" }
        }
    }
}

tasks.named("check") { dependsOn(verifyNoIbmClasses) }
