# Android module (disabled by default)

This module is scaffolded but **not** wired into the build, because no Android SDK
was present when the project was created. The desktop build works without it.

## Enabling it

1. Install the **Android SDK** (Android Studio, or command-line tools).
2. Point the build at it, either by exporting `ANDROID_HOME=/path/to/Android/Sdk`
   or by creating `android/local.properties` with:
   ```
   sdk.dir=/path/to/Android/Sdk
   ```
3. In `settings.gradle`, uncomment:
   ```
   include 'android'
   ```
4. Build/run:
   ```
   ./gradlew :android:assembleDebug        # build an APK
   ./gradlew :android:installDebug         # install to a connected device/emulator
   ```

## Notes
- The Android Gradle Plugin version is pinned in `android/build.gradle`
  (`com.android.application` `8.7.2`). If Gradle reports an incompatibility, bump it to
  a version compatible with your Gradle wrapper.
- `copyAndroidNatives` unpacks the libGDX native `.so` files into `android/libs/`
  before the APK is assembled.
- The launcher shares 100% of the game logic in `core` — same `Terrarium` class as desktop.
