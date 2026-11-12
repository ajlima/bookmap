# Copybook Maven Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Maven plugin `pt.dazz.bookmap:bookmap-copybook-maven-plugin` (goal `generate`). It has the same rules as the Gradle plugin `pt.dazz.bookmap.copybook`.

**Architecture:** The tool-independent logic of the Gradle plugin moves to `bookmap-copybook`, package `pt.dazz.bookmap.copybook.tree`. The two plugins become thin adapters over it. The Maven plugin is a Gradle module. The Gradle plugin `de.benediktritter.maven-plugin-development` generates its descriptor from the Mojo annotations. Unit tests give the coverage. Integration tests download Maven 3.6.3 and 3.9.16 and run them on small projects against a file repository that the build publishes.

**Tech Stack:** Java 21, Gradle 9.9.1, `maven-plugin-api`/`maven-core` 3.6.3 (provided), `maven-plugin-annotations` 3.16.0, `de.benediktritter.maven-plugin-development` 0.4.3 with ASM 9.8, JUnit 6, AssertJ, JaCoCo, `com.vanniktech.maven.publish` 0.37.0.

**Spec:** `docs/superpowers/specs/2026-11-12-bookmap-copybook-maven-plugin-design.md`

## Global Constraints

- Java 21. No runtime reflection in `main` code. No middleware dependency in `bookmap-copybook`.
- The Gradle plugin keeps its behavior and its user messages. Its TestKit tests must pass with the same expected texts.
- Coordinates `pt.dazz.bookmap:bookmap-copybook-maven-plugin`. Packaging `maven-plugin`. Goal prefix `bookmap-copybook`. Mojo `pt.dazz.bookmap.copybook.maven.GenerateMojo`, goal `generate`, phase `generate-sources`, `threadSafe = true`.
- Maven API 3.6.3, scope `provided`. No Maven 4 API.
- Maven messages: missing package "Set the packageName parameter of bookmap-copybook-maven-plugin."; collision hint `<packagePerCopybook>true</packagePerCopybook>`.
- 100% line and branch coverage in `bookmap-copybook`, `bookmap-copybook-gradle-plugin`, and `bookmap-copybook-maven-plugin`.
- English. ASD-STE100 for docs. International business data.
- Commit email `alexandre.lima@dazz.pt`. No Claude signature and no `Co-Authored-By` trailer.
- Branch `feat/bookmap-copybook-maven-plugin`. One commit per task at minimum.

## Review Focus

1. A Maven 3.6.3 build on JDK 21: the plugin loads, and the descriptor has no field that 3.6.3 rejects. Test in Task 5 (`maven363GeneratesAndCompiles`).
2. A second `mvn compile` with no change: the goal logs "up to date", and the compiler still sees the classes (the source root is added). Test in Task 3 and Task 5.
3. A changed `COPY` member in a directory that only an options file declares: the fingerprint changes. Test in Task 3 (`memberInJsonCopyDirChangesTheFingerprint`).
4. A failed run: no fingerprint is stored, so the next build runs again. Test in Task 3 (`failedRunStoresNoFingerprint`).
5. A reactor build with the copybook module and a consumer module: the consumer compiles against the generated classes. Test in Task 5 (`multiModuleReactor`) and the example (Task 6).

---

### Task 1: Move the shared logic to `bookmap-copybook`

**Files:**
- Move: `bookmap-copybook-gradle-plugin/src/main/java/pt/dazz/bookmap/copybook/gradle/PackageNames.java` to `bookmap-copybook/src/main/java/pt/dazz/bookmap/copybook/tree/PackageNames.java`
- Move and rename: `.../gradle/CopybookGeneration.java` to `.../copybook/tree/CopybookTree.java`; `.../gradle/CopybookGenerationException.java` to `.../copybook/tree/CopybookTreeException.java`
- Move: `PackageNamesTest`, `CopybookGenerationTest` (rename to `CopybookTreeTest`) to `bookmap-copybook/src/test/java/pt/dazz/bookmap/copybook/tree/`
- Move: `TestCopybooks.java` and `src/test/resources/copybooks/**` to `bookmap-copybook/src/testFixtures/java/pt/dazz/bookmap/copybook/tree/` and `bookmap-copybook/src/testFixtures/resources/copybooks/**`
- Modify: `bookmap-copybook/build.gradle.kts` (apply `java-test-fixtures`; do not publish the test fixtures variant)
- Modify: `bookmap-copybook-gradle-plugin/build.gradle.kts` (`testImplementation(testFixtures(project(":bookmap-copybook")))`), `GenerateCopybookClasses.java`, `BookmapCopybookPluginTest.java`, `BookmapCopybookPluginFunctionalTest.java`

