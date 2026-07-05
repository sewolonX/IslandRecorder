# AGENTS.md

## Purpose

This file defines repository-specific instructions for coding agents working on **Island Recorder**.

Use it to decide:

* where a change belongs,
* which constraints must be preserved,
* what to verify before claiming a task is complete.

Task-specific maintainer instructions take precedence over this file. When the request is narrow, make the smallest coherent change that satisfies it.

For substantial features, invasive refactors, or behavior changes that span several files, sketch a short implementation plan before editing. Keep the plan aligned with the actual implementation as the work proceeds.

---

## Read these first when relevant

* `README.md` — product scope, core features, and user-facing documentation.
* `PRIVACY_POLICY.md` — data handling and privacy commitments.
* `settings.gradle.kts`, `app/build.gradle.kts`, and `gradle/libs.versions.toml` — before touching Gradle, repositories, flavors, versions, or dependencies.

Do not duplicate or contradict those files casually. Update this file only for stable, repository-wide rules that agents should repeatedly follow.

---

## Repository overview

Island Recorder is a screen recording utility with:

* High-quality screen and internal audio recording capabilities.
* Support for facecam (front camera) overlay during recording.
* Privileged workflows involving Shizuku and `app_process` for advanced system interactions.
* Integration with Miuix UI components for a modern, consistent look.
* Dynamic versioning based on Git commit history.

---

## Critical project constraints

* **Privacy First**: Island Recorder is committed to user privacy. Do not introduce any tracking, telemetry, or network-reporting behavior without explicit maintainer approval.
* **Native API Preference**: Prefer the repository’s existing native API paths and abstractions. Avoid introducing shell-command implementations as a shortcut.
* **Privileged Safety**: Use privileged backends (Shizuku, `app_process`) with caution. Ensure proper permission checks and error handling are in place.

---

## Project layout

### Top-level areas

* `app/` — main Android application.
* `app-process/` — privileged helper process bridge.
* `hidden-api/` — hidden API declarations/helpers consumed by the app.
* `build-plugins/` — shared Gradle convention plugins (Composite Build).

Do not assume every top-level directory is an included Gradle module. Confirm active modules in `settings.gradle.kts` before making module-level assumptions.

### Main Kotlin package map

Under `app/src/main/java/com/island/recorder/`:

* `core/` — shared low-level app infrastructure.
* `data/` — persistence, concrete providers, repositories, and mappers.
* `di/` — Koin modules and initialization wiring.
* `domain/` — domain models, repository contracts, providers, use cases, and business rules.
* `framework/` — Android/platform-facing integration code (including Shizuku services).
* `ui/` — screens, widgets, navigation, themes, and UI-specific models.
* `util/` — utility helpers.

Preserve this separation. Do not move behavior into a convenient but wrong layer just to finish faster.

---

## Build prerequisites

### Toolchain

* Use the repository Gradle Wrapper: `./gradlew ...`.
* The project requires **JDK 25**.
* Kotlin/JVM toolchains and Android compile settings are centrally defined in `build-plugins`; do not downgrade or loosen them unless the task explicitly requires it.

### GitHub Packages authentication

The project resolves snapshot `miuix` artifacts from GitHub Packages.

For local builds, credentials are expected outside the repository, typically in the global Gradle properties file:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PERSONAL_ACCESS_TOKEN
```

The token needs `read:packages` access. CI may instead use `GITHUB_ACTOR` and `GITHUB_TOKEN`.

Never commit credentials or inline them into tracked files.

---

## Default verification

### Standard smoke build

For general changes, run the unstable debug build:

```bash
./gradlew :app:assembleUnstableDebug
```

For changes affecting privileged logic or system integrations, ensure `:app-process` and `:hidden-api` are also built:

```bash
./gradlew assembleDebug
```

### Report verification honestly

When summarizing work:

* state which commands were run,
* state whether they passed,
* say explicitly when verification was not run or could not be completed.

---

## Gradle, variants, and dependency rules

### Flavors and build levels

The app uses the `level` flavor dimension:

* `Unstable` (Default): Includes git-hash version suffixes.
* `Stable`: Clean version name for releases.

### Dependencies

* Prefer `gradle/libs.versions.toml` for dependency and plugin version changes.
* Follow the existing version catalog naming style.
* Respect the current centralized repository setup in `settings.gradle.kts`.

---

## Architecture conventions

### Dependency injection

Use the existing Koin structure in `app/src/main/java/com/island/recorder/di/`. Keep initialization wiring explicit and avoid ad-hoc global singletons.

### UI conventions

* **Miuix Integration**: Island Recorder heavily utilizes Miuix components. Follow Miuix design patterns for consistency.
* **Component Separation**: Keep UI logic separated from domain and data layers. Use ViewModels to bridge state to the UI.

---

## Recommended agent workflow

1. Restate the concrete behavior being changed.
2. Locate the smallest relevant area of the repository.
3. Find the nearest existing pattern and extend it.
4. Update all affected layers.
5. Run the narrowest meaningful verification (e.g., `:app:assembleUnstableDebug`).
6. Summarize results clearly.

---

## Commit message discipline

Follow the Conventional Commits style:

* `fix:` — bug fixes or behavior corrections.
* `feat:` — new features.
* `refactor:` — code restructuring.
* `docs:` — documentation updates.
* `i18n:` — translation updates.

Example: `feat: add facecam toggle to recording settings`
