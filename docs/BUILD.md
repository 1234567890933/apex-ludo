# Building

## Local

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk     # needs platforms;android-34, build-tools;34.0.0

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

`app/build/outputs/apk/debug/app-debug.apk` is signed with the debug key and installs directly.

## Release APK

`app/build.gradle` signs the release build if a keystore is present:

* file: `beta-release.jks` in the repo root, or `$APEX_KEYSTORE` pointing anywhere
* passwords/alias: `APEX_STORE_PASS`, `APEX_KEY_ALIAS`, `APEX_KEY_PASS` (default `apexludo`)

```bash
keytool -genkeypair -keystore beta-release.jks -alias apexludo \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass apexludo -keypass apexludo \
  -dname "CN=Apex Ludo Beta, C=IN"
./gradlew :app:assembleRelease
```

Without a keystore the release build still compiles but produces `app-release-unsigned.apk`,
which Android will refuse to install.

> The keystore is a **beta test key**. Do not reuse it for a store release.

## Regenerating assets

`tools/gen_assets.py` (needs `numpy` and `pillow`) regenerates the launcher icons and every
WAV in `app/src/main/res/raw`. Nothing is downloaded; all audio is synthesized.

## CI

`.github/workflows/build-apk.yml`:

1. JDK 17 + Android SDK 34
2. `./gradlew :app:testDebugUnitTest` — the build fails if any test fails
3. generates a beta keystore
4. `assembleRelease` + `assembleDebug`
5. prints `aapt dump badging` for each APK, uploads `apex-ludo-1.1-beta-apk`

Artifacts are on the workflow run page.
