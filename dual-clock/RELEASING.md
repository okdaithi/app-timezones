# Releasing

Signed releases let users update the app in place. Android installs an update over an existing app only if both of these hold:
- **Same signing certificate:** every release is signed with one release key.
- **`versionCode` not lower:** the version is derived from the tag, so each tag must be higher than the last.

## One-time setup

Do this once, by hand. The key must never be committed or regenerated.

1. **Generate the release key:**
   ```sh
   keytool -genkeypair -v -keystore dualclock-release.jks -keyalg RSA -keysize 4096 \
     -validity 36500 -alias dualclock
   ```
2. **Back it up offline:** store the `.jks` file and both passwords somewhere outside GitHub. If they are lost, no future release can be installed over the app, and every user has to uninstall and reinstall.
3. **Read the certificate fingerprint:**
   ```sh
   keytool -list -v -keystore dualclock-release.jks -alias dualclock \
     | sed -nE 's/^\s*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f'
   ```
4. **Add repository secrets** under Settings → Secrets and variables → Actions → Secrets:

   | Secret | Value |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | `base64 -w0 dualclock-release.jks` (on macOS, `base64 -i dualclock-release.jks`) |
   | `SIGNING_STORE_PASSWORD` | Keystore password |
   | `SIGNING_KEY_ALIAS` | `dualclock` |
   | `SIGNING_KEY_PASSWORD` | Key password |

5. **Add a repository variable** on the Variables tab: `RELEASE_CERT_SHA256` = the fingerprint from step 3. It has 64 lowercase hex characters and no colons.

## Cutting a release

1. **Check CI:** the commit must be green on the `dual-clock` workflow, which runs the emulator checks and the upgrade test.
2. **Tag and push:**
   ```sh
   git tag v1.0.0 <commit>
   git push origin v1.0.0
   ```
3. **The `dual-clock-release` workflow takes over:**
   - It runs the forbidden-API guard and `:core:test`.
   - It builds `assembleRelease` with `RELEASE_TAG` set.
   - It verifies the APK's signature against `RELEASE_CERT_SHA256`.
   - It compares against the previous release: the certificate must match, and the `versionCode` must be higher.
   - It publishes a GitHub Release with `dual-clock.apk` attached.

The newest release is always at:

```
https://github.com/okdaithi/app-timezones/releases/latest/download/dual-clock.apk
```

## Versioning

The tag `vMAJOR.MINOR.PATCH` sets `versionName` to `MAJOR.MINOR.PATCH` and `versionCode` to `MAJOR*10000 + MINOR*100 + PATCH`, so `v1.2.3` gives `10203`.
- `MINOR` and `PATCH` must be below 100.
- A tag that is not higher than the last release fails the version check.

Builds without a tag get `versionName` `0.0.0-dev` and `versionCode` 1. The CI upgrade test overrides this with `VERSION_CODE`.

## Upgrade test

On every CI run, the build job builds two versions on the same runner:
- **Previous:** the base commit (the PR base, or the parent commit on a push), as `versionCode` 1.
- **New:** this commit, as `versionCode` 2.

Both are signed with the runner's debug key. The emulator job then:
1. Installs the previous version, places the widget and saves non-default settings.
2. Installs the new version with `adb install -r`.
3. Checks that the version number went up, and that the app logged `refresh[MY_PACKAGE_REPLACED]`.
4. Checks that the settings and the placed widget survived (`WidgetHostTest#upgradeKeptState`).

Release builds differ from this test only in which key they are signed with.

## Moving existing installs to the release key

Anyone who installed an APK from a CI artifact has one signed with a throwaway debug key. Android can't move that install to the release key, so those users must uninstall once and then install the first signed release. After that, every release installs over the previous one.
