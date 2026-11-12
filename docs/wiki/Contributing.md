# Contributing

This page tells how to build BookMap, how to propose a change, and how a release occurs.

## Build

BookMap needs JDK 21 or later. The build uses the Gradle wrapper.

```bash
./gradlew build
```

The build runs all tests. It fails if the line or branch coverage of a module is less than 100%. The examples are not included in this rule.

Build the standalone example of the Gradle plugin:

```bash
./gradlew -p bookmap-examples-gradle-plugin build
```

## Rules for the code

- Write all code, comments, Javadoc, and messages in English.
- Do not use reflection at runtime. Use compile-time code generation.
- Use JavaPoet for code generation in the annotation processor.
- The core and processor modules must not depend on Spring, Quarkus, Guice, or any middleware library.
- Write examples and test data in English. Use an international business domain, for example orders, invoices, or shipments.

## Rules for the documentation

Write all documentation in English, in ASD-STE100 (Simplified Technical English) style:

- Use short sentences: 20 words maximum for an instruction, 25 words maximum for a description.
- Write one instruction in each sentence. Use the imperative form.
- Use the active voice.
- Use one word for one meaning.

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
A GitHub Action publishes them to the wiki after each merge to `main`.

1. Change the files in `docs/wiki/` or `ROADMAP.md` on a branch.
2. Use a `docs:` commit.
3. Open a pull request to `main`.

**CAUTION:** Do not edit the pages in the wiki web editor. The next publication replaces all pages, and your change is lost.
