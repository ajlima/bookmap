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
5. The `release` job sets the new version in `README.md`, `bookmap-copybook-gradle-plugin/README.md`, and `bookmap-copybook-maven-plugin/README.md` with `scripts/set-readme-version.sh`. Then it pushes the commit `docs: set the README version to X.Y.Z` to `main`. A `docs:` commit makes no release.

The `release` job runs only after a successful `publish` job. Thus, each GitHub release is also on Maven Central.

These modules go to Maven Central with the group `pt.dazz.bookmap`:
`bookmap-core`, `bookmap-processor`, `bookmap-transport-simulator`, `bookmap-spring-boot`,
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

## Supply-chain protection

- All GitHub Actions are pinned to a full commit SHA. Dependabot updates the pins every week.
- The Gradle wrapper checks the SHA-256 of the Gradle distribution (`distributionSha256Sum`).
  The `setup-gradle` action also validates the wrapper JAR.
- Only the `publish` job sees the Maven Central token and the signing key, through the `maven-central` environment.
- Only the `release` job sees the private key of the release app, through the `readme-update` environment. The app token has only the `contents: write` permission, and it is valid for one hour at most.
- The `setup-gradle` cache is disabled. Since v6, that cache is a proprietary component with separate terms of use.
  The `build` job uses the open-source `setup-java` cache instead.
  The `publish` job uses no cache, so a release always builds from clean dependencies.
- Not done yet: Gradle dependency verification (`gradle/verification-metadata.xml`). It pins the checksum of each plugin and library.
  Add it when the dependency set is stable, because each Dependabot update then needs a new checksum.
