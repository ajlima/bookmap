# Copybook Gradle Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Gradle plugin `pt.dazz.bookmap.copybook`. The plugin generates BookMap classes from a directory tree of COBOL copybooks into a generated source set before `compileJava`.

**Architecture:** A new module, `bookmap-copybook-gradle-plugin`, holds three layers. `PackageNames` and `CopybookGeneration` are plain Java with no Gradle API, so plain unit tests cover all their branches. `GenerateCopybookClasses` (a cacheable task), `BookmapExtension`, and `BookmapCopybookPlugin` are thin Gradle wiring: `ProjectBuilder` tests cover them for coverage, and TestKit tests prove the real build behavior. A standalone build, `bookmap-examples-gradle-plugin/`, uses the plugin through `includeBuild("..")`.

**Tech Stack:** Java 21, Gradle 9.9.1 (`java-gradle-plugin`, TestKit, `ProjectBuilder`), `bookmap-copybook`, JUnit 6, AssertJ, JaCoCo (100% line and branch), `com.vanniktech.maven.publish` 0.37.0.

**Spec:** `docs/superpowers/specs/2026-11-12-bookmap-copybook-gradle-plugin-design.md`

## Global Constraints

- Java 21. No runtime reflection in `main` code. The plugin must not parse, map, or generate code itself: it calls only `Copybooks`, `GeneratorConfigJson`, and `JavaClassGenerator.writeTo` (plus the option records `ParseOptions`, `MappingOptions`, `GenerateOptions`).
- Do not change `bookmap-copybook` or any other existing module's `main` code.
- Plugin id `pt.dazz.bookmap.copybook`. Package `pt.dazz.bookmap.copybook.gradle`. Task name `generateCopybookClasses`, group `build`, description "Generates the BookMap classes from the COBOL copybooks."
- Extension `bookmap { copybooks { ... } }`. Defaults: `sourceDir` = `src/main/copybook`, `lombok` = `false`, `packagePerCopybook` = `false`, `outputDir` = `build/generated/sources/copybook/main`, include = `**/*.cpy`.
- The plugin adds no dependency to the user's configurations.
- Compatible with the configuration cache: no `Project` access at execution time.
- 100% line and branch coverage in the new module (`./gradlew build` runs the JaCoCo verification).
- Maven Central only. No Gradle Plugin Portal.
- English for code and docs. ASD-STE100 for docs. International business data (orders, invoices, addresses, bank accounts).
- Commit email `alexandre.lima@dazz.pt`. No Claude signature and no `Co-Authored-By` trailer.
- Branch `feat/bookmap-copybook-gradle-plugin`, from `main`. One commit per task at minimum.

## Review Focus

1. A `COPY` member in a directory that only an options file declares (outside `sourceDir`): a change in that member must make the task run again. Test in Task 4 (`memberChangeInJsonCopyDirRunsTaskAgain`).
2. An options file with invalid JSON: Gradle must report the file path and the JSON message, not a stack trace from the input fingerprint. Test in Task 3 (`invalidJsonGivesTheFilePath`) and Task 4 (`invalidJsonFailsWithTheFilePath`).
3. A copybook removed from the tree: its classes must disappear from the output, also when the task comes from the build cache. Test in Task 4 (`removedCopybookRemovesItsClasses`).
4. A copybook name or directory name that is not a valid Java identifier (`2026`, `new`, `Open-Invoices`, `ORDER.V2`): a valid package, never a compile error. Test in Task 1.
5. Two collisions in one build: both appear in one failure message. Test in Task 2 (`reportsAllCollisions`).

---

## File Structure

```
bookmap-copybook-gradle-plugin/
  build.gradle.kts
  README.md                                               (Task 6)
  src/main/java/pt/dazz/bookmap/copybook/gradle/
    PackageNames.java              package derivation (spec section 6)
    CopybookGeneration.java        runs the pipeline for a list of copybooks (spec sections 7, 8)
    CopybookGenerationException.java
    BookmapExtension.java          the `bookmap` extension, with the nested `copybooks` block
    CopybooksSpec.java             the `copybooks` block (spec section 5)
    GenerateCopybookClasses.java   the cacheable task
    BookmapCopybookPlugin.java     the plugin: extension, task, source set wiring
  src/test/java/pt/dazz/bookmap/copybook/gradle/
    PackageNamesTest.java
    CopybookGenerationTest.java
    BookmapCopybookPluginTest.java          ProjectBuilder tests (coverage of the wiring)
    BookmapCopybookPluginFunctionalTest.java  TestKit tests
  src/test/resources/copybooks/...          test copybooks
bookmap-examples-gradle-plugin/              standalone build (Task 5)
```

