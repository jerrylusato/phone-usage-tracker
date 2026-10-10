# Releasing Phone Usage

Source and releases are public under the MIT license. The shared website is
https://usage.jerrylusato.com; it discovers published release packages automatically.

## Checks and versioning

Pull requests and pushes to `main` run unit tests, Android lint, and a debug build on
Ubuntu with Java 21, explicitly provisioned Android SDK 36, and Build Tools 36.1.0.
Actions are pinned to commit revisions, including Gradle wrapper validation. Dependabot maintains action dependencies.
Signing secrets are only used by the tag release workflow, never by pull requests.

Update `versionName` and increment `versionCode` in `app/build.gradle.kts`, update the
changelog, and merge the verified change before tagging. The initial public version is
1.1.1 (build 3); earlier build numbers are not represented as invented release entries.

```sh
python3 scripts/release.py --validate-only
git tag v1.1.1
git push origin v1.1.1
```

Use the new version's tag for later releases. Tags must match the configured version.
Do not move published tags or replace released assets. A failed run before publication
can be rerun on the same tag. `workflow_dispatch` also supports rerunning a tag; selecting
a branch deliberately skips the release job. Existing published releases cause a rerun
to fail rather than overwrite downloads. Publish fixes as new versions/build codes.

## Signing credentials

Keep the existing Android signing key: replacing it prevents normal upgrades of installed
copies. Local release builds still read gitignored `keystore.properties` and the keystore.
A release build now fails when signing configuration is missing; it no longer silently
produces an unsigned package. CI accepts environment variables with the same values.

Configure these encrypted GitHub Actions repository secrets through `gh secret set` or
GitHub Settings → Secrets and variables → Actions:

- `ANDROID_KEYSTORE_BASE64`: base64 of the existing keystore.
- `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

The workflow restores the keystore under the runner's temporary directory with restricted
permissions and removes it in an always-run cleanup step. Keep a separate secure backup.
Never commit signing files or paste their contents into issues, logs, or release notes.
Review changes to release workflows carefully because they can access these secrets.

## Release outputs

The release reruns tests/lint, builds the signed APK, verifies its signing certificate,
checks the actual package version/application ID against Gradle metadata and the tag,
and rejects an APK declaring `android.permission.INTERNET`.

Published assets are `phone-usage-<version>.apk`, `SHA256SUMS`, and
`release-manifest.json` (version, build number, source commit, filenames, sizes, SHA-256).
GitHub issues a build provenance attestation for the APK. Verify downloaded assets with:

```sh
sha256sum -c SHA256SUMS
gh attestation verify phone-usage-1.1.1.apk --repo jerrylusato/phone-usage-tracker
```

GitHub Releases provide permanent download URLs; Actions artifacts are for verification,
not the public download channel. Android updates remain manual APK installations.
Download the newer APK and install over the existing app to retain local history.
