# BookMap copybook Gradle plugin: design

Date: 2026-11-12
Status: implemented in v0.5.0.

Related documents: `docs/design/2026-11-11-bookmap-copybook-design.md` (the generator), `CLAUDE.md` (project rules).

## 1. Intent

Version 0.4.0 added the copybook generator: the `bookmap-copybook` library and the `bookmap-copybook-cli` program. Today a build runs the generator through one `JavaExec` task for each copybook (see `bookmap-examples/build.gradle.kts`). That approach needs about 30 lines of build script in each project.

This design adds a Gradle plugin. A project applies the plugin, points it at a directory tree of copybooks, and gets the BookMap classes in a generated source set before `compileJava`. The plugin is a thin adapter over the public API of `bookmap-copybook`. It does not parse, map, or generate code itself.

The author expects real projects to keep all copybooks in one directory tree. The directory structure gives the Java subpackages.

## 2. Goals, non-goals, and anti-goals

Goals:

- Generate the classes of all copybooks in a directory tree, in one task, before `compileJava`.
- Derive the Java package of each copybook from a base package and the relative directory of the copybook.
- Keep the per-copybook options file `<NAME>.json` next to `<NAME>.cpy`, with the format that `GeneratorConfigJson` reads.
- Support correct up-to-date checks, the build cache, and the configuration cache.
- Publish the plugin to Maven Central only, with the release pipeline that exists.
- Document the plugin in its own module README, with a clear section on the location of `COPY` members.

Non-goals:

- Publication to the Gradle Plugin Portal.
- A Maven plugin. A later design covers it.
- Global parse or mapping options in the extension (for example, a default `format` or `stripPrefixes` for all copybooks). `GeneratorConfigJson.read` returns resolved values, so the plugin cannot merge a global value with a per-copybook file without new API in the core. Put these options in each `<NAME>.json` file. A later change can add a merge API.
- Generation from a model JSON file (`*.model.json`). The CLI supports it. The plugin can add it later.
- Automatic discovery of `COPY` members anywhere in the tree.

Anti-goals (a failure even if the code works):

- The plugin contains parser, mapper, or Java generation logic. It must call `Copybooks`, `GeneratorConfigJson`, and `JavaClassGenerator.writeTo` only.
- A build that silently skips a copybook, or that compiles stale generated classes after a copybook change.
- The plugin adds `bookmap-core` or another dependency to the user's `implementation` configuration without the user's knowledge.

## 3. Decisions of the author

These decisions come from the author. Do not change them without the author.

1. **Directory tree and subpackages.** The plugin scans a source directory recursively for copybooks. The relative directory of each copybook gives a subpackage under the base package.
2. **`COPY` members have a declared location.** The user declares the copy directories, in search order. The plugin does not search the tree for members. This matches the parser: `ParseOptions.copyDirs` is an ordered list, and `CopyExpander` searches each directory (not recursively) for the member name with no extension, `.cpy`, `.cbl`, or `.cob`. An optional `exclude` pattern lets a user keep members inside the source tree.
3. **Document the `COPY` member rule in the module README.** This is very important to the author. The README must explain the search, the order, the extensions, the failure message, and a warning about members inside the source tree.
4. **Publication: Maven Central only.** No Gradle Plugin Portal.
5. **Examples: keep both forms.** `bookmap-examples` stays as it is (the `JavaExec` and CLI form). A new standalone build, `bookmap-examples-gradle-plugin/`, shows the plugin.
6. **Class name collisions: the `packagePerCopybook` option.** The mapper makes class names unique only in one copybook. Two copybooks in one directory can generate the same class (for example, both `COPY ADDRESS`, or both have a group `CUSTOMER`). The option `packagePerCopybook = true` adds one package segment from the copybook name and removes the collision. With the option off (the default), the build fails with a clear message. Document this well in the module README.

## 4. Module and publication