---

### Task 1: Module and package derivation

**Files:**
- Modify: `settings.gradle.kts` (add `include("bookmap-copybook-gradle-plugin")`)
- Create: `bookmap-copybook-gradle-plugin/build.gradle.kts`
- Create: `bookmap-copybook-gradle-plugin/src/main/java/pt/dazz/bookmap/copybook/gradle/PackageNames.java`
- Test: `bookmap-copybook-gradle-plugin/src/test/java/pt/dazz/bookmap/copybook/gradle/PackageNamesTest.java`

**Interfaces:**
- Produces:
  - `static String PackageNames.segment(String name)`: one Java package segment (spec section 6, conversion rules 1 to 4).
  - `static String PackageNames.packageOf(String basePackage, Path relativeDir, String copybookName, boolean perCopybook)`: `relativeDir` is the directory of the copybook relative to `sourceDir` (an empty path for the root). It does not apply rule 1 (the options file): `CopybookGeneration` does that.

- [ ] **Step 1: Create the module build file**

`build.gradle.kts`: `plugins { `java-gradle-plugin` }`. Dependencies: `implementation(project(":bookmap-copybook"))`. Declare `gradlePlugin { plugins { create("bookmapCopybook") { id = "pt.dazz.bookmap.copybook"; implementationClass = "pt.dazz.bookmap.copybook.gradle.BookmapCopybookPlugin"; displayName = "BookMap copybook generator"; description = "Generates BookMap classes from COBOL copybooks." } } }`. Keep `gradleTestKit()` (the default of `java-gradle-plugin`). The root build applies `java-library`, JaCoCo, and the toolchain already.

- [ ] **Step 2: Write the failing tests**

`PackageNamesTest`, with base `com.example.mainframe`:
- `segmentChangesToLowerCase`: `segment("Billing")` is `billing`.
- `segmentReplacesOtherCharacters`: `segment("Open-Invoices")` is `open_invoices`; `segment("ORDER.V2")` is `order_v2`; `segment("bank account")` is `bank_account`; `segment("A_B")` is `a_b`.
- `segmentPrefixesALeadingDigit`: `segment("2026")` is `_2026`.
- `segmentSuffixesAKeyword`: `segment("new")` is `new_`; `segment("TRUE")` is `true_`; `segment("null")` is `null_`; `segment("class")` is `class_`.
- `rootCopybookGetsTheBasePackage`: `packageOf(base, Path.of(""), "ORDER", false)` is `com.example.mainframe`.
- `directoriesGiveSubpackages`: `packageOf(base, Path.of("Billing", "Open-Invoices"), "INVOICE", false)` is `com.example.mainframe.billing.open_invoices`.
- `perCopybookAddsTheCopybookSegment`: `packageOf(base, Path.of("billing"), "LIST-OPEN-INVOICES", true)` is `com.example.mainframe.billing.list_open_invoices`; `packageOf(base, Path.of(""), "ORDER", true)` is `com.example.mainframe.order`.

- [ ] **Step 3: Run the tests to make sure they fail**

Run: `./gradlew :bookmap-copybook-gradle-plugin:test`
Expected: FAIL (compile errors: `PackageNames` does not exist).

- [ ] **Step 4: Implement `PackageNames`**

A final class with a private constructor. Use `javax.lang.model.SourceVersion.isKeyword(String)` for the keyword check (it includes `true`, `false`, `null`). Rule 2 keeps ASCII letters and digits, `_`, and replaces each other character with `_`.

- [ ] **Step 5: Run the tests to make sure they pass**

