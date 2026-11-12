# BookMap copybook Maven plugin: design

Date: 2026-11-12. Status: draft for the approval of the author.

Related documents: `docs/superpowers/specs/2026-11-12-bookmap-copybook-gradle-plugin-design.md` (the Gradle plugin), `docs/superpowers/specs/2026-11-11-bookmap-copybook-design.md` (the generator), `CLAUDE.md` (project rules).

## 1. Intent

Version 0.5.0 added the Gradle plugin `pt.dazz.bookmap.copybook`. This design adds the same function for Maven builds.

The first user is a multi-module Maven project with a corporate parent POM and an internal Nexus. This setup must not be a limitation. The author decided: use the same rules as the Gradle plugin.

A project applies the plugin, points it at a directory tree of copybooks, and gets the BookMap classes in a generated source root before `compile`.

## 2. Goals, non-goals, and anti-goals

Goals:

- The same behavior as the Gradle plugin: the directory tree, the packages from directories, the `<NAME>.json` options files, the declared copy directories, `includes` and `excludes`, `packagePerCopybook`, and the same error and collision messages.
- One implementation of these rules, used by both plugins.
- Support Maven 3.6.3 and later.
- Skip the generation when no input changed.
- Publish the plugin to Maven Central with the release pipeline that exists.
- Document the plugin in its own module README, with the multi-module pattern.

Non-goals:

- Reading copybooks from a dependency artifact. In a multi-module project, one module generates the classes, and the other modules depend on its JAR (section 9).
- Maven 4. On 2026-11-12, Maven 4 is still a release candidate (4.0.0-rc-7). The plugin uses only Maven 3 API, and the tests cover 3.6.3 to 3.9. The README says "tested with Maven 3.6.3 to 3.9". (A call of the designer.)
- A goal prefix that works with no setup (`mvn bookmap-copybook:generate`). Maven resolves a prefix only for the groups in `settings.xml` (`<pluginGroups>`), and the publication does not write group-level plugin metadata. The README shows the `<executions>` form, and the full form `mvn pt.dazz.bookmap:bookmap-copybook-maven-plugin:<version>:generate`. (A call of the designer.)
- An m2e (Eclipse) incremental build integration.
- Global parse or mapping options in the plugin configuration (as in the Gradle plugin, section 2).

Anti-goals (a failure even if the code works):

- Two copies of the generation rules, one in each plugin. They would drift apart.
- A build that silently skips a copybook, or that compiles stale generated classes after a copybook change.
- A plugin that does not work behind a Maven mirror (Nexus or Artifactory), or that needs a repository other than Maven Central.

## 3. Decisions of the author

1. **Same rules as the Gradle plugin.**
2. **Multi-module: option A.** A shared module holds the copybooks and runs the plugin. Its JAR contains the generated classes and their codecs. The other modules depend on that JAR. The plugin does not read copybooks from other artifacts.
3. **Minimum Maven version: 3.6.3.**
4. **Publication: Maven Central.**

## 4. Shared logic

Move the tool-independent logic of the Gradle plugin into `bookmap-copybook`:

- `pt.dazz.bookmap.copybook.gradle.PackageNames` becomes `pt.dazz.bookmap.copybook.tree.PackageNames` (public).
- `pt.dazz.bookmap.copybook.gradle.CopybookGeneration` and `CopybookGenerationException` become `pt.dazz.bookmap.copybook.tree.CopybookTree` and `pt.dazz.bookmap.copybook.tree.CopybookTreeException` (public). The class names are a call of the designer.
- The `Settings`, `Log`, `run`, `configCopyDirs`, and `optionsFile` members stay, with public visibility.
- The message for a missing base package depends on the tool. `Settings` gets one more field: `String missingPackageMessage`. The Gradle plugin gives "Set bookmap.copybooks.packageName.". The Maven plugin gives "Set the packageName parameter of bookmap-copybook-maven-plugin.".
- The collision message also names the tool option: `Settings` gets `String packagePerCopybookHint`. The Gradle plugin gives `bookmap.copybooks.packagePerCopybook = true`. The Maven plugin gives `<packagePerCopybook>true</packagePerCopybook>`.
- Move the unit tests and the test copybooks with the classes. The 100% coverage rule then applies in `bookmap-copybook`.
- The Gradle plugin keeps its behavior and its messages. Its TestKit tests must pass without a change to their expected texts.

## 5. Module and publication