- New module: `bookmap-copybook-gradle-plugin`. Add it to `settings.gradle.kts`.
- Plugin id: `pt.dazz.bookmap.copybook`.
- Implementation class: `pt.dazz.bookmap.copybook.gradle.BookmapCopybookPlugin`. Package `pt.dazz.bookmap.copybook.gradle`.
- The module applies `java-gradle-plugin` and declares the plugin in `gradlePlugin { plugins { ... } }`.
- Dependencies: `implementation(project(":bookmap-copybook"))` and the Gradle API. No other runtime dependency.
- Publication: the root build publishes the module with `com.vanniktech.maven.publish`. For this module, configure `GradlePlugin(javadocJar = JavadocJar.Javadoc(), sourcesJar = true)` instead of `JavaLibrary(...)`. The publication must contain the plugin JAR and the plugin marker artifact `pt.dazz.bookmap.copybook:pt.dazz.bookmap.copybook.gradle.plugin`. The marker artifact must have the same signing and POM data as the other modules.
- Add the module to `publishedModules` in the root `build.gradle.kts` with the description "BookMap copybook generator Gradle plugin."
- Add the module to the list of published JARs in `.github/workflows/release.yml` (job `publish`, step "Collect the published JARs").
- The plugin version is the BookMap version. The plugin brings `bookmap-copybook` of the same version.
- The plugin shares the buildscript classpath of the user's build. It does not isolate or shade its dependencies (`bookmap-core`, JavaPoet, Jackson). The README lists them. (A call of the designer: this keeps the adapter thin.)
- The plugin needs a Gradle daemon on JDK 21 or later, because the module compiles for Java 21. The tests use only the Gradle version of the wrapper (9.9.1). The README gives both facts. (A call of the designer.)

Users add Maven Central to the plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

## 5. The DSL

```kotlin
plugins {
    java
    id("pt.dazz.bookmap.copybook") version "X.Y.Z"
}

dependencies {
    implementation("pt.dazz.bookmap:bookmap-core:X.Y.Z")
    annotationProcessor("pt.dazz.bookmap:bookmap-processor:X.Y.Z")
}

bookmap {
    copybooks {
        sourceDir = layout.projectDirectory.dir("src/main/copybook") // the default
        copyDirs.from("src/main/copylib")                            // COPY members, in search order
        packageName = "com.example.mainframe"                        // required
        lombok = false                                               // the default
        packagePerCopybook = false                                   // the default
        exclude("**/members/**")                                     // optional
    }
}
```

The extension `bookmap` has one nested block, `copybooks`. The nesting leaves space for other BookMap build options later. (This is a call of the designer, shown to the author in the brainstorm.)

Properties of `copybooks`:

| Property | Type | Default | Meaning |
|---|---|---|---|
| `sourceDir` | `DirectoryProperty` | `src/main/copybook` | The root of the copybook tree. The plugin scans it recursively. |
| `copyDirs` | `ConfigurableFileCollection` | empty | The directories with `COPY` members, in search order. |
| `packageName` | `Property<String>` | none (required) | The base package. |
| `lombok` | `Property<Boolean>` | `false` | Generate Lombok classes. |
| `packagePerCopybook` | `Property<Boolean>` | `false` | Add one package segment from the copybook name (section 6). |
| `outputDir` | `DirectoryProperty` | `build/generated/sources/copybook/main` | The directory for the generated Java files. |
| `include(...)`, `exclude(...)` | patterns (`PatternFilterable`) | include `**/*.cpy` | Select the copybook files in `sourceDir`. |

Rules:

- The default include pattern is `**/*.cpy`. If the user calls `include(...)`, the user's patterns replace the default.
- If `packageName` is not set and at least one copybook exists, the task fails with: "Set bookmap.copybooks.packageName." If no copybook exists, the task does nothing and does not need `packageName`.
- If `sourceDir` does not exist, the task does nothing.

## 6. Package derivation

For a copybook at `<sourceDir>/<relative dir>/<NAME>.cpy`:

1. If `<NAME>.json` exists next to the copybook and sets `package`, use that package exactly. The file wins.
2. Otherwise, use `packageName` plus one segment for each directory in `<relative dir>`. A copybook directly in `sourceDir` gets `packageName` with no segment.
3. If `packagePerCopybook` is `true`, add one more segment from `<NAME>` (the file name without the extension). This rule does not apply when rule 1 applies.

Convert each directory name, and the copybook name in rule 3, to a Java package segment:

1. Change the name to lower case (`Locale.ROOT`).
2. Replace each character that is not a letter, a digit, or `_` with `_`. For example, `-`, `.`, and space become `_`.
3. If the result starts with a digit, add `_` before it.
4. If the result is a Java keyword or a reserved literal (`true`, `false`, `null`), add `_` after it.

Examples, with `packageName = "com.example.mainframe"`:

| Copybook | Package |
|---|---|
| `ORDER.cpy` | `com.example.mainframe` |
| `orders/ORDER.cpy` | `com.example.mainframe.orders` |
| `Billing/Open-Invoices/INVOICE.cpy` | `com.example.mainframe.billing.open_invoices` |
| `2026/INVOICE.cpy` | `com.example.mainframe._2026` |
| `new/INVOICE.cpy` | `com.example.mainframe.new_` |
| `billing/LIST-OPEN-INVOICES.cpy`, with `packagePerCopybook = true` | `com.example.mainframe.billing.list_open_invoices` |