**Interfaces:**
- Produces (all public, package `pt.dazz.bookmap.copybook.tree`):
  - `final class PackageNames`: `static String segment(String name)`, `static String packageOf(String basePackage, Path relativeDir, String copybookName, boolean perCopybook)`.
  - `final class CopybookTree`:
    - `record Settings(Path sourceDir, List<Path> copyDirs, String basePackage, boolean lombok, boolean packagePerCopybook, String missingPackageMessage, String packagePerCopybookHint)`
    - `interface Log { void warn(String message); void error(String message); }`
    - `static void run(Settings, List<Path> copybooks, Path outputDir, Log) throws IOException`
    - `static List<Path> configCopyDirs(List<Path> copybooks)`
    - `static Path optionsFile(Path copybook)`
  - `final class CopybookTreeException extends RuntimeException` (public constructor `(String message)`).
  - Test fixture: `final class TestCopybooks` with `static Path copy(String name, Path target)` and `static List<Path> copybooks(Path dir)`.

Rules:
- The missing-package exception message is `settings.missingPackageMessage()`.
- The collision message ends with: `"Set " + settings.packagePerCopybookHint() + ", set \"package\" in an options file, or put the copybooks in different directories."`. The Gradle plugin passes `bookmap.copybooks.packagePerCopybook = true`, so its text does not change.
- In `bookmap-copybook/build.gradle.kts`, after `java-test-fixtures`, skip the fixtures variants in the published component: `components["java"] as AdhocComponentWithVariants` with `withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }` and the same for `testFixturesRuntimeElements`.