- New module: `bookmap-copybook-maven-plugin`. Add it to `settings.gradle.kts`.
- Coordinates: `pt.dazz.bookmap:bookmap-copybook-maven-plugin`. Packaging: `maven-plugin`. Goal prefix: `bookmap-copybook`.
- Mojo class: `pt.dazz.bookmap.copybook.maven.GenerateMojo`. Goal: `generate`. Default phase: `generate-sources`. Thread-safe: `true`.
- Dependencies:
  - `implementation(project(":bookmap-copybook"))`.
  - `compileOnly` (Maven scope `provided`): `org.apache.maven:maven-plugin-api:3.6.3`, `org.apache.maven:maven-core:3.6.3`, `org.apache.maven.plugin-tools:maven-plugin-annotations` (a version that works with Maven 3.6.3).
- The plugin descriptor (`META-INF/maven/plugin.xml`):
  - First choice: the Gradle plugin `de.benediktritter.maven-plugin-development`, which generates the descriptor from the annotations. Its last release (0.4.3) is from 2024. Validate it with Gradle 9.9.1 in the first implementation task.
  - Fallback: write the descriptor by hand in `src/main/resources/META-INF/maven/plugin.xml`, and add a unit test that compares its parameters with the fields of the Mojo. Record the result in the plan.
- The POM must have packaging `maven-plugin`, and the Maven dependencies must have the scope `provided`. Validate the generated POM.
- Add the module to `publishedModules` in the root `build.gradle.kts` ("BookMap copybook generator Maven plugin.").
- Add the module to the list of published JARs in `.github/workflows/release.yml`.

## 6. Configuration

```xml
<plugin>
  <groupId>pt.dazz.bookmap</groupId>
  <artifactId>bookmap-copybook-maven-plugin</artifactId>
  <version>0.6.0</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <packageName>com.example.mainframe</packageName>
    <copyDirs>
      <copyDir>src/main/copylib</copyDir>
    </copyDirs>
  </configuration>
</plugin>
```

| Parameter | Property | Default | Meaning |
|---|---|---|---|
| `sourceDir` | `bookmap.copybook.sourceDir` | `${project.basedir}/src/main/copybook` | The root of the copybook tree. |
| `copyDirs` | none | empty | The directories with `COPY` members, in search order. |
| `packageName` | `bookmap.copybook.packageName` | none | The base package. |
| `lombok` | `bookmap.copybook.lombok` | `false` | Generate Lombok classes. |
| `packagePerCopybook` | `bookmap.copybook.packagePerCopybook` | `false` | Add one package segment from the copybook name. |
| `outputDir` | `bookmap.copybook.outputDir` | `${project.build.directory}/generated-sources/copybook` | The directory for the generated Java files. |
| `includes` | none | `**/*.cpy` | The patterns of the copybook files. The user's patterns replace the default. |
| `excludes` | none | empty | The patterns of the files that are not copybooks. |
| `skip` | `bookmap.copybook.skip` | `false` | Do not run the goal. |

- The patterns use the Ant style of Maven (`**/*.cpy`), relative to `sourceDir`.
- Relative paths in `sourceDir`, `copyDirs`, and `outputDir` start at `${project.basedir}`. Maven resolves `File` parameters this way.
- In a corporate parent POM, put the plugin in `<pluginManagement>`. Each module then declares only the `groupId` and the `artifactId`.

## 7. The goal

1. Add `outputDir` to the compile source roots of the project (`MavenProject.addCompileSourceRoot`). Do this in all cases, also with `skip` and when the generation is up to date. Then the compiler always sees the classes of an earlier run.
2. If `skip` is `true`, log "Skipped." and return. Thus, `skip` means "do not generate again", not "remove the classes". (A call of the designer.)
3. Collect the copybook files: the files in `sourceDir` that the `includes` select and the `excludes` do not select. A missing `sourceDir` gives an empty list.
4. Compute the fingerprint of the inputs (section 8). If it is equal to the stored fingerprint, and `outputDir` exists, log "The generated classes are up to date." and return.
5. Call `CopybookTree.run` with a `Log` that writes to the Maven log (`getLog().warn`, `getLog().error`).
6. A `CopybookTreeException` becomes a `MojoFailureException` with the same message. An `IOException` becomes a `MojoExecutionException`.
7. After a successful run, store the fingerprint.

## 8. Up-to-date check

Maven has no input and output tracking. The goal does its own check:

- The fingerprint is a SHA-256 hash over, in this order:
  - the plugin version;
  - `packageName`, `lombok`, `packagePerCopybook`, `includes`, and `excludes`;
  - for each copybook, in sorted order of the relative path: the relative path and the content;
  - for each options file (`*.json` in `sourceDir`), in sorted order: the relative path and the content;
  - for each copy directory, in search order (the extension directories, then the directories of the options files): its path relative to `sourceDir`, and the relative path and the content of each file in it.
