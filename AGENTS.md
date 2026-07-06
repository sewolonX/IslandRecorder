# AGENTS.md

## Purpose

This file defines repository-specific instructions for coding agents working on
**Island Recorder**.

Use it to decide:

* where a change belongs,
* which architectural boundary must be preserved,
* which project-specific risks need extra care,
* what to verify before claiming work is complete.

Task-specific maintainer instructions take precedence over this file. For narrow
requests, make the smallest coherent change that satisfies the request. For
substantial features, behavior changes, or refactors spanning multiple areas,
outline a short implementation plan before editing and keep it aligned with the
actual implementation.

---

## Read these first when relevant

* `README.md` — product scope and user-facing behavior. Treat it as
  documentation, not the final source of truth for implementation details.
* `PRIVACY_POLICY.md` — privacy commitments.
* `settings.gradle.kts` — active Gradle modules and repository setup.
* `app/build.gradle.kts`, `build-plugins/`, and `gradle/libs.versions.toml` —
  required before changing Gradle plugins, SDK levels, toolchains, variants,
  repositories, versions, or dependencies.

Do not update this file for one-off task details. Update it only for stable
rules that future agents should repeatedly follow.

---

## Current architecture

Island Recorder is a Kotlin Android screen recorder built around:

* Jetpack Compose UI with Miuix components.
* Koin dependency injection.
* DataStore-backed app settings.
* MediaProjection, MediaCodec, MediaMuxer, and AudioRecord/MediaRecorder based
  recording.
* Quick Settings tile and notification controls.
* Optional privileged operations through Shizuku or root `app_process` binder
  hooks.

The architecture is layered. Keep domain models and contracts independent from
Android framework implementation details.

---

## Active modules

Confirm module assumptions in `settings.gradle.kts`. The current active modules
are:

* `:app` — main Android app.
* `:app-process` — root `app_process` bridge and binder wrapper process.
* `:hidden-api` — hidden Android API declarations used by `:app`.
* `build-plugins/` — included build containing shared Gradle convention
  plugins and centralized SDK/JDK settings.

Do not assume any other top-level directory is an included module.

---

## Package map

Under `app/src/main/java/com/island/recorder/`:

* `core/` — low-level recording primitives such as audio capture, codecs,
  muxing, projection, and reflection helpers.
* `data/` — concrete persistence and repository implementations, including
  DataStore-backed settings.
* `di/` — Koin modules and dependency wiring.
* `domain/` — stable models, repository contracts, provider contracts, and
  business-facing types.
* `framework/` — Android/platform integrations: services, notifications,
  storage providers, permission checks, privileged providers, and Shizuku/root
  binder hook infrastructure.
* `ui/` — activities, pages, navigation, Compose components, themes, and UI
  state.
* `util/` — general utilities only when they do not fit a more specific layer.

Preserve these boundaries. Do not move behavior into a convenient but wrong
layer just to finish faster.

---

## Core rules

### Privacy

Island Recorder is privacy-first. Do not add tracking, telemetry, analytics,
remote reporting, or network calls without explicit maintainer approval. Screen
recordings and settings must remain local unless the user explicitly chooses an
export/share action.

### Native API preference

Prefer existing native Android APIs, binder APIs, repository abstractions, and
platform-facing helpers. Do not introduce shell-command implementations as a
shortcut when a maintained native/binder path exists.

### Privileged safety

Privileged operations are sensitive. Keep permission checks, capability checks,
fallback behavior, and failure logging explicit.

Use existing privileged abstractions:

* `DeviceCapabilityProvider` detects Root/Shizuku capability.
* `PrivilegedOperationProvider` chooses the active authorizer and exposes
  privileged use cases.
* `DirectPrivilegedExecutor` dispatches into Shizuku hook or root `app_process`
  hook runtimes.
* `DefaultPrivilegedService` contains privileged operation implementations.
* `ShizukuHookRecycler` and `ProcessHookRecycler` manage binder hook process
  lifetimes.

The project currently uses binder hook paths for privileged work. Do not
reintroduce Shizuku UserService/AIDL plumbing unless explicitly requested.

### User-selected authorizer

Respect the user's selected authorizer. If the user chooses Shizuku, features
that require Shizuku should be disabled when Shizuku is not authorized/running,
even if root is available. If the user chooses Root, root-only UI should be
disabled when root is unavailable, even if Shizuku is available.

Operational fallback can exist only where the code clearly intends it and the UI
state still explains the active capability accurately.

---

## Settings and state

Settings are DataStore-backed through `AppSettingsRepository`.

* `AppPreferences` is the aggregate app settings model.
* `RecordingSettings` is the recording-specific settings model passed to the
  recorder service.
* `AppSettingsRepository.currentPreferences` is the in-process latest settings
  snapshot for synchronous decisions.
* `preferencesFlow` is the collected settings stream for UI and state holders.

