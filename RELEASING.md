# Release builds

## Source checkpoint - 2026-10-19

`gradlew.bat --offline testDebugUnitTest assembleDebug lintDebug` passed before
committing the existing automation, notifications, graph and release-workflow
changes. Some tasks reused valid Gradle outputs. Instrumentation/device tests
were not run for this checkpoint. No release was built and no version reserved.
Private HCI bugreport ZIPs and signing credentials are excluded from Git.

Run `./gradlew assembleRelease` (or `gradlew.bat assembleRelease` on Windows).
Android Studio's release APK / bundle builds use the same version task.

`release-version.properties` records the most recently reserved version.
Each release variant build increments `versionCode` and the patch component
of `versionName` once per Gradle invocation. Debug builds and Gradle sync
do not increment it. APK and bundle outputs built together share one version.
Commit the updated properties file with the release's source changes.

The version is reserved before the release's manifest is generated. A failed
or interrupted build may leave a gap; retrying reserves a new number.
Release-only manifest/lint tasks can also reserve a number because they
consume the release version. Build releases sequentially in a checkout.

To start a new major or minor line, edit the recorded `versionName` while
keeping `versionCode` increasing. The next release increments its patch.

The release APK is placed in `app/build/outputs/apk/release/`.

## Local signing

`release-signing.properties` configures `storeFile`, `storePassword`,
`keyAlias`, and `keyPassword`. The keystore is in `.signing/`. Both the
credentials file and directory are ignored by Git. Without the credentials
file, Gradle produces an unsigned release APK.

Keep a secure backup of the keystore and credentials: future updates must
use the same key. The local release key differs from Android's debug key,
so the signed release cannot directly replace a debug-signed installation.