## 7. Per-copybook options

- For `<NAME>.cpy`, the options file is `<NAME>.json` in the same directory. The file is optional.
- Read it with `GeneratorConfigJson.read(text, directoryOfTheJsonFile)`. Relative `copyDirs` in the file resolve against the directory of the file, as in the CLI.
- If the file is not valid, fail the task. Give the file path and the `ModelJsonException` message.
- `lombok` in the file wins over the extension value. If the file does not set it, use the extension value.
- Copy directories: first the `copyDirs` of the file, then the `copyDirs` of the extension. The more specific list comes first.
- All other parse and mapping options come only from the file. With no file, use `ParseOptions.defaults()` and `MappingOptions.defaults()`.

## 8. The task

- Name: `generateCopybookClasses`. Group: `build`. Description: "Generates the BookMap classes from the COBOL copybooks."
- Type: `GenerateCopybookClasses`, an abstract class with Gradle managed properties. Annotate it with `@CacheableTask`.
- Inputs:
  - The copybook files: the file tree of `sourceDir` with the include and exclude patterns. Use `@InputFiles` and `@PathSensitive(RELATIVE)`. Do not use `@SkipWhenEmpty`: an empty tree must still clean the output.
  - The options files: all `*.json` files in `sourceDir` (`@InputFiles`, `@PathSensitive(RELATIVE)`).
  - The copy directories: `copyDirs` (`@InputFiles`, `@PathSensitive(RELATIVE)`). A change in a `COPY` member makes the task run again.
  - The copy directories that the options files declare (key `copyDirs`, section 7). These directories can be outside `sourceDir`. Declare them as an `@InputFiles` property that the task computes from the options files when Gradle takes the fingerprint of the inputs, not at configuration time. A change in a member there must also make the task run again. (A call of the designer.)
  - `packageName` (`@Input`, `@Optional`), `lombok` (`@Input`), and `packagePerCopybook` (`@Input`).
- Output: `outputDir` (`@OutputDirectory`).
- Action:
  1. Delete the contents of `outputDir`.
  2. For each selected copybook, in sorted order of the relative path: read the options file, derive the package, and call `Copybooks.generate(path, parse, mapping, new GenerateOptions(package, lombok))`.
  3. Log each diagnostic through the Gradle logger, with `Diagnostic.format()`. Warnings go to `warn`. Errors go to `error`.
  4. Collect the result of all copybooks. Do not stop at the first copybook with an error. This shows all errors in one build.
  5. If any result has an error, fail the task with a `GradleException`. The message gives the number of copybooks with errors.
  6. If two copybooks generate the same fully qualified class name, fail the task. Give the class name and the two copybook paths. Tell the user to set `packagePerCopybook = true`, to set `package` in an options file, or to put the copybooks in different directories. Report all collisions, not only the first.
  7. Otherwise, call `JavaClassGenerator.writeTo(files, outputDir)` for each result.
- The task must be compatible with the configuration cache. Do not use `Project` at execution time.

## 9. Wiring

- The plugin reacts to the `java` plugin (`plugins.withType(JavaPlugin)`). Without the `java` plugin, the plugin registers the extension and the task, but adds no source directory.
- With the `java` plugin, add the task output to `sourceSets.main.java` with `srcDir(taskProvider)`. Gradle then runs the task before `compileJava` and before the `sourcesJar` task.
- The plugin does not add dependencies to the user's configurations. The README tells the user to add `bookmap-core` and `bookmap-processor`.

## 10. The example build

Add `bookmap-examples-gradle-plugin/` at the repository root. It is a standalone Gradle build, not a subproject of the root build.

- `settings.gradle.kts` includes the root build twice: `pluginManagement { includeBuild("..") }` for the plugin, and a top-level `includeBuild("..")` for `bookmap-core`, `bookmap-processor`, and `bookmap-transport-simulator`. Gradle substitutes the `pt.dazz.bookmap:*` dependencies with the projects of the root build.
- The build uses the Gradle wrapper of the root build: `./gradlew -p bookmap-examples-gradle-plugin build`.
- Layout:

  ```
  src/main/copybook/orders/ORDER.cpy        -> <base>.orders
  src/main/copybook/billing/INVOICE.cpy     -> <base>.billing
  src/main/copybook/billing/INVOICE.json    (options of INVOICE.cpy)
  src/main/copylib/ADDRESS.cpy              (a COPY member; no classes)
  ```

