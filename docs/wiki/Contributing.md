This page tells how to build BookMap, how to propose a change, and how a release occurs.

## Build

Install JDK 21. The build uses a Java 21 toolchain, and it does not download a JDK. The Gradle wrapper uses Gradle 9.9.1.

```bash
./gradlew build
```

The build runs all tests. It fails if the line or branch coverage of a module is less than 100%. The module `bookmap-examples` is not included in this rule.

Run the tests of one module:

```bash
./gradlew :bookmap-core:test
```

Each module writes a JaCoCo coverage report in `build/reports/jacoco/` (HTML and XML).

Build the standalone example of the Gradle plugin:

```bash
./gradlew -p bookmap-examples-gradle-plugin build
```

Build the standalone example of the Maven plugin. It uses the snapshot version in the local Maven repository:

```bash
./gradlew publishToMavenLocal
```

```bash
bookmap-examples-maven-plugin/mvnw -B -f bookmap-examples-maven-plugin/pom.xml verify
```

Build and run the native image smoke test. It needs GraalVM for JDK 21. Set `GRAALVM_HOME` to its directory:

```bash
GRAALVM_HOME=/path/to/graalvm ./gradlew -p bookmap-native-test nativeRun
```

The program runs one operation through the simulator transport, with the EBCDIC charset `IBM1047`.
It fails if the native image does not contain the charset.

The `build` check of a pull request runs all these builds, except the native image.
The `native-image` check of the workflow `.github/workflows/native-image.yml` runs the native image.
`./gradlew build` needs no GraalVM.

### Quality checks

`./gradlew build` also runs these checks:

| Check | Tool | Rule | Report |
|---|---|---|---|
| Dependency structure | ArchUnit, in the test `ArchitectureTest` of a module | A module must not have a package cycle. `bookmap-core`, `bookmap-processor`, and `bookmap-copybook` must depend only on a list of allowed packages. | The test report. |
| Cyclomatic complexity | PMD, rule `CyclomaticComplexity` in `config/pmd/complexity.xml` | The complexity of a method in the main code must not be more than 35. | `build/reports/pmd/` of each module. |
| Module size | The task `moduleSize` | No limit. | `build/reports/module-size.md`. |

The module `bookmap-examples` is not included in these checks.

The rule for allowed packages makes sure that `bookmap-core` and `bookmap-processor` do not depend on Spring, Quarkus, Micronaut, Guice, or a middleware library.
`ArchitectureTest` ignores two known cycles: `annotation` and `convert` in `bookmap-core`, and `ast` and `parse` in `bookmap-copybook`.
Each exception ignores only one dependency. A new cycle fails the test.

Run the complexity check or the size report alone:

```bash
./gradlew pmdMain
```

```bash
./gradlew moduleSize
```

### Mutation tests

The mutation tests are slow, so `./gradlew build` does not run them. Run them with PIT:

```bash
./gradlew mutationTest
```

The task runs PIT on `bookmap-core`, `bookmap-processor`, and `bookmap-copybook`.
It fails if the mutation score of a module is less than 95%. The minimum score of each module is in `build.gradle.kts`.
Each module writes a PIT report in `build/reports/pitest/` (HTML and XML).
The `mutation-test` check of the workflow `.github/workflows/mutation.yml` runs the mutation tests.

### Pull requests that change only documentation

A pull request that changes only documentation files does not build the modules. A documentation file is one of these files:

- A Markdown file (`*.md`) in any folder, for example `README.md` or `ROADMAP.md`.
- A file in the folder `docs/`.

In this case, the `build`, `native-image`, and `mutation-test` checks skip the Gradle steps and pass.
The `build` check always runs the commit author check.
A push to `main` always runs the full build, because the release needs it.
The script `scripts/docs-only-change.sh` finds the changed files. The checks run the script of `main`, so a pull request cannot change the rule for itself.