- Store the fingerprint in `${project.build.directory}/bookmap-copybook/fingerprint`.
- A changed, added, or removed file, a changed parameter, or a new plugin version gives a new fingerprint. The goal then generates again.
- `mvn clean` removes the fingerprint and the output.
- If the goal fails, it does not store a fingerprint. The next build runs the goal again.

## 9. Multi-module projects

The README shows this pattern:

```
parent/
  pom.xml                       pluginManagement for bookmap-copybook-maven-plugin
  mainframe-model/              the copybooks; runs the plugin and bookmap-processor
    src/main/copybook/...
    src/main/copylib/...
  orders-service/               depends on mainframe-model
  billing-service/              depends on mainframe-model
```

- `mainframe-model` depends on `bookmap-core`, has `bookmap-processor` as an annotation processor, and runs the goal.
- Its JAR has the generated classes and the generated codecs.
- The other modules depend on `mainframe-model`. They do not run the plugin.

## 10. The example build

- Add `bookmap-examples-maven-plugin/` at the repository root: a standalone Maven project with the Maven Wrapper (`mvnw`, `.mvn/wrapper/maven-wrapper.properties`). It is not part of the Gradle build.
- It is multi-module and follows section 9: a parent POM, a module `mainframe-model` with the copybooks (two subdirectories, an options file, and a `COPY` member in `src/main/copylib`), and a module `orders-service` with a test that encodes and decodes through `bookmap-transport-simulator`.
- It uses the snapshot version of this repository. The CI step first runs `./gradlew publishToMavenLocal`, then `./bookmap-examples-maven-plugin/mvnw -f bookmap-examples-maven-plugin/pom.xml verify`.
- Add this step to the `build` job of `.github/workflows/release.yml`, after the Gradle plugin example.

## 11. Tests and coverage

- `bookmap-copybook` and `bookmap-copybook-maven-plugin` keep 100% line and branch coverage.
- Unit tests of `GenerateMojo` construct the Mojo, set its fields, and call `execute()`, with a `MavenProject` from `maven-core`. Cover: the skip parameter, the source root, the up-to-date skip, each fingerprint input (a change gives a new run), the failure mapping, and the missing `sourceDir`.
- Integration tests run a real Maven, 3.6.3 and the latest 3.9, on small projects:
  - Use the Maven Wrapper or a Maven distribution that the test downloads. The machine of the author has no `mvn` command.
  - The tests need the plugin and the BookMap libraries in a local repository. Run them after `publishToMavenLocal`, or use a test-local repository.
  - Cover at least: compile of the generated classes, subpackages, an options file, a missing `COPY` member, a collision with and without `packagePerCopybook`, the second build that skips as up to date, and a multi-module reactor build.
  - Where these tests live (a Gradle test task, or the example build) is left to the builder, if the CI runs them on each pull request.
- Test data uses an international business domain.

## 12. Documentation

- New file `bookmap-copybook-maven-plugin/README.md`, with the same structure as the Gradle plugin README:
  1. What the plugin does.
  2. Setup, with the requirements: Maven 3.6.3 or later, run on JDK 21 or later.
  3. The parameters (section 6).
  4. Packages from directories (link to the rules, or repeat them).
  5. Options files.
  6. Class name collisions and `packagePerCopybook`, with the Maven hint.
  7. `COPY` members: where to put them, with the same warning as the Gradle README.
  8. Up-to-date check (section 8), and the `skip` parameter.
  9. Multi-module projects (section 9).
  10. A link to `bookmap-examples-maven-plugin/`.
- Do not duplicate the package and `COPY` rules in two READMEs if a link is clear. The builder decides. The rules must be visible from both READMEs.
- In the root `README.md`, add a "Maven plugin" subsection next to "Gradle plugin", and change the limitation "No Maven plugin yet."
- Add `bookmap-copybook-maven-plugin` and `bookmap-examples-maven-plugin` to the module table.
- Update `scripts/set-readme-version.sh`, so that it also sets the plugin version in the Maven plugin README (`<version>X.Y.Z</version>` after the `bookmap-copybook-maven-plugin` artifact).
- Write in English, in ASD-STE100 style.

## 13. Left to the builder

- The internal structure of the Mojo, beyond section 7.
- The exact location of the integration tests (section 11).
- The fallback for the plugin descriptor, if the first choice fails (section 5).
