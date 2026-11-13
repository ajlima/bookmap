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

The `build` check of a pull request runs all these builds.

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
- The core and processor modules must not depend on Spring, Quarkus, Guice, or any middleware library.
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

## Releases

GitHub Actions makes each release from `main`. It builds and tests the commit, publishes the modules to Maven Central, and creates the tag and the GitHub release.
See [RELEASING.md](https://github.com/ajlima/bookmap/blob/main/RELEASING.md) for the release cycle and the one-time setup.

### The release jobs

The workflow `.github/workflows/release.yml` has three jobs:

| Job | Runs on | Access | Result |
|---|---|---|---|
| `build` | Each pull request and each push to `main`. | Read-only. | The next version, the build, the tests, and the coverage check. |
| `publish` | A push to `main` with a new version. | Read-only. The `maven-central` environment gives the Sonatype token and the signing key. | The signed modules on Maven Central. |
| `release` | After a successful `publish`. | `contents: write`. The `readme-update` environment gives the key of the release app. | The tag, the GitHub release, and the README version commit. |

These modules go to Maven Central: `bookmap-core`, `bookmap-processor`, `bookmap-transport-simulator`, `bookmap-spring-boot`, `bookmap-copybook`, `bookmap-copybook-cli`, `bookmap-copybook-gradle-plugin`, and `bookmap-copybook-maven-plugin`.

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
