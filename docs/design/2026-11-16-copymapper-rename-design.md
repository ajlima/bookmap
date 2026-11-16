# Rename the library to CopyMapper (design)

Date: 2026-11-16
Status: approved.
Base package: `pt.dazz.copymapper` (before this change: `pt.dazz.bookmap`)
Parent spec: `2026-11-10-copymapper-design.md` (before this change: `2026-11-10-bookmap-design.md`)

## 1. Intent

The library has a new name, CopyMapper, to prevent confusion with another software product with the name BookMap.
The repository moved from `ajlima/bookmap` to `dazz-pt/copymapper` before this change. GitHub redirects the old addresses.
This change applies the new name to the code, the modules, the Maven coordinates, the plugins, the command line, and the documentation.

The release `0.27.0` came from the new repository, but it still has the old coordinates.
The first release with the new name is `0.28.0`.

## 2. Goals, non-goals, and anti-goals

Goals:

- All identifiers use `copymapper` or `CopyMapper`: the Maven group, the Java packages, the modules, the plugin ids, the configuration keys, and the file names.
- The documentation uses the name CopyMapper and the addresses of `dazz-pt/copymapper`.
- A user of `0.27.0` or earlier can migrate with one wiki page.
- An application with the old Spring Boot keys fails at startup. It does not silently use the default values.
- A wrong import of an annotation with the same simple name causes a compile error.

Non-goals:

- No relocation POMs in the old group `pt.dazz.bookmap`. The published versions stay as they are on Maven Central.
- No change to the behavior of the codec, the generator, or the transports. The processor gets only the check of section 5.2. It still finds the classes from `@Operation`, as before.
- No change to the types with the prefix `Book` that are not annotations (section 4.3).
- No change to the GitHub repository settings (description, topics) in this change. Section 9 lists them.

Anti-goals:

- A text of the library names a private product of the author. Do not name it.
- A text claims that BookMap is a trademark. Use only the reason in section 1.
- A commit message contains the words `BREAKING CHANGE`. The release would become `1.0.0` (section 6).
- A replacement makes the address `ajlima/copymapper`. That address does not exist and does not redirect.

## 3. Approaches

| Approach | Result | Decision |
|---|---|---|
| Rename all identifiers in one release, with a migration page. | One break for the users. The code and the documentation agree. | **Selected.** |
| Keep the old coordinates and change only the text. | No break, but the old name stays in each import and each artifact. | Rejected: the name BookMap stays in the code. |
| Rename, and also publish relocation POMs in the old group. | Maven shows a warning that points to the new coordinates. | Rejected: Gradle does not reliably follow a relocation, Dependabot does not support it, and the user code does not compile after a relocation anyway. |

## 4. Names

### 4.1 Coordinates, modules, and packages

| Item | Before | After |
|---|---|---|
| Maven group | `pt.dazz.bookmap` | `pt.dazz.copymapper` |
| Java packages | `pt.dazz.bookmap.*` | `pt.dazz.copymapper.*` |
| Gradle root project | `bookmap` | `copymapper` |
| Modules | `bookmap-<suffix>` | `copymapper-<suffix>`, with the same suffix |

The suffix does not change. For example, `bookmap-core` becomes `copymapper-core`, and `bookmap-transport-ctg` becomes `copymapper-transport-ctg`.
This rule applies to the 11 published modules, to `bookmap-examples`, and to the standalone builds `bookmap-examples-gradle-plugin`, `bookmap-examples-maven-plugin`, and `bookmap-native-test`.
Move each module folder and each package folder with `git mv`, so that git keeps the file history.

The Maven Central namespace is `pt.dazz`. It already includes `pt.dazz.copymapper`. No new namespace is necessary.

The file `META-INF/native-image/pt.dazz.bookmap/bookmap-core/native-image.properties` moves to `META-INF/native-image/pt.dazz.copymapper/copymapper-core/`.
The services file of the generated codecs becomes `META-INF/services/pt.dazz.copymapper.spi.BookCodec`.

### 4.2 Types with `Bookmap` in the name

These types contain the old name. Each type becomes `CopyMapper<rest>`:

| Before | After |
|---|---|
| `BookmapClient` | `CopyMapperClient` |
| `BookmapException` | `CopyMapperException` |
| `BookmapAutoConfiguration` | `CopyMapperAutoConfiguration` |
| `BookmapProperties` | `CopyMapperProperties` |
| `BookmapTransportProperties` | `CopyMapperTransportProperties` |
| `BookmapProcessor` | `CopyMapperProcessor` |
| `BookmapExtension` | `CopyMapperExtension` |
| `BookmapCopybookPlugin` | `CopyMapperCopybookPlugin` |

Test classes, test methods, constants, and local names follow the same rule. For example, `BookmapClientTest` becomes `CopyMapperClientTest`, and `ProcessorOrder.BOOKMAP_FIRST` becomes `ProcessorOrder.COPYMAPPER_FIRST`.

### 4.3 Annotations

The annotations lose the prefix `Book`:

| Before | After |
|---|---|
| `@BookOperation` | `@Operation` |
| `@BookField` | `@Field` |
| `@BookCollection` | `@Occurs` |
| `@BookGroup` | `@Group` |
| `@BookSubTypes` (and `@BookSubTypes.Type`) | `@SubTypes` (and `@SubTypes.Type`) |
| `@BookDefaults` | `@Defaults` |

`@Occurs` uses the COBOL term `OCCURS`. It also prevents a conflict with `java.util.Collection`.
The attributes of each annotation do not change.
`@CobolValue`, `@CobolUnknown`, `Direction`, and `EmptyItem` do not change.

The other types with the prefix `Book` do not change, because a name without the prefix conflicts with the JDK or is too general:
`BookCodec`, `BookCodecRegistry`, `BookRequest`, `BookReader`, `BookWriter`, `BookModel`, and the suffix `_BookCodec` of the generated codecs.
"Book" is the COBOL term for a record layout. It is not the old name of the library.

The processor messages, the generator output, the Javadoc, and the wiki use the new annotation names.
The copybook generator writes the new annotations in the generated classes.

### 4.4 Keys and names that a user writes

| Where | Before | After |
|---|---|---|
| Spring Boot keys | `bookmap.charset`, `bookmap.max-calls`, `bookmap.transport.*` | `copymapper.charset`, `copymapper.max-calls`, `copymapper.transport.*` |
| Spring Boot environment variables | `BOOKMAP_*` | `COPYMAPPER_*` |
| Gradle plugin id | `pt.dazz.bookmap.copybook` | `pt.dazz.copymapper.copybook` |
| Gradle plugin declaration name | `bookmapCopybook` | `copymapperCopybook` |
| Gradle extension | `bookmap { copybooks { … } }` | `copymapper { copybooks { … } }` |
| Maven plugin goal prefix | `bookmap-copybook` | `copymapper-copybook` |
| Maven plugin user properties | `bookmap.copybook.*` | `copymapper.copybook.*` |
| Command line JAR | `bookmap-copybook-cli-<version>-all.jar` | `copymapper-copybook-cli-<version>-all.jar` |
| Command line name in the documentation | `bookmap-copybook` | `copymapper-copybook` |
| System properties of the build and the tests | `bookmap.core.jar`, `bookmap.repository`, `bookmap.version` | `copymapper.core.jar`, `copymapper.repository`, `copymapper.version` |
| GitHub release title | `BookMap X.Y.Z` | `CopyMapper X.Y.Z` |

### 4.5 Text and assets

- The name in text is CopyMapper. The wiki, the README files, `ROADMAP.md`, `RELEASING.md`, `CONTRIBUTING.md`, `CLAUDE.md`, the workflow comments, and the scripts use it.
- Each address `https://github.com/ajlima/bookmap` becomes `https://github.com/dazz-pt/copymapper`. This includes the wiki, the issues, the releases, and the POM fields `url` and `scm`. The POM developer id `ajlima` does not change.
- The first sentence of the README says that the library maps COBOL copybooks. A Java developer can otherwise read "CopyMapper" as a bean mapper.
- The three logo files in `docs/assets/` become `copymapper-icon.svg`, `copymapper-logo-dark.svg`, and `copymapper-logo-light.svg`. The text of the logo becomes `CopyMapper`. Adjust the width of the image for the longer name.