When writing settings, keep the DataStore write and in-process snapshot update
consistent. Avoid duplicate caches for individual settings unless there is a
specific reason and a clear invalidation/update path.

For capability state, `DeviceCapabilityProvider.refreshPrivilegeStatus()` should
be called when UI needs a fresh Root/Shizuku status, especially in transient
entry points such as quick-tile launched dialogs.

---

## Recording flow

The main recording lifecycle lives in `RecorderService`.

Important state rules:

* `RecordingState.Idle` means the service is ready for a new recording.
* `RecordingState.Recording` and `RecordingState.Paused` mean recording is
  active.
* `RecordingState.Stopping` means recording has been stopped but cleanup is
  still running. Treat it as busy. Do not start a new recording while cleanup is
  active.

Keep state transitions and Quick Settings tile refreshes in sync. If a change
affects start/stop/pause/resume behavior, review notification actions,
`QuickTileService`, `RecordingShortcutActivity`, and lock/screen-off handling.

Heavy recording setup and cleanup must stay off the main thread. Foreground
service and MediaProjection requirements must still be satisfied on the correct
thread/API path.

---

## Quick Settings tile and shortcut dialog

`QuickTileService` should be a thin control surface:

* Stop active recordings through `RecorderService.ACTION_STOP_RECORDING`.
* Launch `RecordingShortcutActivity` only when the recorder is truly idle.
* Treat `RecordingState.Stopping` as busy.
* Collect recording state and tile style while listening and update `qsTile`
  promptly.

`RecordingShortcutActivity` is a transient UI for starting recording from the
tile. Keep it focused:

* Audio source and touch visualization may be adjusted there.
* Broader app settings should remain in Settings unless explicitly requested.
* Refresh Root/Shizuku capability when opening the dialog if privilege-dependent
  controls are shown.

---

## UI conventions

Use Compose and existing Miuix components/patterns. Prefer current local
components and themes before introducing new UI abstractions.

Guidelines:

* Keep screen-level state collection in ViewModels or activity-level entry
  points as appropriate to the existing pattern.
* Keep domain/data logic out of composables.
* Keep settings screens comprehensive, but keep shortcut/dialog surfaces compact.
* Use existing string resources for user-visible text.
* Avoid introducing Material-only controls where nearby UI uses Miuix components.

---

## Dependency injection

Use Koin. Wiring belongs in:

* `di/CoreModule.kt`
* `di/SettingsModule.kt`
* `di/ViewModelModule.kt`

Avoid ad-hoc global singletons. If a dependency needs process lifetime, register
it explicitly in a Koin module and make ownership/lifecycle clear.

---

## Gradle and dependencies

Use the Gradle wrapper:

```bash
./gradlew ...
```

The project requires JDK 25. SDK levels, Java/Kotlin toolchains, and common
Android settings are centralized in `build-plugins`; do not downgrade or loosen
them without an explicit request.

Dependency/version rules:

* Prefer `gradle/libs.versions.toml` for dependency and plugin versions.
* Follow existing version catalog naming style.
* Respect centralized repositories in `settings.gradle.kts`.
* Do not add project repositories in module build files.

The app uses the `level` flavor dimension:

* `Unstable` — default debug/dev flavor with git-hash version suffix.
* `Stable` — release-style version name.

---

## Credentials and signing

GitHub Packages credentials for Miuix snapshots must stay outside the repository,
typically in global Gradle properties:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PERSONAL_ACCESS_TOKEN
```

The token needs `read:packages`. CI may use `GITHUB_ACTOR` and `GITHUB_TOKEN`.

Never commit credentials, signing keys, keystores, or inline secret values.

Release signing may use local `keystore.properties` or environment variables.
Keep both out of source control.

---

## Verification

Default smoke build:

```bash
./gradlew :app:assembleUnstableDebug
```

For changes affecting privileged logic, hidden APIs, `app_process`, or module
boundaries, run:

```bash
./gradlew assembleDebug
```

For narrow UI/resource-only changes, `:app:assembleUnstableDebug` is usually
enough unless the change touches shared build logic or privileged code.

Always report:

* which commands were run,
* whether they passed,
* if verification was skipped or could not be completed, why.

---

## Recommended workflow

1. State the concrete behavior or boundary being changed.
2. Locate the smallest relevant area of the repository.
3. Read nearby code and follow the existing pattern.
4. Update all affected layers deliberately.
5. Run the narrowest meaningful verification.
6. Summarize the result and any remaining risk.

Be careful with dirty worktrees. Do not revert unrelated user changes. If a file
already has user edits, preserve them and work with them.

---

## Commit messages

Use Conventional Commits when asked to commit:

* `fix:` — bug fixes or behavior corrections.
* `feat:` — user-visible features.
* `refactor:` — code restructuring without behavior changes.
* `docs:` — documentation updates.
* `i18n:` — translation/resource text updates.
* `build:` — Gradle, dependency, CI, or toolchain changes.

Example:

```text
fix: refresh tile state after recording cleanup
```