**CAUTION:** Do not add `paths-ignore` to a workflow of a required check. If the workflow does not run, the check does not report, and the pull request stays blocked.

### Dependency checksums

The Gradle builds verify the SHA-256 checksum of each downloaded plugin and library.
The checksums are in `gradle/verification-metadata.xml` and in `bookmap-examples-gradle-plugin/gradle/verification-metadata.xml`.
The build fails if a dependency has no checksum or a different checksum.

When you add, remove, or update a Gradle dependency, write the checksums again:

```bash
scripts/update-verification-metadata.sh
```

The script downloads all dependencies into a temporary Gradle user home. Then it writes the two files again.
Read the diff of the two files, and commit them with the dependency change.
Do this also on the branch of a Dependabot pull request for Gradle.
For more information, see "Supply-chain protection" in [RELEASING.md](https://github.com/ajlima/bookmap/blob/main/RELEASING.md).

## Rules for the code

- Write all code, comments, Javadoc, and messages in English.
- Do not use reflection at runtime. Use compile-time code generation.
- Use JavaPoet for code generation in the annotation processor.
- The core and processor modules must not depend on Spring, Quarkus, Micronaut, Guice, or any middleware library.
- Write examples and test data in English. Use an international business domain, for example orders, invoices, or shipments.
- Do not use identifiers that are specific to one country, for example a national tax ID.
- Keep the line and branch coverage of each module at 100%.

## Rules for the documentation

Write all documentation in English, in ASD-STE100 (Simplified Technical English) style. Follow these rules for approximately 80% of the text:

- Use short sentences: 20 words maximum for an instruction, 25 words maximum for a description.
- Write one instruction in each sentence. Use the imperative form.
- Use the active voice.
- Use one word for one meaning. For example, always write "field", never "attribute", for the same thing.
- Use only simple verb forms. Do not use "-ing" forms as verbs.
- Use articles ("a", "an", "the") where possible.
- Do not use a phrasal verb when a single verb is available ("remove", not "take out").
- Write one topic in each paragraph, with six sentences maximum.
- Write a warning or a caution as a clear command, followed by the risk.

You can use technical names as they are: class names, annotation names, and COBOL terms such as `PIC`, `COMP-3`, and `OCCURS DEPENDING ON`.

## Design documents

Write a design spec before a large change. Put it in `docs/design/`, with the name `YYYY-MM-DD-<topic>-design.md`.

Put a `Status:` line under the `Date:` line of the spec. When the feature is released, change the status to `implemented in vX.Y.Z.`

Start with the main design: [`2026-11-10-bookmap-design.md`](https://github.com/ajlima/bookmap/blob/main/docs/design/2026-11-10-bookmap-design.md).

## Branches and pull requests

1. Open an issue, or select an issue from the [Roadmap](Roadmap).
2. Create a branch from `main`.
3. Commit often on the branch.
4. Open a pull request to `main`. The `build` check must pass.
5. The author approves and merges the pull request.

A ruleset on `main` blocks direct pushes and force pushes.
Use a merge commit or a rebase merge. Do not use a squash merge: the release version comes from each commit message.

## Commit messages

The commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):

| Type | Release |
|---|---|
| `feat:` | A new minor version. |
| `fix:` | A new patch version. |
| `BREAKING CHANGE` in the commit body | A new major version. |
| `docs:`, `test:`, `build:`, `refactor:`, `ci:` | No release. |

### Commit authors

Commit with your own git identity. Set `git config user.name` and `git config user.email` before the first commit.
Do not use an AI assistant as the author, the committer, or a co-author. Do not add a `Co-Authored-By` line or a "Generated with" line for an AI assistant.
The `build` check runs `scripts/check-commit-authors.sh`. The check fails if a commit breaks this rule.

**CAUTION:** Set the git identity in each new environment, for example a cloud session or a CI runner. The default identity of that environment is not your identity.

## Releases

GitHub Actions makes each release from `main`. It builds and tests the commit, publishes the modules to Maven Central, and creates the tag and the GitHub release.
See [RELEASING.md](https://github.com/ajlima/bookmap/blob/main/RELEASING.md) for the release cycle and the one-time setup.

### The release jobs

The workflow `.github/workflows/release.yml` has four jobs:

| Job | Runs on | Access | Result |
|---|---|---|---|
| `build` | Each pull request and each push to `main`. | Read-only. | The next version, the build, the tests, and the coverage check. |
| `publish` | A push to `main` with a new version. | Read-only. The `maven-central` environment gives the Sonatype token and the signing key. | The signed modules on Maven Central. |
| `attest` | After a successful `publish`. | `id-token: write` and `attestations: write`. No environment and no secret. | A build provenance attestation for each file on Maven Central. |
| `release` | After a successful `publish`. | `contents: write`. The `readme-update` environment gives the key of the release app. | The tag, the GitHub release, and the README version commit. |

These modules go to Maven Central: `bookmap-core`, `bookmap-processor`, `bookmap-transport-simulator`, `bookmap-transport-ctg`, `bookmap-transport-http`, `bookmap-transport-mq`, `bookmap-spring-boot`, `bookmap-copybook`, `bookmap-copybook-cli`, `bookmap-copybook-gradle-plugin`, and `bookmap-copybook-maven-plugin`.

### The release app

After a release, the `release` job sets the new version in the README files with `scripts/set-readme-version.sh`. Then it pushes the commit `docs: set the README version to X.Y.Z` to `main`.

The ruleset on `main` blocks direct pushes, also from the `GITHUB_TOKEN`. A repository of a personal account cannot add GitHub Actions to the bypass list of a ruleset.
Thus, the `release` job pushes with the token of a GitHub App that the owner of the repository creates: the release app.

| Item | Value |
|---|---|
| App permissions | **Contents: Read and write** only. No webhook. No events. |
| Installation | Only on the `bookmap` repository. |
| Ruleset | The app is a bypass actor of the ruleset "Protect main". |
| Environment | `readme-update`. Only the `release` job uses it. |
| Environment variable | `RELEASE_APP_CLIENT_ID`: the Client ID of the app. |
| Environment secret | `RELEASE_APP_PRIVATE_KEY`: the private key of the app. |
| Token | `actions/create-github-app-token` makes a token with only `contents: write`. The token is revoked at the end of the job. |

The tag and the GitHub release use the `GITHUB_TOKEN`. Only the README push uses the token of the app.
For the steps to create and install the app, see "6. Let the release app push the README update" in [RELEASING.md](https://github.com/ajlima/bookmap/blob/main/RELEASING.md).

**CAUTION:** Do not use the secret `RELEASE_APP_PRIVATE_KEY` in another job or another environment. The key can push to `main` through the bypass of the ruleset.

If the variable or the secret is missing, the release still completes. The `release` job then fails at the README update with a clear error.
In that case, set the version in a pull request:

```bash
scripts/set-readme-version.sh X.Y.Z
```

## Edit this wiki

The source of the wiki pages is the folder [`docs/wiki/`](https://github.com/ajlima/bookmap/tree/main/docs/wiki) in the main repository.
The Roadmap page comes from [`ROADMAP.md`](https://github.com/ajlima/bookmap/blob/main/ROADMAP.md).
A GitHub Action publishes them to the wiki after each push to `main` that changes `docs/wiki/` or `ROADMAP.md`. The Action deletes each wiki page that is not in `docs/wiki/`.

1. Change the files in `docs/wiki/` or `ROADMAP.md` on a branch.
2. Do not start a page with a level-1 title (`# Title`). The wiki shows the file name as the title. A file name such as `Getting-started.md` gives the title "Getting started".
3. Use a `docs:` commit.
4. Open a pull request to `main`.

**CAUTION:** Do not edit the pages in the wiki web editor. The next publication replaces all pages, and your change is lost.
