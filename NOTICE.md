# Wahari — third-party notices

Wahari is modified by **AjiroDesu** from the Needle codebase. The on-device
model stays Cactus Compute's Needle 3 (Apache License 2.0) and the
screen-automation core stays TherealCitali's TaskPilot (MIT,
https://github.com/TherealCitali) — both credited below with their licences
intact.

This repository contains the original Python/Termux assistant (MIT, see below if
present) plus an Android application under `android/` that bundles or adapts work
from two other projects. Both are used with their licences intact.

## Needle (the model and its engine) — Apache License 2.0

- Copyright (c) Cactus Compute, Inc.
- https://github.com/cactus-compute/needle
- https://huggingface.co/Cactus-Compute/needle3
- Vendored: `android/app/src/main/cpp/needle.h` (the public C header).
- Downloaded at build time: `libneedle.a` (the `arm64-v8a` engine) from
  `Cactus-Compute/needle3`, checksum-verified against the hash in
  `android/app/src/main/cpp/CMakeLists.txt` (cached archives are re-verified;
  the other ABIs build an engine-less stub so the APK installs everywhere).
- Downloaded on the device, once: `needle3.cact` (the 2-bit quantised weights),
  verified against the SHA-256 pinned in `android/app/build.gradle.kts`.

Licensed under the Apache License, Version 2.0; a copy is in
`licenses/Apache-2.0.txt`.

## TaskPilot — MIT License

The screen-automation core in `android/app/src/main/java/dev/citali/needle/pilot/`
is adapted from TaskPilot.

- Copyright (c) 2026 TherealCitali
- https://github.com/TherealCitali/TaskPilot

Files vendored and modified (package renamed to `dev.citali.needle.pilot`, the
decision step rewired to the on-device model, UI replaced):

- `agent/Action.kt`, `agent/AgentEngine.kt`, `agent/DeterministicExecutor.kt`,
  `agent/LlmClient.kt`, `agent/Plan.kt`, `agent/SafetyPolicy.kt`
- `accessibility/NeedleAccessibilityService.kt` (from `TaskPilotAccessibilityService.kt`),
  `accessibility/UiTree.kt`
- `data/AppInventory.kt`, `data/HistoryStore.kt`, `data/SecureStore.kt`, `data/SettingsStore.kt`
- `overlay/PilotOverlay.kt`, `overlay/PilotQuestionOverlay.kt`

The full MIT text is in `licenses/MIT-TaskPilot.txt`.

## Everything else

Jetpack Compose, AndroidX, Material 3, Kotlin and the Gradle wrapper are used
under their respective Apache-2.0 / MIT licences.

## Developer certificate

Wahari's developer identity (AjiroDesu) is certified in
[`DEVELOPER_CERTIFICATE.md`](DEVELOPER_CERTIFICATE.md) and verifiable at
runtime via `dev.citali.needle.engine.DeveloperCertificate`
(Settings → Developer certificate) and `GET /api/certificate`.