### 4.6 Design documents

Update all documents in `docs/design/`. Rename the six files with `bookmap` in the name, for example `2026-11-10-bookmap-design.md` to `2026-11-10-copymapper-design.md`.
Change the package names, the module names, the annotation names, and the type names in the text. Keep each `Status:` line.
Git keeps the old text in the history.

## 5. Protections

### 5.1 Old Spring Boot keys

Spring Boot ignores an unknown key. Thus, an application that keeps `bookmap.charset=IBM500` after the upgrade starts with the default charset `IBM037`.
These two charsets have different bytes for some characters, for example `[`, `]`, and `!`. The data becomes incorrect, and no error occurs.

`copymapper-spring-boot` must fail at startup if the environment contains an old key:

- An old key is a key that the library used before this change: `bookmap.charset`, `bookmap.max-calls`, and each key under `bookmap.transport`. The check uses the relaxed binding of Spring Boot, so it also finds the forms `bookmap.maxCalls`, `BOOKMAP_CHARSET`, `BOOKMAP_MAXCALLS`, and `BOOKMAP_TRANSPORT_CTG_PASSWORD`.
- Other keys that start with `bookmap` are not old keys. Another product with the name BookMap can use them, and the application must start.
- The check reads all enumerable property sources of the environment.
- The check runs before the `CopyMapperClient` bean is created. It runs also when no transport is configured.
- The error message lists each old key and says: `Rename the properties bookmap.* to copymapper.*`. It gives the address of the migration page. It does not show the values, because a value can be a password.

The builder selects the Spring mechanism, for example an `EnvironmentPostProcessor` or a condition in the auto-configuration.
The check is temporary. A later release can remove it.

### 5.2 A wrong import of an annotation

An annotation without a prefix has the same simple name as other common types, for example `io.swagger.v3.oas.annotations.Operation`, `java.lang.reflect.Field`, and the `@Field` of Spring Data MongoDB.
An IDE can select the wrong import. Most wrong imports fail to compile:

- A required attribute (`order`, `maxOccurs`) is missing, or an attribute (`pic`) is unknown.
- The Swagger `@Operation` can go only on a method. On a class, it does not compile.
- `java.util.Collection` is not an annotation. Also, the new name is `@Occurs`.

One case compiles without an error and loses data silently: `@Field(order = 3)` without other attributes, with the import of Spring Data MongoDB. That annotation also has `order`. The member is not in the layout, and the offsets of the next members are incorrect.

The processor must report a compile error in this case:

- **Member:** a class that the processor maps (an operation class, or a class that the processor reaches from it: a group, an item, a subtype) has a member with an annotation with the simple name `Field`, `Occurs`, or `Group` from a different package, and the member has no CopyMapper annotation.
- **Class:** a class that the processor maps has an annotation with the simple name `SubTypes` or `Defaults` from a different package, and no CopyMapper annotation with the same simple name.

The message gives the two qualified names, for example: `org.springframework.data.mongodb.core.mapping.Field is not pt.dazz.copymapper.annotation.Field. Change the import.`
A member or a class with both annotations (the CopyMapper annotation and the other annotation) is valid.

The check applies only to the classes that the processor maps, because only these classes go into a layout. The processor still finds the classes from `@Operation`. A wrong import of `@Defaults` in a `package-info.java` file is not checked.

### 5.3 IDE settings

The wiki page `Conventions` tells how to exclude a conflicting name from the auto-import of the IDE: IntelliJ IDEA (**Settings** > **Editor** > **General** > **Auto Import** > **Exclude from auto-import**) and Eclipse (**Type Filters**).

## 6. Version and commits

The release is `0.28.0`. The tag action calculates the version from the commit messages: `feat:` gives a minor version, and `BREAKING CHANGE` gives a major version.
In a `0.x` version, SemVer lets a minor version break the API.

- Use `feat:` for the commits that change a name a user sees. Use `refactor:`, `test:`, `docs:`, or `build:` for the other commits.
- Do not write `BREAKING CHANGE` or `BREAKING-CHANGE` in a commit message, and do not use `!` after the type. Describe the migration in the body of the commits and in the pull request.
- Before the pull request, search all commit messages of the branch for these words.