- [ ] **Step 1: Move the tests and the fixtures first.** Change their package to `pt.dazz.bookmap.copybook.tree`, the class names to `CopybookTree`, `CopybookTreeException`, and `CopybookTreeTest`, and the `Settings` calls to the seven-argument form. In `CopybookTreeTest`, add `customMessagesAreUsed`: with `missingPackageMessage = "Set the packageName parameter of bookmap-copybook-maven-plugin."` and `packagePerCopybookHint = "<packagePerCopybook>true</packagePerCopybook>"`, the missing-package message and the collision message contain these texts.
- [ ] **Step 2: Run** `./gradlew :bookmap-copybook:test`. Expected: FAIL (compile errors: the classes are not in `bookmap-copybook` yet).
- [ ] **Step 3: Move the three classes,** make them public with Javadoc on each public member, and add the two `Settings` fields. Change the Gradle plugin to use them and to pass its two texts.
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook:build :bookmap-copybook-gradle-plugin:build`. Expected: BUILD SUCCESSFUL, 100% coverage in both modules, and all TestKit tests pass with their texts unchanged.
- [ ] **Step 5: Run** `./gradlew :bookmap-copybook:publishToMavenLocal` and check that `~/.m2/.../bookmap-copybook/0.1.0-SNAPSHOT/` has no `-test-fixtures` JAR, and the `.module` file has no fixtures variant.
- [ ] **Step 6: Commit** `refactor: move the copybook tree logic to bookmap-copybook`.

---

### Task 2: The Maven plugin module and the Mojo

**Files:**
- Modify: `settings.gradle.kts` (`include("bookmap-copybook-maven-plugin")`), `gradle/libs.versions.toml` (versions and libraries below)
- Create: `bookmap-copybook-maven-plugin/build.gradle.kts`
- Create: `bookmap-copybook-maven-plugin/src/main/java/pt/dazz/bookmap/copybook/maven/GenerateMojo.java`
- Test: `bookmap-copybook-maven-plugin/src/test/java/pt/dazz/bookmap/copybook/maven/GenerateMojoTest.java`

**Interfaces:**
- Consumes: `CopybookTree`, `CopybookTreeException`, `TestCopybooks` (Task 1).
- Produces: `public class GenerateMojo extends AbstractMojo`, with package-private fields (Maven injects them; tests set them): `MavenProject project`, `File sourceDir`, `List<File> copyDirs`, `String packageName`, `boolean lombok`, `boolean packagePerCopybook`, `File outputDir`, `List<String> includes`, `List<String> excludes`, `boolean skip`. The `@Parameter` annotations and defaults are those of spec section 6. `project` uses `@Parameter(defaultValue = "${project}", readonly = true, required = true)`.

Build file:
- `plugins { id("de.benediktritter.maven-plugin-development") version "0.4.3" }`, through the version catalog (`[plugins] maven-plugin-development`).
- A `buildscript` block that forces `org.ow2.asm:*` to `9.8` on the `classpath` configuration (`resolutionStrategy.eachDependency`). Comment: the plugin-tools of 0.4.3 cannot read Java 21 class files without it.
- `mavenPlugin { goalPrefix = "bookmap-copybook"; name = "BookMap copybook Maven plugin"; description = "Generates BookMap classes from COBOL copybooks." }`
- Dependencies: `implementation(project(":bookmap-copybook"))`; `compileOnly` and `testImplementation` of `maven-plugin-api:3.6.3`, `maven-core:3.6.3`, `maven-plugin-annotations:3.16.0`; `testImplementation(testFixtures(project(":bookmap-copybook")))`.

Behavior of `execute()` in this task (spec section 7, without the fingerprint):
1. `project.addCompileSourceRoot(outputDir.getAbsolutePath())`.
2. If `skip`: `getLog().info("Skipped.")`, return.
3. Collect the copybooks with `org.codehaus.plexus.util.DirectoryScanner` (from `plexus-utils`, which Maven 3.6.3 provides): base `sourceDir`, includes (default `**/*.cpy` when `includes` is null or empty), excludes. A missing `sourceDir` gives an empty list.
4. Call `CopybookTree.run` with the Maven messages of the Global Constraints, and a `Log` to `getLog().warn` and `getLog().error`.
5. `CopybookTreeException` becomes `MojoFailureException(message)`. `IOException` becomes `MojoExecutionException("Cannot write the generated classes: " + message, e)`.

Add `plexus-utils` as `compileOnly`/`testImplementation` with the version that Maven 3.6.3 uses (3.2.1), if `maven-core` does not bring it.

- [ ] **Step 1: Write the failing tests** in `GenerateMojoTest` (a helper creates the Mojo with defaults and `project = new MavenProject()`, and a `@TempDir` copy of the fixtures):
  - `generatesIntoDerivedPackages` (fixtures `tree` + `copylib`): `orders/Order.java` and the options-file package exist.
  - `addsTheSourceRoot`: `project.getCompileSourceRoots()` contains `outputDir`.
  - `skipAddsTheSourceRootAndGeneratesNothing`.
  - `includesReplaceTheDefault`, `excludesFilterFiles`.
  - `missingSourceDirGeneratesNothing`.
  - `missingPackageNameFails`: `MojoFailureException` with "Set the packageName parameter of bookmap-copybook-maven-plugin.".
  - `collisionFailsWithTheMavenHint`: the message contains `<packagePerCopybook>true</packagePerCopybook>`.
  - `ioFailureIsAnExecutionException`: `outputDir` below a regular file.
  - `logGetsWarningsAndErrors`: a `Log` stub (`org.apache.maven.plugin.logging.SystemStreamLog` subclass that records) receives the warning of `warning/PAYMENT.cpy` and the errors of `broken/`.
- [ ] **Step 2: Run** `./gradlew :bookmap-copybook-maven-plugin:test`. Expected: FAIL (no Mojo).
- [ ] **Step 3: Implement** the build file and `GenerateMojo`, with Javadoc on each parameter.
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook-maven-plugin:build`. Expected: BUILD SUCCESSFUL with 100% coverage. Check `META-INF/maven/plugin.xml` in the JAR: `goalPrefix` `bookmap-copybook`, goal `generate`, phase `generate-sources`, `threadSafe` true, and the ten parameters with their properties and defaults.
- [ ] **Step 5: Commit** `feat: add the copybook Maven plugin`.

---

### Task 3: The up-to-date check

**Files:**
- Create: `bookmap-copybook-maven-plugin/src/main/java/pt/dazz/bookmap/copybook/maven/Fingerprint.java`
- Modify: `GenerateMojo.java`
- Test: `FingerprintTest.java`, `GenerateMojoTest.java`

**Interfaces:**
- Produces: `final class Fingerprint` with `static String of(String pluginVersion, List<String> parameters, Path sourceDir, List<Path> copybooks, List<Path> optionsFiles, List<Path> copyDirs) throws IOException` (hex SHA-256, inputs in the order of spec section 8; a missing copy directory adds only its path).
- `GenerateMojo` gets `@Parameter(defaultValue = "${project.build.directory}", readonly = true) File buildDirectory` and `@Parameter(defaultValue = "${plugin.version}", readonly = true) String pluginVersion`. The fingerprint file is `buildDirectory/bookmap-copybook/fingerprint`.

Goal order: source root, skip, collect, fingerprint, compare, run, store. The copy directories for the fingerprint are the `copyDirs` parameter, then `CopybookTree.configCopyDirs(copybooks)`.

- [ ] **Step 1: Write the failing tests.** `FingerprintTest`: `sameInputsGiveTheSameHash`; and one test for each change that gives a new hash: `copybookContent`, `copybookAdded`, `copybookRemoved`, `optionsFile`, `copyDirMember`, `copyDirOrder`, `parameter`, `pluginVersion`; `missingCopyDirIsPermitted`. `GenerateMojoTest`: `secondRunIsUpToDate` (the log has "The generated classes are up to date.", and a file written to `outputDir` between the runs is still there); `changeRunsAgain`; `memberInJsonCopyDirChangesTheFingerprint` (fixtures `jsoncopy` + `jsoncopylib`); `failedRunStoresNoFingerprint`; `missingOutputDirRunsAgain` (the stored fingerprint is equal, but `outputDir` was deleted).
- [ ] **Step 2: Run** `./gradlew :bookmap-copybook-maven-plugin:test`. Expected: FAIL.
- [ ] **Step 3: Implement** `Fingerprint` and the check in `GenerateMojo`.
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook-maven-plugin:build`. Expected: BUILD SUCCESSFUL with 100% coverage.
- [ ] **Step 5: Commit** `feat: skip the copybook generation when no input changed`.

---

### Task 4: Publication

**Files:**
- Modify: root `build.gradle.kts`, `.github/workflows/release.yml`

- [ ] **Step 1:** Add `"bookmap-copybook-maven-plugin" to "BookMap copybook generator Maven plugin."` to `publishedModules`. For this module, set `pom { packaging = "maven-plugin" }` in the `MavenPublishBaseExtension` block.
- [ ] **Step 2:** Add a file repository for the integration tests to each published module: `extensions.configure<PublishingExtension> { repositories { maven { name = "integrationTest"; url = uri(rootProject.layout.buildDirectory.dir("integration-test-repo")) } } }`. Gradle then has `publishAllPublicationsToIntegrationTestRepository` in each module.
- [ ] **Step 3: Run** `./gradlew :bookmap-copybook-maven-plugin:publishToMavenLocal`. Expected: the POM has `<packaging>maven-plugin</packaging>`, the license, the developer, and the SCM data; it has `bookmap-copybook` as a runtime dependency and no Maven API dependency; the JAR has `META-INF/maven/plugin.xml`.
- [ ] **Step 4:** In `release.yml`, add `bookmap-copybook-maven-plugin` to the module list of "Collect the published JARs".
- [ ] **Step 5: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `build: publish the copybook Maven plugin`.

---

### Task 5: Integration tests with real Maven

**Files:**
- Modify: `bookmap-copybook-maven-plugin/build.gradle.kts` (a source set `integrationTest` and a `Test` task `integrationTest`, which `check` depends on)
- Create: `bookmap-copybook-maven-plugin/src/integrationTest/java/pt/dazz/bookmap/copybook/maven/MavenIntegrationTest.java`, and test projects in `src/integrationTest/resources/projects/`

Setup:
- The task depends on `publishAllPublicationsToIntegrationTestRepository` of `bookmap-core`, `bookmap-processor`, `bookmap-copybook`, `bookmap-copybook-maven-plugin`, and `bookmap-transport-simulator`. It passes as system properties: the repository path, the BookMap version, and a cache directory for the Maven distributions (`build/maven-distributions`).
- A helper downloads `https://repo1.maven.org/maven2/org/apache/maven/apache-maven/<v>/apache-maven-<v>-bin.zip` for `3.6.3` and `3.9.16` once, unzips it, and runs `bin/mvn` (`bin/mvn.cmd` on Windows) with `ProcessBuilder`: `-B -s <settings.xml> -Dmaven.repo.local=<temp>/m2 -Dbookmap.version=<version>`, and `JAVA_HOME` of the test JVM. The `settings.xml` of the test adds the file repository as a repository and a plugin repository (snapshots enabled), next to Maven Central.
- JaCoCo does not cover this source set. The unit tests give the coverage.

- [ ] **Step 1: Write the tests,** each for both Maven versions (`@ParameterizedTest` with `3.6.3` and `3.9.16`):
  - `generatesAndCompiles` (named `maven363GeneratesAndCompiles` for the 3.6.3 case in the display name): `mvn compile` passes; `target/classes/.../orders/Order.class` and the codec class exist.
  - `optionsFileAndSubpackages`.
  - `missingMemberFails`: the output contains "copy member 'ADDRESS' not found in".
  - `collisionFailsAndPackagePerCopybookFixes`: the output contains `<packagePerCopybook>true</packagePerCopybook>`; with `-Dbookmap.copybook.packagePerCopybook=true` the build passes.
  - `secondBuildIsUpToDate`: the second `mvn compile` output contains "The generated classes are up to date.".
  - `multiModuleReactor`: a parent with `pluginManagement`, a module with the copybooks, and a module that uses the generated classes: `mvn verify` passes.
- [ ] **Step 2: Run** `./gradlew :bookmap-copybook-maven-plugin:integrationTest`. Expected: PASS for both Maven versions. A failure here shows a defect in Task 2 or Task 3, or in the descriptor: fix the code.
- [ ] **Step 3: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `test: run the copybook Maven plugin with Maven 3.6.3 and 3.9`.

---

### Task 6: The example build

**Files:**
- Create: `bookmap-examples-maven-plugin/` with `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` (Maven 3.9.16, wrapper 3.3.4), `pom.xml` (parent), `mainframe-model/pom.xml` with the copybooks (`orders/GET-ORDER.cpy`, `billing/GET-INVOICE.cpy` with `GET-INVOICE.json`, `src/main/copylib/ADDRESS.cpy`), and `orders-service/pom.xml` with a test through `bookmap-transport-simulator`.
- Modify: `.github/workflows/release.yml` (job `build`)

- [ ] **Step 1:** Create the wrapper files with the content of the Apache Maven Wrapper 3.3.4 (`only-script` type, so no JAR file is in the repository). The parent POM has `<bookmap.version>0.1.0-SNAPSHOT</bookmap.version>`, `<maven.compiler.release>21</maven.compiler.release>`, the plugin in `<pluginManagement>`, and a `<repositories>` entry is not necessary (the snapshot comes from the local repository).
- [ ] **Step 2: Run** `./gradlew publishToMavenLocal` and then `bookmap-examples-maven-plugin/mvnw -B -f bookmap-examples-maven-plugin/pom.xml verify`. Expected: BUILD SUCCESS, and the test of `orders-service` passes.
- [ ] **Step 3:** In `release.yml`, job `build`, after "Build the Gradle plugin example", add the step "Build the Maven plugin example": `./gradlew publishToMavenLocal --no-daemon` and then the `mvnw` command above.
- [ ] **Step 4: Commit** `feat: add the Maven plugin example build`.

---

### Task 7: Documentation

**Files:**
- Create: `bookmap-copybook-maven-plugin/README.md`
- Modify: `README.md`, `bookmap-copybook-gradle-plugin/README.md` (links only, if the rules are shared), `scripts/set-readme-version.sh`

- [ ] **Step 1: Write the module README** with the ten sections of spec section 12. Use the real messages of Tasks 2 and 3. Show the `<executions>` form, the `pluginManagement` form, the full command-line form `mvn pt.dazz.bookmap:bookmap-copybook-maven-plugin:0.1.0:generate`, and the multi-module pattern. Requirements: Maven 3.6.3 or later on JDK 21 or later; tested with 3.6.3 and 3.9.16.
- [ ] **Step 2: Update the root README:** a "Maven plugin" subsection after "Gradle plugin"; the limitation line "No Maven plugin yet." becomes a statement that both plugins exist (or is removed); two rows in the module table.
- [ ] **Step 3: Update `scripts/set-readme-version.sh`:** also change `bookmap-copybook-maven-plugin/README.md` and the root README: each `<version>` line directly after `<artifactId>bookmap-copybook-maven-plugin</artifactId>`, and the command-line form `bookmap-copybook-maven-plugin:<version>:generate`. Add checks for both. Write the versions as `0.1.0` first, then run the script with `0.5.0`.

  Ruling for the builder: use `0.5.0` as the current published version in the README, as the script does for the other places. The first release with the Maven plugin is 0.6.0, and the release job sets that version.
- [ ] **Step 4: Run** `scripts/set-readme-version.sh 0.5.0`, twice. Expected: the first run passes, and the second run changes nothing.
- [ ] **Step 5: Check the style** against ASD-STE100.
- [ ] **Step 6: Commit** `docs: document the copybook Maven plugin`.

---

### After the tasks

- Final whole-branch review. Push the branch and open a PR to `main`. Do not merge without the approval of the author: a `feat:` merge publishes 0.6.0 to Maven Central.
