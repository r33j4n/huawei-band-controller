# Message macros — direct SMS and WhatsApp Business drafts

Open **Message macros · Send SMS / WhatsApp draft**. This version changes the SMS action from a draft to **direct SIM-based sending**. WhatsApp Business remains a draft: you still tap Send inside Business. The game and original diagnostics remain separate.

1. Select **Send SMS directly (uses your SIM)**. Review/edit the recipient and message. Defaults: **+94 76 106 9598** and exactly **Hi This message is send by huawei band**. Local `0761069598` is interpreted as Sri Lankan (+94); edit the country code if needed.
2. Set the phone's **default SMS SIM** in SIM settings. If your dual-SIM preference is Ask every time or no valid default is available, the app asks you to configure one instead of guessing a SIM. It freezes the selected subscription when arming.
3. Tap **Arm SMS send** and grant Android **SMS permission** when requested. Permission alone sends nothing: review the fields and tap Arm again. If Android/MIUI denies sending, check the app's SMS permission and SIM/service settings. No default-SMS-app role, SMS reading permission, phone-state permission or WhatsApp credentials are requested.
4. For band input, check **Use band media controls**. **Audible local loop is now enabled by default**, based on your successful phone tests; silent routing is unreliable on this setup. A notification prompt may require tapping Arm again afterward. Touch-only macros need no media session.
5. While the macro screen is visible and armed, press **⏮ → ⏯ → ⏭** (Previous → Play/Pause → Next) within **8 seconds total** using the band, touchscreen or a mixture. **A match sends the SMS immediately. There is no messaging composer or second Send tap for SMS.** Both explicit Play/Pause and raw Toggle map to the middle button.

SMS uses the SIM/mobile network, and **normal SMS charges apply**. Long messages may be split into separately charged segments; the default short message is normally one segment. Every match disarms the macro *before* requesting the send. There is no automatic retry, background queue or restore of an armed macro after recreation/process death. Leaving, locking or losing focus also disarms it and stops its opted-in media session. Wrong order resets progress, and a new Previous starts another attempt. Review the recipient before arming; game controls never run message macros.

**Latest SMS result** is persisted and updated by a non-exported sent-result receiver, even if the activity closes. The UI distinguishes requested/pending, partial multipart progress, Android-reported sent status and errors. A successful sent callback does **not** verify recipient delivery; delivery receipts are not requested. Pending results never imply success. If a partial send fails or an outcome is unknown, retrying manually may duplicate already-sent segments. Old callbacks cannot overwrite the latest request, and duplicate callbacks are ignored. Phone numbers/message bodies are not copied to diagnostic logs.