- The base package is `pt.dazz.bookmap.examples.plugin`.
- At least one copybook uses `COPY ADDRESS`.
- A test in the example encodes and decodes a record through `bookmap-transport-simulator`.
- Add a step to the `build` job in `.github/workflows/release.yml`, after "Build and test": `./gradlew -p bookmap-examples-gradle-plugin build --no-daemon`.
- Risk: the double `includeBuild("..")` is expected to work on Gradle 9.9.1. Validate it in the first implementation task. If it does not work, the fallback is: the CI step runs `./gradlew publishToMavenLocal` first, and the example build uses `mavenLocal()` with the snapshot version. Record the result in the plan.
- Do not apply the Jacoco rule to the example build.

## 11. Tests and coverage

- The module has 100% line and branch coverage, as the root build requires for each subproject except `bookmap-examples`.
- Functional tests use Gradle TestKit (`GradleRunner`) with `withPluginClasspath()`. Cover at least:
  - Generation into the source set, and a successful `compileJava` of a project with `bookmap-core` on the classpath.
  - Subpackages from directories, with the conversion rules of section 6.
  - `package` and `lombok` in a `<NAME>.json` file win over the extension.
  - A `COPY` member in `copyDirs`, and a failure with a clear message when the member is missing.
  - `exclude` keeps a member in the tree out of the generation.
  - A copybook error fails the build and shows the diagnostic. All copybooks with errors appear.
  - A missing `packageName` fails the build. An empty or missing `sourceDir` does not fail.
  - Up-to-date: a second build is `UP-TO-DATE`. A change in a copybook, a JSON file, or a `COPY` member runs the task again. A removed copybook removes its classes.
  - The build cache: a clean build with the cache gives `FROM-CACHE`.
  - The configuration cache: a build with `--configuration-cache` passes, and the second run reuses the cache.
  - A project without the `java` plugin applies the plugin with no failure.
  - Two copybooks in one directory with the same class (for example, both `COPY ADDRESS`): the build fails with the collision message. With `packagePerCopybook = true`, the same project builds.
- Coverage of code that runs in the TestKit JVM: Jacoco does not see it by default. The builder decides how to reach 100%. Two options: unit tests with `ProjectBuilder` and direct calls of the task action, or a Jacoco agent passed to the TestKit runner through a `gradle.properties` file in the test project.
- Unit tests cover the package derivation of section 6 directly.
- Test data uses an international business domain: orders, invoices, addresses, bank accounts.

## 12. Documentation

- New file `bookmap-copybook-gradle-plugin/README.md`. Sections:
  1. What the plugin does.
  2. Setup: `pluginManagement` with `mavenCentral()`, the `plugins` block, and the dependencies.
  3. The DSL, with the table of section 5.
  4. Packages from directories, with the rules and examples of section 6.
  5. Options files (`<NAME>.json`), with the precedence rules of section 7.
  6. **Class name collisions and `packagePerCopybook`.** Explain: each COBOL group becomes a Java class; names are unique only in one copybook; two copybooks in one package can generate the same class (show the `COPY ADDRESS` case); the build then fails, and the message names the class and the copybooks. Give the three solutions: `packagePerCopybook = true` (with a before and after example of the packages), `package` in an options file, or different directories. Add this caution: "Do not change `packagePerCopybook` in a project that is in use without a plan. The option changes the package of each generated class, and all imports of these classes must change."
  7. **`COPY` members: where to put them.** Explain: the plugin does not search the tree; declare the directories in `copyDirs`; the search order (the options file first, then the extension); the search is not recursive; the accepted extensions (none, `.cpy`, `.cbl`, `.cob`); the build fails if a member is missing, and the message lists the searched directories. Add this warning: "Do not put `COPY` members in `sourceDir` without an `exclude` pattern. The plugin tries to generate classes from each member, and the build can fail."
  8. Up-to-date checks, the build cache, and the configuration cache.
  9. Requirements: JDK 21 or later for the Gradle daemon; the tested Gradle version; the dependencies that the plugin brings to the buildscript classpath.
  10. A link to `bookmap-examples-gradle-plugin/`.
- In the root `README.md`, add a short "Gradle plugin" subsection to the "Copybook generator" section, with the minimum setup and a link to the module README.
- Write all documentation in English, in ASD-STE100 style (see `CLAUDE.md`).

## 13. Left to the builder

- The internal structure of the plugin classes, beyond the names of section 4 and the task name.
- The approach to coverage of TestKit code (section 11).
- The exact log format, if it includes `Diagnostic.format()`.
- The Gradle API to read the JSON files and the copybook tree, if the inputs keep relative path sensitivity.