**CAUTION:** The first release with `pt.dazz.copymapper` stays on Maven Central for ever. Make sure that the coordinates are correct before the merge.

## 7. Migration and documentation

### 7.1 The migration page

Add the wiki page `docs/wiki/Migrate-to-CopyMapper.md`. Add it to `_Sidebar.md`. It contains:

- The reason in section 1.
- The new dependency coordinates, for Gradle and for Maven.
- The table of section 4.2, the table of section 4.3, and the table of section 4.4.
- Commands that replace the old names in a project, for the imports, the annotations, the Spring Boot keys, and the plugin declarations. Replace `@BookCollection` with `@Occurs`, not with `@Collection`.
- Two notes: the startup error of section 5.1, and the compile error of section 5.2.

The README and the wiki page `Home` link to this page.
The release notes of `0.28.0` come from the commit subjects. Thus, the subject of one `feat:` commit names the page, for example `feat: rename the library to CopyMapper (see the wiki page Migrate to CopyMapper)`.

### 7.2 Verify an artifact

The README and `RELEASING.md` tell how to verify an attestation. After this change there are three groups of releases:

| Releases | Download path | Command |
|---|---|---|
| `0.26.0` and earlier | `pt/dazz/bookmap/bookmap-core/…` | `--owner ajlima --signer-workflow ajlima/bookmap/.github/workflows/release.yml` |
| `0.27.0` | `pt/dazz/bookmap/bookmap-core/…` | `--repo dazz-pt/copymapper --signer-workflow dazz-pt/copymapper/.github/workflows/release.yml` |
| `0.28.0` and later | `pt/dazz/copymapper/copymapper-core/…` | `--repo dazz-pt/copymapper --signer-workflow dazz-pt/copymapper/.github/workflows/release.yml` |

The command for `0.27.0` was tested on 2026-11-16 with `bookmap-core-0.27.0.jar`. It passed. The command with `--repo ajlima/bookmap` failed.

## 8. Constraints

- Follow `docs/wiki/Contributing.md`: 100% line and branch coverage, no package cycle, the allowed packages, the complexity limit, and the mutation score of each module.
- The rules of `ArchitectureTest`, the PIT `targetClasses`, and the coverage rules must use the new packages. A rule with the old package name passes with nothing to check. Compare the number of tests, the coverage, and the mutation counts before and after the change.
- `scripts/set-readme-version.sh` must find the new coordinates and the new plugin id. Run it locally with a test version, and then revert the change.
- `scripts/update-verification-metadata.sh` uses the folder of the standalone Gradle example. Update the path.
- Run `./gradlew publishToMavenLocal`. Examine the group, the artifact names, the Gradle plugin marker, and the POM fields `url` and `scm`.
- Build the two standalone examples.

## 9. Out of scope

These items need an action of the author outside the repository, after the merge:

- The repository description on GitHub starts with "BookMap:". Change it to "CopyMapper:".
- The four open Dependabot pull requests (#127 to #130) change `gradle/libs.versions.toml` and two workflows. The rename does not change these lines, so the pull requests do not conflict. They need their own verification metadata, as before.

## 10. The check at the end

A search for `bookmap` without case (`git grep -i bookmap` and `git ls-files | grep -i bookmap`) finds only these items:

- This design document.
- The migration page `docs/wiki/Migrate-to-CopyMapper.md`.
- The rows and the commands for `0.27.0` and earlier in the section "Verify an artifact" of the README.
- One sentence in `RELEASING.md` that gives the old group of the releases until `0.27.0`.
- One sentence in the README and in the wiki page `Home` that gives the old name and links to the migration page. The link to the migration page in `Home` also gives the old name.
- The check of section 5.1, its tests, and the sentence about it in the wiki page `Spring-Boot`.

The folders `build/` are not in git. Ignore them.

## 11. Left to the builder

- The order of the commits, inside the rules of section 6.
- The Spring mechanism of section 5.1.
- The tool that replaces the names, if the result passes the check of section 10 and the build.
- The exact width of the new logo.