Run: `./gradlew :bookmap-copybook-gradle-plugin:test`
Expected: PASS. (`build` still fails the coverage rule: the plugin class does not exist yet. Do not run `build` in this task.)

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts bookmap-copybook-gradle-plugin
git commit -m "feat: derive Java packages from the copybook directories"
```

---

### Task 2: The generation logic

**Files:**
- Create: `.../gradle/CopybookGeneration.java`, `.../gradle/CopybookGenerationException.java`
- Test: `.../gradle/CopybookGenerationTest.java`, `src/test/resources/copybooks/...`

**Interfaces:**
- Consumes: `PackageNames.packageOf` (Task 1).
- Produces:
  - `record CopybookGeneration.Settings(Path sourceDir, List<Path> copyDirs, String basePackage, boolean lombok, boolean packagePerCopybook)`. `basePackage` can be `null`.
  - `interface CopybookGeneration.Log { void warn(String message); void error(String message); }`
  - `static void CopybookGeneration.run(Settings settings, List<Path> copybooks, Path outputDir, Log log) throws IOException`. `copybooks` are absolute paths below `sourceDir`. Throws `CopybookGenerationException` (extends `RuntimeException`) on each failure in the list below.
  - `static List<Path> CopybookGeneration.configCopyDirs(Path sourceDir, List<Path> copybooks)`: the copy directories that the options files of these copybooks declare, resolved as `GeneratorConfigJson` resolves them. It ignores an options file that is not valid (the task fingerprint must not fail; `run` reports the error).
  - `static Path CopybookGeneration.optionsFile(Path copybook)`: `<NAME>.json` next to `<NAME>.cpy` (the file name without its last extension, plus `.json`).

Behavior of `run`, in this order:
1. Delete all files and directories in `outputDir` (create `outputDir` if it does not exist).
2. If `copybooks` is empty, return.
3. Sort the copybooks by the relative path (string form, `/` separators).
4. For each copybook: read the options file if it exists (`GeneratorConfigJson.read(text, optionsFile.getParent())`). An invalid file is an error: `"<optionsFile>: <ModelJsonException message>"`, through `log.error`. Then go to the next copybook.
5. Package: the `package` of the options file if set. Else, if `basePackage` is `null`, throw `CopybookGenerationException("Set bookmap.copybooks.packageName.")` before any generation. Else `PackageNames.packageOf(basePackage, relativeDir, name, packagePerCopybook)`.
6. Lombok: the `lombok` of the options file if not `null`, else `settings.lombok()`.
7. Parse options: the options file's `ParseOptions` (or `ParseOptions.defaults()`), with `copyDirs` = file `copyDirs` + `settings.copyDirs()`. Mapping options: the file's, or `MappingOptions.defaults()`.
8. Call `Copybooks.generate(copybook, parse, mapping, new GenerateOptions(package, lombok))`. Log each diagnostic with `Diagnostic.format()`: `ERROR` to `log.error`, other severities to `log.warn`.
9. After all copybooks: if at least one copybook had an error (invalid options file or `result.hasErrors()`), throw `CopybookGenerationException("<n> copybook(s) have errors. See the messages above.")`. Write nothing.
10. Collisions: map each `packageName() + "." + typeSpec().name()` of each `JavaFile` to its copybook. If a name has two copybooks, collect a line `"class <fqn> comes from <relA> and <relB>"`. If one or more lines exist, throw one `CopybookGenerationException` with all lines, then this text: "Set bookmap.copybooks.packagePerCopybook = true, set \"package\" in an options file, or put the copybooks in different directories."
11. Call `JavaClassGenerator.writeTo(result.files(), outputDir)` for each result.

- [ ] **Step 1: Write the test copybooks**

In `src/test/resources/copybooks/`, fixed format, international data:
- `tree/orders/ORDER.cpy` (an order with an `ORDER-ID` and an `AMOUNT PIC S9(7)V99 COMP-3`).
- `tree/billing/INVOICE.cpy` (an invoice with `COPY ADDRESS.`), and `tree/billing/INVOICE.json` with `{"package": "com.example.invoicing", "lombok": true}`.
- `copylib/ADDRESS.cpy` (a `05 ADDRESS` group with `STREET`, `CITY`, `COUNTRY-CODE`).
- `collision/CUSTOMER-ORDER.cpy` and `collision/CUSTOMER-RETURN.cpy`: both have `COPY ADDRESS.` (each gives a class `Address`).
- `broken/BAD.cpy` (a `PIC` clause the parser rejects) and `broken/WORSE.cpy` (another error).
- `badjson/SHIPMENT.cpy` and `badjson/SHIPMENT.json` with the text `{"package": `.
- `jsoncopy/ACCOUNT.cpy` with `COPY BANK-ADDRESS.`, and `jsoncopy/ACCOUNT.json` with `{"copyDirs": ["../jsoncopylib"]}`; `jsoncopylib/BANK-ADDRESS.cpy`.

Tests copy the resources to a JUnit `@TempDir` before use.

- [ ] **Step 2: Write the failing tests**

`CopybookGenerationTest` (a recording `Log` collects the messages):
- `generatesIntoDerivedPackages`: tree + copylib, base `com.example.mainframe`. The output has `com/example/mainframe/orders/Order.java` and `com/example/invoicing/Invoice.java` (the options file wins).
- `optionsFileLombokWins`: `Invoice.java` contains `lombok`; `Order.java` does not.
- `extensionLombokApplies`: with `lombok = true`, `Order.java` contains `lombok`.
- `perCopybookAddsASegment`: `packagePerCopybook = true`: `com/example/mainframe/orders/order/Order.java` exists; `Invoice` stays in `com.example.invoicing`.
- `missingPackageNameFails`: `basePackage = null` and the `ORDER` copybook only: `CopybookGenerationException` with "Set bookmap.copybooks.packageName.".
- `missingPackageNameIsNoErrorWhenTheFileSetsIt`: `basePackage = null` and `INVOICE` only: passes.
- `noCopybookCleansTheOutput`: a file in `outputDir` before; an empty list; the file is gone.
- `missingCopyMemberFails`: `INVOICE` with no copy directory: the exception says "1 copybook(s) have errors", and the log has an error that contains "copy member 'ADDRESS' not found".
- `reportsErrorsOfAllCopybooks`: `broken/` (two copybooks): the exception says "2 copybook(s) have errors", and the log has an error line for each file; `outputDir` is empty.
- `warningsGoToWarn`: a copybook that gives a warning diagnostic (for example, a level-66 item or another case that `bookmap-copybook` tests report as `WARNING`): one `warn` line, no exception.
- `invalidJsonGivesTheFilePath`: `badjson/`: an `error` line starts with the path of `SHIPMENT.json`; the exception says "1 copybook(s) have errors".
- `collisionFails`: `collision/`, base `com.example.mainframe`: the message contains "class com.example.mainframe.Address comes from CUSTOMER-ORDER.cpy and CUSTOMER-RETURN.cpy" and "packagePerCopybook = true".
- `reportsAllCollisions`: two collisions in one run (add a second shared group, for example `COPY CONTACT.` in both): the message has two "comes from" lines.
- `perCopybookRemovesTheCollision`: `collision/` with `packagePerCopybook = true`: passes; two `Address.java` files exist.
- `jsonCopyDirsAreSearchedFirst`: `jsoncopy/` passes with no extension copy directory.
- `configCopyDirsReadsTheOptionsFiles`: `configCopyDirs` gives the absolute path of `jsoncopylib` for `ACCOUNT`, an empty list for `ORDER`, and an empty list for `SHIPMENT` (invalid JSON).
- `optionsFileName`: `optionsFile(Path.of("a/ORDER.cpy"))` is `a/ORDER.json`; `optionsFile(Path.of("ORDER"))` is `ORDER.json`.

- [ ] **Step 3: Run the tests to make sure they fail**

Run: `./gradlew :bookmap-copybook-gradle-plugin:test --tests '*CopybookGenerationTest'`
Expected: FAIL (compile errors).

- [ ] **Step 4: Implement `CopybookGeneration` and `CopybookGenerationException`**

Follow the behavior list above. Use the same copy-directory merge as the CLI (`bookmap-copybook-cli/.../Main.java`, `parseOptions`), but with the file list first.

- [ ] **Step 5: Run the tests to make sure they pass**

Run: `./gradlew :bookmap-copybook-gradle-plugin:test --tests '*CopybookGenerationTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add bookmap-copybook-gradle-plugin
git commit -m "feat: run the copybook generator for a tree of copybooks"
```

---

### Task 3: The extension, the task, and the plugin

**Files:**
- Create: `.../gradle/BookmapExtension.java`, `.../gradle/CopybooksSpec.java`, `.../gradle/GenerateCopybookClasses.java`, `.../gradle/BookmapCopybookPlugin.java`
- Test: `.../gradle/BookmapCopybookPluginTest.java`

**Interfaces:**
- Consumes: `CopybookGeneration.run`, `CopybookGeneration.configCopyDirs`, `CopybookGeneration.Settings`, `CopybookGeneration.Log` (Task 2).
- Produces:
  - `abstract class BookmapExtension`: `@Nested abstract CopybooksSpec getCopybooks()`, `void copybooks(Action<? super CopybooksSpec> action)`. Registered as `bookmap`.
  - `abstract class CopybooksSpec`: `DirectoryProperty getSourceDir()`, `ConfigurableFileCollection getCopyDirs()`, `Property<String> getPackageName()`, `Property<Boolean> getLombok()`, `Property<Boolean> getPackagePerCopybook()`, `DirectoryProperty getOutputDir()`, `ListProperty<String> getIncludes()`, `ListProperty<String> getExcludes()`, `CopybooksSpec include(String... patterns)`, `CopybooksSpec exclude(String... patterns)`.
  - `abstract class GenerateCopybookClasses extends DefaultTask`, `@CacheableTask`, with: `@Internal DirectoryProperty getSourceDir()`; `@Input ListProperty<String> getIncludes()`; `@Input ListProperty<String> getExcludes()`; `@InputFiles @PathSensitive(RELATIVE) FileCollection getCopybookFiles()`; `@InputFiles @PathSensitive(RELATIVE) FileCollection getOptionsFiles()`; `@InputFiles @PathSensitive(RELATIVE) ConfigurableFileCollection getCopyDirs()`; `@InputFiles @PathSensitive(RELATIVE) FileCollection getConfigCopyDirs()`; `@Input @Optional Property<String> getPackageName()`; `@Input Property<Boolean> getLombok()`; `@Input Property<Boolean> getPackagePerCopybook()`; `@OutputDirectory DirectoryProperty getOutputDir()`; `@TaskAction void generate()`.
  - `BookmapCopybookPlugin implements Plugin<Project>`.

Details:
- The `includes` list has no convention. The task uses `**/*.cpy` when the list is empty. So a call of `include(...)` replaces the default.
- `getCopybookFiles()`: the file tree of `sourceDir`, filtered with the includes (or `**/*.cpy`) and the excludes. A missing `sourceDir` gives an empty tree.
- `getOptionsFiles()`: the file tree of `sourceDir`, filtered with `**/*.json`.
- `getConfigCopyDirs()`: a file collection from a provider that calls `CopybookGeneration.configCopyDirs(sourceDir, copybook files)`. Gradle evaluates it when it takes the fingerprint of the inputs, not at configuration time. Use an injected `ObjectFactory` (`@Inject abstract ObjectFactory getObjects()`).
- `generate()`: build the `Settings` and the sorted file list, and call `CopybookGeneration.run` with a `Log` that writes to `getLogger().warn` and `getLogger().error`. Wrap a `CopybookGenerationException` in a `GradleException` with the same message. Wrap an `IOException` in a `GradleException` ("Cannot write the generated classes: " + message).
- The plugin: create the extension; set the conventions (spec section 5); register the task and map each task property from the spec (`convention`/`set` with providers, `from` for `copyDirs`). Then `project.getPlugins().withType(JavaPlugin.class, ...)`: add the task provider to `sourceSets.main.java.srcDir(task)` (use `task.flatMap(GenerateCopybookClasses::getOutputDir)` or the task provider itself, so `compileJava` depends on the task).

- [ ] **Step 1: Write the failing tests**

`BookmapCopybookPluginTest`, with `ProjectBuilder.builder().withProjectDir(tempDir).build()`:
- `registersTheExtensionAndTheTask`: after `apply(plugin = "pt.dazz.bookmap.copybook")` (by id), the extension `bookmap` and the task `generateCopybookClasses` exist; group `build`; the description is the exact text of the Global Constraints.
- `setsTheConventions`: `sourceDir` is `<project>/src/main/copybook`; `outputDir` is `<project>/build/generated/sources/copybook/main`; `lombok` and `packagePerCopybook` are `false`; `includes` is empty.
- `withoutJavaAddsNoSourceDir`: no `java` plugin: no source set exists, no failure.
- `withJavaAddsTheOutputToMain`: with the `java` plugin: `sourceSets.main.java.srcDirs` contains the output directory.
- `includeReplacesTheDefault` and `excludeFiltersFiles`: copy files into `src/main/copybook`; `getCopybookFiles()` gives the expected set for: the default; `include("**/*.cob")`; `exclude("members/**")`.
- `taskActionGenerates`: copy the `tree` and `copylib` resources; set `packageName` and `copyDirs`; call `task.generate()` directly; `Order.java` exists in the output.
- `taskActionWrapsTheFailure`: no `packageName`: `generate()` throws `GradleException` with "Set bookmap.copybooks.packageName.".
- `taskActionWrapsAnIoFailure`: make `outputDir` a regular file (not a directory): `generate()` throws `GradleException` that starts with "Cannot write the generated classes: ".
- `configCopyDirsComeFromTheOptionsFiles`: with `jsoncopy`: `getConfigCopyDirs().getFiles()` contains `jsoncopylib`.
- `logWritesWarningsAndErrors`: run a copybook that gives a warning and one that gives an error through `generate()`; catch the exception. This covers both `Log` methods. (If the logger output cannot be read in a `ProjectBuilder` test, assert only that the exception happened. The coverage is the purpose.)

- [ ] **Step 2: Run the tests to make sure they fail**

Run: `./gradlew :bookmap-copybook-gradle-plugin:test --tests '*BookmapCopybookPluginTest'`
Expected: FAIL (compile errors).

- [ ] **Step 3: Implement the four classes**

Follow the Interfaces block and the details above.

- [ ] **Step 4: Run the full module build**

Run: `./gradlew :bookmap-copybook-gradle-plugin:build`
Expected: BUILD SUCCESSFUL, which includes the JaCoCo rule at 100% line and branch. If coverage is short, read `bookmap-copybook-gradle-plugin/build/reports/jacoco/test/jacocoTestReport.xml` and add unit tests. Do not add exclusions.

- [ ] **Step 5: Commit**

```bash
git add bookmap-copybook-gradle-plugin
git commit -m "feat: add the copybook Gradle plugin"
```

---

### Task 4: TestKit functional tests

**Files:**
- Test: `.../gradle/BookmapCopybookPluginFunctionalTest.java`

**Interfaces:**
- Consumes: the plugin (Task 3) through `GradleRunner.create().withPluginClasspath()`.

Each test writes a project into a `@TempDir`: `settings.gradle.kts` (with a `buildCache { local { directory = <tempDir>/cache } }` block when the test needs the cache), `build.gradle.kts` with `plugins { java; id("pt.dazz.bookmap.copybook") }`, and the copybooks. For `compileJava`, the test project needs `bookmap-core` on the compile classpath: pass the path of the `bookmap-core` JAR to the test as a system property from `build.gradle.kts` (`tasks.test { dependsOn(":bookmap-core:jar"); systemProperty("bookmap.core.jar", ...) }`), and use `dependencies { implementation(files("<path>")) }` in the test project.

- [ ] **Step 1: Write the tests**

- `compilesTheGeneratedClasses`: `build` passes; the task outcome is `SUCCESS`; `build/classes/java/main/.../orders/Order.class` exists.
- `secondBuildIsUpToDate`: the second `compileJava` gives `UP-TO-DATE` for `generateCopybookClasses`.
- `copybookChangeRunsTaskAgain`, `jsonChangeRunsTaskAgain`, `memberChangeRunsTaskAgain` (a member in the extension `copyDirs`), and `memberChangeInJsonCopyDirRunsTaskAgain` (a member in a directory that only `ACCOUNT.json` declares): after the change, the outcome is `SUCCESS`.
- `removedCopybookRemovesItsClasses`: remove `ORDER.cpy`; run again; `Order.java` is not in the output.
- `buildCacheGivesFromCache`: run `generateCopybookClasses --build-cache`; run `clean`; run again with `--build-cache`; the outcome is `FROM-CACHE`.
- `configurationCacheIsReused`: run `generateCopybookClasses --configuration-cache` twice; the second output contains "Reusing configuration cache.".
- `copybookErrorFailsTheBuild`: `buildAndFail()`; the output contains the diagnostic of each broken copybook and "2 copybook(s) have errors".
- `invalidJsonFailsWithTheFilePath`: `buildAndFail()`; the output contains `SHIPMENT.json`.
- `missingMemberFailsWithTheSearchedDirectories`: `buildAndFail()`; the output contains "copy member 'ADDRESS' not found in" and the copylib path.
- `excludeKeepsAMemberOut`: the member is in `src/main/copybook/members/`, with `exclude("members/**")` and `copyDirs.from("src/main/copybook/members")`: the build passes.
- `collisionFailsAndPackagePerCopybookFixes`: `collision/` fails with "packagePerCopybook = true"; the same project with `packagePerCopybook = true` passes.
- `missingPackageNameFails`: `buildAndFail()` with "Set bookmap.copybooks.packageName.".
- `noSourceDirPasses`: no `src/main/copybook`, no `packageName`: `build` passes.
- `withoutJavaPluginApplies`: `plugins { id("pt.dazz.bookmap.copybook") }` only; `generateCopybookClasses` runs.

- [ ] **Step 2: Run the tests**

Run: `./gradlew :bookmap-copybook-gradle-plugin:test --tests '*FunctionalTest'`
Expected: PASS. If one fails, it shows a defect in Task 2 or Task 3: fix it there (the code), not in the test.

- [ ] **Step 3: Run the full module build**

Run: `./gradlew :bookmap-copybook-gradle-plugin:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add bookmap-copybook-gradle-plugin
git commit -m "test: add Gradle TestKit tests for the copybook plugin"
```

---

### Task 5: Publication and the example build

**Files:**
- Modify: `build.gradle.kts` (root): `publishedModules` and the publication type
- Modify: `.github/workflows/release.yml`: the published JAR list, and the example build step
- Create: `bookmap-examples-gradle-plugin/settings.gradle.kts`, `build.gradle.kts`, `src/main/copybook/orders/ORDER.cpy`, `src/main/copybook/billing/INVOICE.cpy`, `src/main/copybook/billing/INVOICE.json`, `src/main/copylib/ADDRESS.cpy`, `src/test/java/pt/dazz/bookmap/examples/plugin/...Test.java`

- [ ] **Step 1: Validate the double `includeBuild` (spec section 10 risk)**

Create the example `settings.gradle.kts`: `pluginManagement { includeBuild("..") }`, a top-level `includeBuild("..")`, `rootProject.name = "bookmap-examples-gradle-plugin"`. Create `build.gradle.kts`: `plugins { java; id("pt.dazz.bookmap.copybook") }`, `repositories { mavenCentral() }`, the Java 21 toolchain, the dependencies `implementation("pt.dazz.bookmap:bookmap-core")`, `annotationProcessor("pt.dazz.bookmap:bookmap-processor")`, `testImplementation("pt.dazz.bookmap:bookmap-transport-simulator")`, and the JUnit and AssertJ test dependencies with explicit versions from `gradle/libs.versions.toml`. Use the version `0.1.0-SNAPSHOT` for the `pt.dazz.bookmap` dependencies if Gradle requires a version. Set `bookmap { copybooks { packageName = "pt.dazz.bookmap.examples.plugin"; copyDirs.from("src/main/copylib") } }`.

Run: `./gradlew -p bookmap-examples-gradle-plugin generateCopybookClasses`
Expected: BUILD SUCCESSFUL. If Gradle rejects the double include, use the fallback of the spec: `mavenLocal()` in the example, and the CI step runs `./gradlew publishToMavenLocal` first. Write the result in this plan, under this step.

- [ ] **Step 2: Add the copybooks and a test**

Copybooks: as in spec section 10; `INVOICE.cpy` uses `COPY ADDRESS.`; `INVOICE.json` sets `stripPrefixes` (for example `["INV-"]`). The test encodes an `Order` request and decodes it through `bookmap-transport-simulator`, as `bookmap-examples` tests do (read one of them for the API).

Run: `./gradlew -p bookmap-examples-gradle-plugin build`
Expected: BUILD SUCCESSFUL. The test passes.

- [ ] **Step 3: Configure the publication**

In the root `build.gradle.kts`: add `"bookmap-copybook-gradle-plugin" to "BookMap copybook generator Gradle plugin."` to `publishedModules`. In the `configure<MavenPublishBaseExtension>` block, use `GradlePlugin(javadocJar = JavadocJar.Javadoc(), sourcesJar = true)` (import `com.vanniktech.maven.publish.GradlePlugin`) when the project has the plugin `java-gradle-plugin`, else `JavaLibrary(...)`.

Run: `./gradlew :bookmap-copybook-gradle-plugin:publishToMavenLocal`
Expected: BUILD SUCCESSFUL. `~/.m2/repository/pt/dazz/bookmap/bookmap-copybook-gradle-plugin/0.1.0-SNAPSHOT/` has the JAR, the sources JAR, the Javadoc JAR, and a POM with the description. `~/.m2/repository/pt/dazz/bookmap/copybook/pt.dazz.bookmap.copybook.gradle.plugin/0.1.0-SNAPSHOT/` has the marker POM, which depends on `pt.dazz.bookmap:bookmap-copybook-gradle-plugin`. Check that the marker POM has the name, URL, license, developer, and SCM data.

- [ ] **Step 4: Update the workflow**

In `.github/workflows/release.yml`:
- Job `build`: add a step after "Build and test", named "Build the Gradle plugin example": `./gradlew -p bookmap-examples-gradle-plugin build --no-daemon`. If Step 1 used the fallback, run `publishToMavenLocal` first in the same step.
- Job `publish`, step "Collect the published JARs": add `bookmap-copybook-gradle-plugin` to the module list.
- Add one line to the comment at the top that says the build job also builds the plugin example.

- [ ] **Step 5: Run the full build**

Run: `./gradlew build` and then `./gradlew -p bookmap-examples-gradle-plugin build`
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts .github/workflows/release.yml bookmap-examples-gradle-plugin
git commit -m "feat: publish the copybook Gradle plugin and add its example build"
```

---

### Task 6: Documentation

**Files:**
- Create: `bookmap-copybook-gradle-plugin/README.md`
- Modify: `README.md` (root), in the "Copybook generator" section

- [ ] **Step 1: Write the module README**

Use the ten sections of spec section 12, in that order, with the exact warning and caution texts of the spec. Each example must match the real behavior: use the DSL of the example build, and copy the real message texts of Task 2 (the collision message, "Set bookmap.copybooks.packageName.", "copy member '...' not found in ..."). Section 6 (collisions) shows the `COPY ADDRESS` case, the failure message, and a before and after table of packages for `packagePerCopybook`. Section 7 (`COPY` members) states that the search is not recursive, the search order (the options file first, then the extension), and the accepted extensions (none, `.cpy`, `.cbl`, `.cob`).

- [ ] **Step 2: Update the root README**

Add a subsection "Gradle plugin" at the end of the "Copybook generator" section: the `pluginManagement` block, the `plugins` block, a minimum `bookmap { copybooks { ... } }` block, and a link to `bookmap-copybook-gradle-plugin/README.md`. If the root README lists the modules, add the new module.

- [ ] **Step 3: Check the style**

Read both texts against the ASD-STE100 rules of `CLAUDE.md`: sentence length, one instruction in each sentence, the imperative form, no "-ing" verb forms, and one word for one meaning ("copybook", "options file", "copy directory", "member").

- [ ] **Step 4: Commit**

```bash
git add README.md bookmap-copybook-gradle-plugin/README.md
git commit -m "docs: document the copybook Gradle plugin"
```

---

### After the tasks

- Push the branch and open a PR to `main`. Do not merge: the author must approve the merge. A `feat:` merge publishes a minor version to Maven Central, and you cannot undo it.
- Wait for the green `build` check.
