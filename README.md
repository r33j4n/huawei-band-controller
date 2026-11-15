# Band Controller

Minimal native Kotlin Android diagnostic app for a Redmi Note 12 / Huawei Band 6 experiment. It tests whether **Android MediaSession commands** reach this app while its local playback session is active. It does not connect to the band, read raw band touch events, bypass Huawei Health, or identify a command's originating device.

## Get an APK (no Android Studio or locally installed SDK needed)

Push the branch to GitHub and open **Actions → Build debug APK → latest successful run → Artifacts → band-controller-debug**. Download and unzip the artifact, then install `app-debug.apk` on the phone. GitHub Actions installs JDK 17 and the Android SDK on its runner, invokes `./gradlew --no-daemon assembleDebug`, and uploads `app/build/outputs/apk/debug/app-debug.apk`. CI needs network access to GitHub, Gradle, Google's Android repositories and Maven Central. Debug APKs use the runner's generated debug signing key; no credentials or signing keys belong in this repository. Builds from different runners may require uninstalling an earlier APK before installation (which clears its diagnostics).

Android compilation requires an SDK on the **build machine**. It does not require an SDK on the phone or your local computer when using CI. There is no SDK-free Android compilation claim.

## Build from a terminal

Toolchain: JDK **17**, Gradle **8.9** (committed wrapper with distribution checksum), Android Gradle Plugin **8.7.3**, Kotlin **2.0.21**. Minimum Android 8 / API 26; compile and target API 35. AndroidX Media 1.7.0 provides MediaSessionCompat and notification/media-button support. No Android Studio, account, server, runtime network access, Bluetooth permission, or Huawei API key is required.

Install Android command-line tools, set `JAVA_HOME` to JDK 17, put its `bin` on `PATH`, and set `ANDROID_HOME` to your SDK directory. Accept the Android SDK license terms using `sdkmanager --licenses`, then:

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
./gradlew --no-daemon assembleDebug
# Optional static Android checks:
./gradlew --no-daemon lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`ANDROID_HOME` avoids needing `local.properties`. Never commit `local.properties`, keys, credentials, APKs or generated build directories. The wrapper downloads Gradle on first invocation. On Windows use `gradlew.bat`. No emulator is required for building.

## Test on the physical phone

1. Install the debug APK, open **Band Controller**, and allow notifications on Android 13+. Notification permission is useful for visibility but is not required to start a foreground media service. If sideloading, allow installation for your browser/file manager only as needed.
2. Keep the Band 6 paired through Huawei Health. Confirm its normal music-control feature is enabled and Huawei Health has the permissions/background access it normally requires. Menu names vary by Health/firmware version. This app does not change Huawei settings.
3. Stop other music apps. Tap **Start / resume test session** while Band Controller is visible. The app runs a real one-second local WAV loop at zero player volume by default; optionally enable the quiet audible test tone before starting. Set phone media volume appropriately for audible testing. The UI start is logged but does not increment a remote-event counter.
4. Verify the UI says **playing** and Android shows Band Controller's media controls. Press notification **Next**, **Previous**, and **Pause/Play** first. These are a local control-path check, not evidence of band routing. Counters should increase and the latest timestamp should change. Tap Start to resume if another app steals audio focus.
5. Clear counters/logs, then operate Next, Previous and Play/Pause from the **band's music screen**. Try with the phone unlocked, then locked. Check the app for corresponding callbacks and timestamps. Next/Previous rewind the same test clip; no playlist or song change is implied. After Pause, try Play; if the OS routes it elsewhere, start the session from the app again.
6. Use **Share diagnostics** to export the displayed counters/logs. **Stop session** releases the player, audio focus and session and removes the service notification. Closing the activity alone leaves the service running for lock-screen testing. Don't leave the loop running unnecessarily; it uses battery even when silent.

Logs are independent of Bluetooth/band connection state, persist across activity/service restarts, and include service lifecycle, focus changes, playback state, callback names, and Android media key code/action/repeat when supplied. They are capped at about 24,000 characters. Counters and last timestamps persist until cleared or app data is removed. `PLAY` and `PAUSE` are separate callbacks; `TOGGLE` counts a raw play/pause (or headset hook) key. Some controllers send explicit play/pause rather than a toggle. Key-up/repeat diagnostics do not increment counters. UI/notification controls also count when they invoke session callbacks; logs cannot prove source attribution.

## Diagnose routing

With USB debugging authorized:

```sh
adb shell dumpsys media_session
adb logcat -s BandController:I
# Synthetic Android media-key checks (do not prove band delivery):
adb shell input keyevent 87   # next
adb shell input keyevent 88   # previous
adb shell input keyevent 85   # toggle
```

Look for the `BandController` session, active/playing state and advertised next/previous/play/pause actions. Keep USB debugging restricted to a trusted computer. The app has no network or storage permissions; sharing logs is an explicit Android share-sheet action.

## Limits and interpretation

Huawei Health, the band's firmware, MIUI/HyperOS, audio-focus policy, or another active media player may consume or route commands elsewhere. If notification controls work but band controls produce no callbacks, the app's control path works; **band-to-session routing remains unproven**. Absence of a callback does not prove the band emitted nothing. This app cannot force Huawei Health to forward commands or intercept its internal events.

Try the audible mode, ensure this is the most recently playing session, and check the system media panel. If background behavior differs, review battery/background restrictions for both this app and Huawei Health on the phone. Audio-focus loss pauses playback and is logged; start again after stopping the competing app. Android can remove paused media sessions, stop background processes or kill the app without an `onDestroy` log; the last persisted status can then be stale. There is no automatic boot start or sticky service restart. Start explicitly from the visible activity.

The foreground service exists only to support user-started local media playback testing. It advertises the mediaPlayback service type and uses foreground-service permissions (including Android 14+ media playback permission). It requests audio focus after entering foreground, as required for target Android 15. It does not use an empty foreground service or simulate a hardware connection.

## Validation scope

Build and lint validate packaging and static checks. Physical band routing requires the experiment above on your own Redmi Note 12, installed Huawei Health version and Band 6 firmware; a CI build cannot establish hardware compatibility. There are no automated hardware tests or claims that band delivery has already been verified.
