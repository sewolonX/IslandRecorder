<h1 align="center">Island Recorder</h1>

[![License: GPL v3 or later](https://img.shields.io/badge/License-GPLv3%2B-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Latest Release](https://img.shields.io/github/v/release/wxxsfxyzm/IslandRecoder?label=Release)](https://github.com/wxxsfxyzm/IslandRecoder/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Android-brightgreen.svg)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-35-orange.svg)](https://developer.android.com/about/versions/15)

> A Xiaomi-focused screen recorder built as a replacement for the stock Xiaomi screen recorder, with
> device-specific settings integration and Super Island recording controls.

Island Recorder is a personal fork built because the stock Xiaomi screen recorder did not fit my
workflow. It is intended as a replacement recorder for supported Xiaomi devices, with local-only
recording, flexible video/audio settings, Xiaomi-specific system setting adaptation, and recording
controls exposed through Xiaomi Super Island.

This project is not intended for non-Xiaomi devices or Xiaomi devices that do not support Super
Island. Core Android recording paths may still compile or run elsewhere, but the product design,
privileged integrations, and control surface are optimized for Xiaomi/HyperOS behavior.

This project is developed based on the following projects:

- [Leaf-lsgtky/IslandRecoder](https://github.com/Leaf-lsgtky/IslandRecoder)
- [IcradleInnovationsLtd/FluxRecorder](https://github.com/IcradleInnovationsLtd/FluxRecorder)

## Highlights

- **Screen recording:** record the device screen through Android MediaProjection.
- **Resolution options:** native, 1080p, 720p, 480p, and 360p.
- **Frame-rate control:** auto mode and fixed limits from 15 FPS up to 120 FPS.
- **Codec choices:** H.264, H.265, and H.265 HDR, with H.265 used as the default fallback.
- **Audio sources:** no audio, internal audio, microphone, or both.
- **Recording controls:** foreground notification, Quick Settings tile, and recording state handling
  for start, stop, pause, resume, and cleanup.
- **Super Island controls:** recording status and controls are adapted for Xiaomi Super Island on
  supported systems.
- **Floating controls and facecam:** optional overlay controls and a CameraX front-camera preview
  remain available while recording.
- **Miuix interface:** Compose UI built around Miuix components and Xiaomi/HyperOS-oriented visual
  conventions.
- **Xiaomi-specific settings:** privileged settings integration for Xiaomi screen recording related
  behavior.
- **Scoped storage:** recordings use MediaStore under `DCIM/screenrecorder` by default, or a
  user-selected directory through Android Storage Access Framework, without broad storage or media
  permissions.
- **Launcher icon control:** optionally hides the launcher icon; settings remain accessible by
  long-pressing the Quick Settings tile.
- **Privacy-first design:** no analytics, no telemetry, and no network reporting.

## Privileged Features

Island Recorder can work without privileged access for normal MediaProjection-based recording. Some
advanced features require Root or Shizuku:

- **Show screen touches:** temporarily enables Android touch visualization while recording.
- **Xiaomi screen share protection control:** an independent setting temporarily disables and
  restores Xiaomi screen share protection around recordings when supported.
- **Project media permission grant:** grants the app the system project media operation where
  supported, reducing repeated capture permission friction.
- **Xiaomi Focus Island notification bypass:** an independent setting temporarily isolates XMSF
  while posting recording notifications on supported Xiaomi systems.

Privileged operations use binder hook paths through Shizuku or root `app_process`. Shizuku
UserService/AIDL is not used. The selected authorizer is respected, and controls are disabled when
that authorizer is unavailable.

## Supported Android Versions

- **Minimum SDK:** 35 (Android 15 based HyperOS 3 only)
- **Target SDK:** 37

## Device Compatibility

Island Recorder is designed for supported Xiaomi/HyperOS devices with Super Island support.

Not recommended:

- non-Xiaomi devices,
- Xiaomi devices without Super Island support,
- ROMs where Xiaomi screen share protection, Focus Island/Super Island, or related privileged
  settings behave differently.

Root/Shizuku features, Xiaomi privacy protection, Super Island behavior, and project media
permission behavior depend on the ROM, Android version, and active authorizer.

## Downloads

Download release builds from:

https://github.com/wxxsfxyzm/IslandRecoder/releases

If you report a bug, please include:

- app version,
- Android version and ROM,
- selected authorizer: Root, Shizuku, or none,
- reproduction steps,
- relevant logcat output.

## Building

Island Recorder is an Android Gradle project.

### Prerequisites

- **JDK 25** with `JAVA_HOME` configured correctly.
- Android SDK / Android Studio with the required platform and build tools installed.
- GitHub Packages credentials for the snapshot `miuix` dependency.

### GitHub Packages Authentication

GitHub Packages requires authentication for the Miuix Maven repository. Add your GitHub username and
a classic personal access token with the `read:packages` scope to your global Gradle properties
file:

- Linux / macOS: `~/.gradle/gradle.properties`
- Windows: `%USERPROFILE%\.gradle\gradle.properties`

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PERSONAL_ACCESS_TOKEN
```

Do not commit these credentials to this repository.

### Build Commands

For a local debug build:

```bash
./gradlew :app:assembleUnstableDebug
```

For a broader debug build that also covers helper modules:

```bash
./gradlew assembleDebug
```

The app uses the `level` flavor dimension:

- **Unstable:** default development flavor with a git hash version suffix.
- **Stable:** release flavor with the same git hash version suffix.

## Technical Overview

- **Language:** Kotlin
- **UI:** Jetpack Compose and Miuix
- **Dependency injection:** Koin
- **Settings:** AndroidX DataStore
- **Recording stack:** MediaProjection, MediaCodec, MediaMuxer, AudioRecord, and MediaRecorder
- **Privileged integration:** Shizuku and root `app_process` binder hooks
- **Modules:** `:app`, `:app-process`, and `:hidden-api`

## Privacy

Island Recorder is designed to keep recording data local.

- No analytics.
- No telemetry.
- No tracking.
- No remote reporting.
- Recordings and settings stay on the user's device.
- Files leave the device only through an explicit user share or export action.

## License

Island Recorder is released under the [GNU General Public License v3.0 or later](LICENSE).

This repository contains work derived from MIT-licensed upstream projects. Their copyright and
license notices are preserved in [NOTICE.md](NOTICE.md).

## Acknowledgements

This project uses code from, or is based on the implementation of, the following projects:

- [Leaf-lsgtky/IslandRecoder](https://github.com/Leaf-lsgtky/IslandRecoder)
- [IcradleInnovationsLtd/FluxRecorder](https://github.com/IcradleInnovationsLtd/FluxRecorder)
- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)
- [iamr0s/AndroidAppProcess](https://github.com/iamr0s/AndroidAppProcess)
