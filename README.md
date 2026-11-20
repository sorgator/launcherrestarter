# Suntel MDM Update Helper

An Android companion that returns the operator to the Headwind MDM launcher after a launcher update. It supports the existing launcher handoff protocol; downloading and installing the launcher remain separate operations. Running the helper does not prove that an update succeeded.

## Compatibility contract

| Interface | Required behavior |
| --- | --- |
| Application ID | `com.hmdm.emuilauncherrestarter` |
| Entry activity | `com.hmdm.emuilauncherrestarter.MainActivity` |
| Intent extra | Optional string `oldVersion`, containing the previous launcher **versionName**, not its numeric version code |
| Launcher | Prefer `com.hmdm.launcher`; retain the legacy `ru.headwind.kiosk` fallback |

When `oldVersion` is supplied, wait for the installed launcher version to change before attempting the handoff. Handle a temporarily missing launcher with bounded retries. Without the extra, support returning to an already installed launcher. Preserve this interface when changing the helper implementation.

Version **1.05-suntel.1 / code 5** uses a platform `android.app.Activity` and a visible handoff screen instead of the old support-library/no-display activity. The exported entrypoint and two launcher package queries are explicit. Polling is bounded to two minutes and pauses when the activity loses focus or is paused, so it does not interrupt Android's installer. The selected launcher package stays fixed across temporary package disappearance and activity recreation. Recovery offers Retry or Open launcher. Physical device validation remains necessary; a higher target SDK alone is not compatibility evidence.

The legacy caller supplies only the previous `versionName`. Reinstalling the same name or changing only its numeric code cannot be detected automatically; use Open launcher after checking Android's installation result.

## Build and release

The pinned modernization baseline is AGP **8.13.2**, Gradle **8.13**, JDK **17**, compile SDK **35**, target SDK **35**, and minimum SDK **21**. Use the checked-in wrapper and an approved local Android SDK. The commands below are for the modernized build configuration.

```sh
export JAVA_HOME="<JDK_17_HOME>"
export ANDROID_HOME="<ANDROID_SDK_HOME>"
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --no-daemon :app:assembleRelease
```

Debug APKs are for development only. Production releases must use an approved release signer, never a debug certificate or credentials committed to this repository. Sign the unsigned release outside the source tree using protected tooling; let the signing tool prompt for secrets rather than putting passwords on the command line.

For the approved project-local dedicated signer, `tools/sign_release.py --build-tools <BUILD_TOOLS>` reads owner-only files in git-ignored `.signing/`, signs a fresh output, verifies it, and records its SHA256 in `outputs/`. It refuses to overwrite an existing release. Preserve the keystore and its separate protected credentials in your approved backup process: future updates require this same signer. Never commit `.signing/`, private backups, or credentials. The release Gradle build itself remains unsigned.

```sh
"<BUILD_TOOLS>/zipalign" -p 4 "<UNSIGNED_RELEASE_APK>" "<ALIGNED_RELEASE_APK>"
"<BUILD_TOOLS>/apksigner" sign --ks "<APPROVED_RELEASE_KEYSTORE>" \
  --ks-key-alias "<APPROVED_KEY_ALIAS>" --out "<SIGNED_RELEASE_APK>" "<ALIGNED_RELEASE_APK>"
"<BUILD_TOOLS>/apksigner" verify --verbose --print-certs "<SIGNED_RELEASE_APK>"
```

An in-place update of the official **1.04 / version code 4** helper requires the same signing certificate as the installed helper and a higher version code. A new Suntel signing key does not establish that compatibility. Verify package, version, APK signature, and certificate compatibility before publishing a candidate. If the matching signer is unavailable, use a separately reviewed managed migration; do not uninstall the launcher, remove its device-owner enrollment, or factory-reset the terminal to work around a signing conflict.

## Physical acceptance

Use a designated test terminal before fleet rollout:

- Start the helper through the existing launcher with `oldVersion`; verify the old-to-new version transition and return to the updated launcher.
- Cancel an update, retry it, and test a failed download or installation. Confirm that the terminal remains recoverable and retries terminate without an activity loop.
- Test missing `oldVersion`, a temporarily unavailable launcher, and repeated handoffs on Android 12+ and the supported terminal OS.
- Verify the existing kiosk/device-owner state and configuration remain intact. This helper does not change NFC or printer integration; confirm both with a blank NFC test tag and a synthetic receipt after the update. No real payments or customer data are needed.

A successful build or helper launch is not a substitute for these physical checks.

## Provenance

Derived from [Headwind MDM Launcher Restarter](https://github.com/h-mdm/launcherrestarter), maintained in the [Suntel fork](https://github.com/sorgator/launcherrestarter). Preserve upstream attribution and any existing notices. The imported upstream snapshot did not contain an explicit `LICENSE` file; this README does not invent a license or grant redistribution rights. Confirm the applicable terms before public redistribution. Keep credentials, private signing material, customer identifiers, and internal authentication data out of this public repository.
