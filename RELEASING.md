# Releasing BookMap

GitHub Actions makes each release. Read this file to set up the release and to understand the cycle.

## How a release occurs

The workflow `.github/workflows/release.yml` runs on each push to `main`.

1. The `build` job calculates the next version from the commit messages since the last tag:
   - `feat:` increases the minor version.
   - `fix:` increases the patch version.
   - `BREAKING CHANGE` in the commit body increases the major version.
   - Other types (`docs:`, `test:`, `build:`, `refactor:`, `ci:`) make no release.
2. The `build` job builds and tests with that version. The build fails if coverage is less than 100%.
3. The `publish` job checks out the same commit, signs the modules, and publishes them to Maven Central.
4. The `attest` job makes a build provenance attestation for each JAR, POM, and Gradle module file that went to Maven Central. See [Verify an artifact](#verify-an-artifact).
5. The `release` job creates the tag `vX.Y.Z` and a GitHub release. It attaches the same JAR files that went to Maven Central.
6. The `release` job sets the new version in `README.md`, `bookmap-copybook-gradle-plugin/README.md`, and `bookmap-copybook-maven-plugin/README.md` with `scripts/set-readme-version.sh`. Then it pushes the commit `docs: set the README version to X.Y.Z` to `main`. A `docs:` commit makes no release.

The `release` job runs only after a successful `publish` job. Thus, each GitHub release is also on Maven Central.
The `release` job does not wait for the `attest` job. If the `attest` job fails, run the failed job again from the workflow run page.
Do this before the end of the 7-day retention of the `maven-central-files` artifact.

These modules go to Maven Central with the group `pt.dazz.bookmap`:
`bookmap-core`, `bookmap-processor`, `bookmap-transport-simulator`, `bookmap-transport-ctg`, `bookmap-spring-boot`,
`bookmap-copybook`, `bookmap-copybook-cli`, `bookmap-copybook-gradle-plugin`, and `bookmap-copybook-maven-plugin`.
The examples are not published.

## One-time setup

Do these steps before the first release. The `publish` job stops with an error if a secret is missing.

### 1. Create a Sonatype Central account

1. Go to <https://central.sonatype.com> and create an account.
2. Open **Namespaces** and add the namespace `pt.dazz`.
3. Sonatype shows a verification key. Add a DNS `TXT` record with this key to the domain `dazz.pt`.
4. Wait until the namespace shows **Verified**.

### 2. Create a Sonatype user token

1. Open **Account** > **Generate User Token**.
2. Keep the token username and the token password. You need them in step 4.

### 3. Create a GPG signing key

Maven Central accepts only signed artifacts.

```bash
gpg --full-generate-key
```

Use RSA 4096 and a strong passphrase. Then find the key ID:

```bash
gpg --list-secret-keys --keyid-format=long
```

Send the public key to a key server, so that Maven Central can verify the signatures:

```bash
gpg --keyserver hkps://keys.openpgp.org --send-keys <KEY_ID>
```

The key server sends a verification email to the address of the key. Click the link in that email.
Without this step, the server publishes the key without your name and email.

If `keys.openpgp.org` is not available, use `hkps://keyserver.ubuntu.com:443`.
Use the `hkps://` form: some networks block the default HKP port 11371.

### 4. Create the `maven-central` environment

The `publish` job uses the GitHub environment `maven-central`. Its secrets are visible only to that job.

```bash
gh api --method PUT repos/ajlima/bookmap/environments/maven-central
```

Optional: add yourself as a required reviewer. Then each publication waits for your approval.
Open **Settings** > **Environments** > **maven-central** > **Required reviewers**.

### 5. Add the environment secrets

Run these commands in the repository folder. Each command asks for the value. Do not put a secret value on the command line.

```bash
gh secret set MAVEN_CENTRAL_USERNAME --env maven-central
```

```bash
gh secret set MAVEN_CENTRAL_PASSWORD --env maven-central
```

```bash
gh secret set SIGNING_KEY_PASSWORD --env maven-central
```

For the signing key, send the exported private key directly to the secret:

```bash
gpg --armor --export-secret-keys <KEY_ID> | gh secret set SIGNING_KEY --env maven-central
```

### 6. Let the release app push the README update

The ruleset on `main` blocks direct pushes, also from the `GITHUB_TOKEN`.
A repository of a personal account cannot add GitHub Actions to the bypass list. Thus, the `release` job pushes with the token of a GitHub App that you own.

Create the app:

1. Open **Settings** (of your account) > **Developer settings** > **GitHub Apps** > **New GitHub App**.
2. Set **GitHub App name** to a unique name, for example `bookmap-release-<your-login>`.
3. Set **Homepage URL** to `https://github.com/ajlima/bookmap`.
4. In **Webhook**, clear **Active**.
5. In **Repository permissions**, set **Contents** to **Read and write**. Keep all other permissions at **No access**.
6. In **Where can this GitHub App be installed?**, select **Only on this account**.
7. Click **Create GitHub App**.
8. Copy the **Client ID**. You need it in step 13.
9. In **Private keys**, click **Generate a private key**. The browser downloads a `.pem` file.

Install the app on the repository:

10. In the app settings, open **Install App**, and click **Install** next to your account.
11. Select **Only select repositories**, select `bookmap`, and click **Install**.

Add the app to the bypass list of the ruleset:

12. Open **Settings** > **Rules** > **Rulesets** > **Protect main**. In **Bypass list**, click **Add bypass**, select the app in **Apps**, and click **Save changes**.

Give the app to the `release` job. The `readme-update` environment holds the settings, so only the `release` job can read them:

13. Create the environment, then set the Client ID as an environment variable:

```bash
gh api --method PUT repos/ajlima/bookmap/environments/readme-update
```

```bash
gh variable set RELEASE_APP_CLIENT_ID --env readme-update --body "<client-id>"
```

14. Send the private key directly to the environment secret:

```bash
gh secret set RELEASE_APP_PRIVATE_KEY --env readme-update < <app-name>.<date>.private-key.pem
```

15. Delete the `.pem` file from your computer.

Caution: Each workflow that has the private key can push to `main`. Do not use the secret in another job or another environment.
Do not give the app more permissions than **Contents: Read and write**.

Without this step, the release still completes, but the `release` job fails at the README update. Then update the README in a pull request:

```bash
scripts/set-readme-version.sh X.Y.Z
```

## Make a release

Open a Pull Request to `main`. The `build` check must pass before the merge.
Merge a commit with the type `feat:` or `fix:` into `main`. The workflow does the rest.

Use a merge commit or a rebase merge. Do not use a squash merge.
A squash merge replaces the commit messages with one message, and the version comes from the commit messages.

To run the workflow again (for example, after you add a missing secret), open **Actions** > **Build and release** > **Run workflow** on the `main` branch.

## Local check

Run this command to make the release artifacts in the local Maven repository, without a signature:

```bash
./gradlew publishToMavenLocal -PreleaseVersion=0.0.0-local
```

## Verify an artifact

Each release after `0.6.0` has a build provenance attestation (SLSA) for each JAR, POM, and Gradle module file on Maven Central.
The JAR files of the GitHub release are the same files, so the same attestation applies to them.
For the user instructions, see "Verify an artifact" in the [README](README.md#verify-an-artifact).

## Supply-chain protection

- All GitHub Actions are pinned to a full commit SHA. Dependabot updates the pins every week.
- The Gradle wrapper checks the SHA-256 of the Gradle distribution (`distributionSha256Sum`).
  The `setup-gradle` action also validates the wrapper JAR.
- Only the `publish` job sees the Maven Central token and the signing key, through the `maven-central` environment.
- Only the `attest` job can get an OIDC token (`id-token: write`). It has no environment and no secret, and it runs no build.
  It attests the files in the staging directory of the publish plugin, so the attested bytes are the bytes on Maven Central.
- Only the `release` job sees the private key of the release app, through the `readme-update` environment. The app token has only the `contents: write` permission, and it is valid for one hour at most.
- The `setup-gradle` cache is disabled. Since v6, that cache is a proprietary component with separate terms of use.
  The `build` job uses the open-source `setup-java` cache instead.
  The `publish` job uses no cache, so a release always builds from clean dependencies.
- Gradle dependency verification pins the SHA-256 checksum of each plugin, library, POM, and Gradle module file that the build downloads.
  The file `gradle/verification-metadata.xml` applies to the root build: `build`, `publishToMavenLocal`, and `publishAndReleaseToMavenCentral`.
  The standalone example `bookmap-examples-gradle-plugin` is a separate root build, so it has its own file `bookmap-examples-gradle-plugin/gradle/verification-metadata.xml`.
  The build fails if a downloaded file has a different checksum, or if a file has no checksum.
  The Javadoc JARs and the source JARs are trusted without a checksum: an IDE downloads them, and the build does not run them.
  Gradle verification does not cover the Maven wrapper and the dependencies of `bookmap-examples-maven-plugin`.
  It also does not cover the Maven distributions that the integration tests of `bookmap-copybook-maven-plugin` download.

### Update the dependency checksums

Each change to a Gradle dependency needs new checksums. If a Dependabot pull request for Gradle does not change the verification files, its `build` check fails.
Update the checksums on the branch of the pull request:

```bash
gh pr checkout <PR_NUMBER>
```

```bash
scripts/update-verification-metadata.sh
```

The script removes all checksums from the two verification files. Then it runs the builds that write the checksums again.
The builds use a temporary, empty Gradle user home. Thus, Gradle downloads each file again, also each POM file and each Gradle module file.
The `publish` job also starts with an empty cache, so it needs the checksums of all these files.

1. Read the diff of the two files. Make sure that only the changed dependencies have new checksums.
2. Commit the files with a `build:` commit, for example `build: update the dependency checksums`.
3. Push the commit to the branch of the pull request.

**CAUTION:** Do not run the script on a computer that you do not trust. Gradle writes the checksums of the files that it downloads, so a changed file gets a pinned checksum.
