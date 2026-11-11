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
4. The `release` job creates the tag `vX.Y.Z` and a GitHub release. It attaches the same JAR files that went to Maven Central.
5. The `release` job sets the new version in `README.md` with `scripts/set-readme-version.sh`. Then it pushes the commit `docs: set the README version to X.Y.Z` to `main`. A `docs:` commit makes no release.

The `release` job runs only after a successful `publish` job. Thus, each GitHub release is also on Maven Central.

These modules go to Maven Central with the group `pt.dazz.bookmap`:
`bookmap-core`, `bookmap-processor`, `bookmap-transport-simulator`, and `bookmap-spring-boot`.

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

### 6. Let GitHub Actions push the README update

The ruleset on `main` blocks direct pushes. Add GitHub Actions to its bypass list, so that the `release` job can push the README update:

1. Open **Settings** > **Rules** > **Rulesets** > **Protect main**.
2. In **Bypass list**, click **Add bypass**.
3. Select **GitHub Actions**, with the mode **Always allow**.
4. Click **Save changes**.

Caution: Each workflow job with `contents: write` can then push to `main`. Keep `contents: read` as the default permission of all workflows.

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

## Supply-chain protection

- All GitHub Actions are pinned to a full commit SHA. Dependabot updates the pins every week.
- The Gradle wrapper checks the SHA-256 of the Gradle distribution (`distributionSha256Sum`).
  The `setup-gradle` action also validates the wrapper JAR.
- Only the `publish` job sees the Maven Central token and the signing key, through the `maven-central` environment.
- The `setup-gradle` cache is disabled. Since v6, that cache is a proprietary component with separate terms of use.
  The `build` job uses the open-source `setup-java` cache instead.
  The `publish` job uses no cache, so a release always builds from clean dependencies.
- Not done yet: Gradle dependency verification (`gradle/verification-metadata.xml`). It pins the checksum of each plugin and library.
  Add it when the dependency set is stable, because each Dependabot update then needs a new checksum.
