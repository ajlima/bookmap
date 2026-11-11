plugins { `java-library` }

val copybookGenerator: Configuration by configurations.creating

dependencies {
    implementation(project(":bookmap-core"))
    compileOnly(libs.lombok)
    // The processor runs before Lombok on purpose: it must detect Lombok by name.
    annotationProcessor(project(":bookmap-processor"))
    annotationProcessor(libs.lombok)

    copybookGenerator(project(":bookmap-copybook-cli"))

    testImplementation(project(":bookmap-transport-simulator"))
}

// Generate classes from each copybook in src/main/copybook in each build.
// A file <NAME>.json next to <NAME>.cpy holds the generator options of that copybook.
val copybookDir = layout.projectDirectory.dir("src/main/copybook").asFile
copybookDir.listFiles { file -> file.name.endsWith(".cpy") }.orEmpty().sortedBy { it.name }
    .forEach { copybook ->
        val name = copybook.nameWithoutExtension
        val config = File(copybookDir, "$name.json")
        val outputDir = layout.buildDirectory.dir("generated/sources/copybook/$name")
        val task = tasks.register<JavaExec>(
            "generate" + name.split('-').joinToString("") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }) {
            description = "Generates the BookMap classes of $name.cpy."
            classpath = copybookGenerator
            mainClass = "pt.dazz.bookmap.copybook.cli.Main"
            inputs.file(copybook)
            if (config.exists()) {
                inputs.file(config)
            }
            outputs.dir(outputDir)
            doFirst { delete(outputDir) }
            args = listOf("generate", copybook.path,
                "--package", "pt.dazz.bookmap.examples.copybook",
                "--output-dir", outputDir.get().asFile.path) +
                (if (config.exists()) listOf("--config", config.path) else listOf())
        }
        sourceSets.main { java.srcDir(task) }
    }
