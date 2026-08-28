# Release build

## Quick install (debug-key signed)

No setup needed. The release build falls back to the Android debug key when
`keystore.properties` is absent, so the APK installs on any device.

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

`versionCode` / `versionName` live in [app/build.gradle.kts](../app/build.gradle.kts)
(`defaultConfig`). Bump them before cutting a new build.

## Signing with your own key (for distribution)

1. Generate a keystore (keep it out of git — `*.jks` is already ignored):

   ```bash
   keytool -genkeypair -v -keystore hormonelog-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias hormonelog
   ```

2. `cp keystore.properties.example keystore.properties` and fill in the paths
   and passwords. `keystore.properties` is git-ignored.

3. `./gradlew :app:assembleRelease` now signs with that key.

## Notes

- `isMinifyEnabled = false` — no code/resource shrinking yet. If you enable R8
  later, test CSV import (`org.json` reflection) and every screen before shipping.
- Debug and release share the same `applicationId`, so installing one replaces
  the other on a device.