Choose **WhatsApp Business draft** for the other action. It opens only `com.whatsapp.w4b` using a [click-to-chat link](https://faq.whatsapp.com/5913398998672934), prefilling the recipient/text; it never falls back to personal WhatsApp or a browser. Install/update Business and sign in to the desired account/profile. **You must tap Send inside Business.** Unattended WhatsApp sending requires a separately configured Meta Business Platform integration; the installed Business app alone does not provide that API. WhatsApp delivery/account state is not verified by this app.

Band input remains the existing MediaSession path; media-session activity never proves a band connection. Macro input logs distinguish touch and media (whose hardware source is unknown). This is a foreground macro screen, not lock-screen/background automation. SMS sending permission, SIM readiness, carrier acceptance, new macro routing and WhatsApp draft handling need physical-phone verification; no real SMS is sent by the build/tests.

Run `./gradlew testDebugUnitTest assembleDebug lintDebug`. Tests cover game logic, macro ordering/timeouts/one-shot execution, phone normalization and SMS-result aggregation (including missing callbacks, partial failures and unverified delivery). GitHub Actions runs the unit suite, builds and uploads `band-controller-debug` as before. Test touch input first with your intended recipient, then the band; each successful match can send a real charged SMS.

---


# Neon Orbit — Band Controller space shooter

The existing Band Controller app now includes **Neon Orbit**, an offline native Canvas space shooter. The original diagnostic screen, service, application ID and build toolchain are retained. Install this APK as an update, then tap **Play Neon Orbit space shooter** on the original screen.

## Play with touch first

Tap **Launch**, hold **◀ / ▶** to steer and hold **FIRE** for continuous lasers. Accessible button clicks also nudge or fire once. Destroy ships (+20) and two-hit asteroids (+30); collisions and escaped threats cost shields. Start with three shields, with a brief grace period after damage. Enemies gradually become faster and spawn more often. **Pause / Resume** freezes the simulation; **Play again** restarts after game over. The best score is saved locally. Use **Sound FX** to mute the synthesized local laser/explosion/hit effects. Touch gameplay needs no band, Huawei Health, media session, Bluetooth permission or internet access.

The starfield, ship engine, neon enemies, laser trails and explosion particles are drawn on Canvas. The field scales proportionally to the available screen area with insets for status/navigation bars. Portrait is used for this vertical game; controls remain native Android buttons. The simulation runs fixed 120 Hz steps driven by display timestamps, clamps long gaps, and does not assume a 60 Hz Redmi display.

## Enable the existing band control path

1. Launch a run using touch first.
2. Tap **Enable band mode** while the game is visible. On the first notification permission prompt, answer it and tap Enable again. Denying notifications does not prevent a subsequent attempt to enable the foreground media service.
3. Keep the Band 6 paired through Huawei Health as in the earlier diagnostic experiment. Optionally select **Audible loop** before enabling if this setup needs audible playback for routing. Default playback is silent; it is a real looping local WAV, not an empty foreground service. The short game sound effects are independent of this loop.
4. Use **Next → right**, **Previous → left**, **Play or Pause → fire**. A raw play/pause toggle also fires. Lasers have a 180 ms cooldown; faster commands are still logged but cannot bypass it. Each movement command nudges a target position; movement is interpolated smoothly. Touch controls continue to work alongside media commands.
5. The game diagnostic panel shows last media command/time, combined Play/Pause count, Next/Previous counts, game state and media playback state. Counts cover game-mode commands received in this app process, including notification or synthetic media controls. A zero Next/Previous count explicitly says **unverified**. A received command says **source unknown**: neither an active session nor a callback proves a band connection. All callbacks and raw media-key diagnostics remain in the original screen's persistent/shareable log.

**Game mode deliberately treats both PLAY and PAUSE as firing while keeping the local media loop/session in the playing state**, so repeated band presses can keep delivering fire commands. This differs from the original diagnostic mode, where Play starts and Pause pauses actual playback. Return to the original screen and explicitly Start the diagnostic session to test the original behavior again. The game's Pause button pauses the game, independently of media playback. Commands on title/pause/game-over screens are logged but do not move or shoot.

Band mode is explicit and may interrupt another player's audio because Android audio focus is required for this playback session. Touch-only play does not request focus. **Disable band mode** releases the game session and audio focus; it does not disable touch play. Focus loss pauses the game and stops game media playback rather than fighting another app for focus. Leaving/locking/backgrounding the game pauses the run, clears held controls and stops its opted-in media session. Resume explicitly and enable band mode again after returning. Opening the notification shade may also pause the game. The display stays awake during a running foreground game, so band-only play does not time out the screen. Neither gameplay nor audio focus resumes automatically. High scores survive process death; an interrupted run is retained across activity configuration changes but is not restored after the process is killed.

Earlier phone experiments verified media callbacks, including with the phone locked, for the **diagnostic** app. The new gameplay mapping still needs physical-phone verification. Huawei Health/firmware or other media apps can change routing; this app cannot force forwarding, read raw Huawei touches, or identify the originating controller. The default silent loop also needs testing on the phone. If commands stop, try the audible option and ensure this is the selected player. The game never runs unseen behind the lock screen.

## Architecture and validation

- `GameEngine`, `Player`, `Enemy`, `Bullet`, `CollisionManager`, `ScoreManager`: Android-free world state, collision rules, scoring and state transitions.
- `GameView`: Canvas rendering, frame scheduling and short local effects through `GameAudio`.
- `BandMediaController`: lifecycle-scoped main-thread bridge from the existing `PlaybackService` callbacks to the visible game's engine. Incoming commands remain logged through `Diagnostics`.
- `GameActivity`: accessible touch controls, lifecycle, high-score storage, session opt-in and diagnostic panel.

Run `./gradlew testDebugUnitTest assembleDebug lintDebug`. The JUnit suite covers collision boundaries, diagonal near misses, one-hit bullet consumption, asteroid damage/scoring, high-score retention, pause/resume/input gating, three-life game over and restart, escaped threats, movement limits, long-frame clamping and equivalence across frame rates. GitHub Actions runs the unit suite, builds with `./gradlew --no-daemon assembleDebug` and uploads the same `band-controller-debug` artifact. No Android Studio or SDK on your own computer is necessary when building through Actions; the CI runner installs its Android SDK.

Phone acceptance checklist: play a complete touch-only run with Health/band disconnected; verify movement, repeated fire, score, shields, pause/resume, game over/restart and high-score persistence; enable media mode and verify all three mappings; confirm Next/Previous remain unverified before receiving them; lock/unlock or press Home and confirm the run is paused and the game session stops; test notification controls and diagnostic mode again; start another music app and confirm focus loss is respected. Frame pacing, sound levels and Huawei routing must be assessed on the actual phone.

---

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
