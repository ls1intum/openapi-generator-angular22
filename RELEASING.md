# Releasing

This generator is published to **Maven Central** (the Sonatype Central Portal). Consumers — Artemis and
any other project — then resolve it from a plain `mavenCentral()` repository, with **no authentication**:

```kotlin
repositories { mavenCentral() }
dependencies { /* openapiGenerator | classpath */ "de.tum.cit.aet:openapi-generator-angular22:4.0.0" }
```

Publishing is automated by [`.github/workflows/build.yml`](.github/workflows/build.yml): pushing a tag
that starts with `v` (e.g. `v1.0.0`) runs the `publish` job, which uploads the GPG-signed artifacts to
the Central Portal via the [`com.vanniktech.maven.publish`](https://vanniktech.github.io/gradle-maven-publish-plugin/)
plugin and (because `automaticRelease = true`) releases the deployment once it validates.

## One-time setup

These steps are done **once** by a maintainer/org admin; afterwards every release is just a tag push.

### 1. Claim the `de.tum.cit.aet` namespace on the Central Portal

1. Sign in at <https://central.sonatype.com> (GitHub or email).
2. Add the namespace `de.tum.cit.aet` and verify ownership of the `tum.de` domain by publishing the
   **DNS `TXT` record** the portal shows you (a one-off verification token).
   - This requires access to the `tum.de` DNS zone. If that is not available, the simplest alternative
     is to switch the `group` to `io.github.ls1intum` (GitHub-verified, no DNS needed) — but that
     changes the artifact coordinates everywhere, so the `tum.de` namespace is preferred.

### 2. Generate a Central Portal user token

In the Central Portal → *Account* → *Generate User Token*. This yields a username/password pair used
for the upload (it is **not** your login password).

### 3. GPG signing key — reuse the shared `ls1intum` org key

The GPG signing key is **not** repo-specific: this repo reuses the organisation-level secrets
`GPG_KEY` (ASCII-armored private key) and `GPG_PASSPHRASE`, the same key Helios and the other
`ls1intum` publishers sign with (`ls1intum/Helios/.github/workflows/release-maven.yml` is the
reference). An org owner grants a new repo access under
`Organization → Settings → Secrets and variables → Actions → GPG_KEY / GPG_PASSPHRASE → Repository access`
(or `gh api -X PUT orgs/ls1intum/actions/secrets/GPG_KEY/repositories/<repo_id>`).

Only create a fresh key if you deliberately want a separate signing identity (`gpg --gen-key`, publish
the public key to a keyserver, export it with `gpg --armor --export-secret-keys <KEY_ID>`, and store it
as repo secrets instead).

### 4. Add the two Central Portal secrets

The **Central Portal auth** is repo-specific (a token for the `de.tum.cit.aet` namespace). Add it under
`Settings → Secrets and variables → Actions → New repository secret`:

| Secret                   | Scope | Value                                                     |
|--------------------------|-------|-----------------------------------------------------------|
| `MAVEN_CENTRAL_USERNAME` | repo  | Central Portal user-token **username**                    |
| `MAVEN_CENTRAL_PASSWORD` | repo  | Central Portal user-token **password**                    |
| `GPG_KEY`                | org   | shared ASCII-armored GPG **private** key (granted, step 3)|
| `GPG_PASSPHRASE`         | org   | passphrase for that key (granted, step 3)                 |

The workflow maps these onto the Gradle properties the plugin reads
(`ORG_GRADLE_PROJECT_mavenCentralUsername`, `…Password`, `…signingInMemoryKey`, `…signingInMemoryKeyPassword`).

## Cutting a release

1. Bump `version` in [`build.gradle.kts`](build.gradle.kts) and the version references in
   [`README.md`](README.md). Use a non-`SNAPSHOT` version.
2. Add `release-notes/v<version>.md` with the changes and migration steps. CI uses this file as the GitHub Release body.
3. Run `./gradlew clean build generateExample --rerun-tasks`, `bash scripts/test-verify-release.sh` and
   `bash scripts/verify-release.sh v<version>`. The release check requires non-empty notes and a tag that matches a
   non-snapshot Gradle version.
4. Merge to `main`, then tag and push:
   ```bash
   git tag v4.0.0
   git push origin v4.0.0
   ```
5. The `publish` job checks the tag and notes before it signs and uploads to the Central Portal and creates a GitHub
   Release. The artifact appears on Maven Central within ~15–30 minutes (search index can lag a few hours).

## Releasing locally (fallback)

If you ever need to publish without CI, provide the same four values as Gradle properties (e.g. in
`~/.gradle/gradle.properties`: `mavenCentralUsername`, `mavenCentralPassword`, `signingInMemoryKey`,
`signingInMemoryKeyPassword`) and run:

```bash
./gradlew publishToMavenCentral --no-configuration-cache
```

## Building without releasing

`./gradlew publishToMavenLocal` installs the artifacts into your local `~/.m2` repository and **requires
no signing key** (signing is gated on the key being present). This is the path downstream projects use to
build the generator from source when they want to regenerate their client before a version is on Central.
