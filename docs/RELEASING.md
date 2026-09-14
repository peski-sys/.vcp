# Releasing

This process is for repository maintainers. Public release artifacts must be built by GitHub Actions from a reviewed version tag; do not upload a locally built APK as an official release.

## One-time signing setup

Create and securely back up a dedicated Android release keystore. The first publicly distributed signing certificate becomes the app's upgrade identity, so losing or replacing it prevents normal in-place upgrades outside a managed store-signing arrangement.

Create a GitHub Actions environment named `release`, add a required reviewer when the repository plan supports it, and configure these environment secrets:

| Secret | Value |
| --- | --- |
| `VCP_KEYSTORE_BASE64` | Base64-encoded release keystore file |
| `VCP_RELEASE_STORE_PASSWORD` | Keystore password |
| `VCP_RELEASE_KEY_ALIAS` | Signing-key alias |
| `VCP_RELEASE_KEY_PASSWORD` | Signing-key password |

Never store the keystore, encoded keystore, passwords, or a populated `local.properties` file in the repository.

## Prepare a release

1. Start from reviewed `main` with CI passing.
2. Update `vcp.versionCode` and `vcp.versionName` in `gradle.properties`.
3. Move the relevant entries from `[Unreleased]` into a dated changelog heading exactly matching the version name.
4. Run the full local verification command:

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
   ```

5. Commit the release metadata and let CI pass again.
6. Create and push an annotated tag matching the version name:

   ```bash
   git tag -a v0.4.0-alpha01 -m ".vcp 0.4.0-alpha01"
   git push origin v0.4.0-alpha01
   ```

The workflow rejects a tag that does not exactly match `vcp.versionName` or lacks a corresponding changelog heading.

## Review and publish

The release workflow creates a draft containing:

- `vcp-<version>.apk` for direct installation;
- `vcp-<version>.aab` for store submission; and
- `SHA256SUMS.txt` for artifact verification.

Before publishing the draft:

1. confirm the workflow's tests, lint, permission audit, and signature checks passed;
2. verify the APK checksum against `SHA256SUMS.txt`;
3. install the APK over the previous public version to confirm signing continuity;
4. run the supported manual conversion smoke test on a physical device; and
5. review generated release notes and mark pre-release versions appropriately.

Do not move or recreate a published version tag. Fix release mistakes with a new, higher version code and version name.
