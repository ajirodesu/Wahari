# Wahari — Developer Certificate

This document certifies the developer identity behind the **Wahari** Android
application (`com.ajirodesu.wahari`).

| Field | Value |
| --- | --- |
| App | Wahari |
| Application ID | `com.ajirodesu.wahari` |
| Developer | **AjiroDesu** |
| Engine | Needle 3 by Cactus Compute (Apache-2.0) |
| Automation core | TaskPilot by TherealCitali (MIT) |
| Certificate file | `DEVELOPER_CERTIFICATE.md` (this file) |
| Runtime verifier | `dev.citali.needle.engine.DeveloperCertificate` |
| In-app surface | Settings → Developer certificate |
| API surface (legacy Python) | `GET /api/certificate` |

## What this certificate guarantees

1. **Authorship.** Wahari reskin, modifications and icon ("W-agent mark") are by
   AjiroDesu. The model stays Cactus Compute's Needle 3 and the
   screen-automation core stays TherealCitali's TaskPilot — see `NOTICE.md`.
2. **Verifiability.** Every installable APK is signed. The app can show its own
   signing-certificate SHA-256 fingerprint at runtime
   (Settings → Developer certificate), so anyone can compare the installed app
   against a published release (`SHA256SUMS`, `apksigner --print-certs`).
3. **No impersonation.** A repackaged APK signed with a different key produces a
   different SHA-256 fingerprint and fails the "pinned" check when an expected
   fingerprint is baked in via `WAHARI_EXPECTED_CERT_SHA256`.

## How to verify a build

```bash
# 1. Fingerprint of the APK you hold:
apksigner verify --print-certs Wahari-0.0.<n>-<sha>.apk | grep SHA-256

# 2. Compare with the SHA256SUMS published next to the release,
#    and with the fingerprint shown in-app under Settings → Developer certificate.

# 3. Pin a private release key (optional, CI / local):
#    CI reads the EXPECTED_CERT_SHA256 repository secret; local builds use the
#    WAHARI_EXPECTED_CERT_SHA256 environment variable.
export WAHARI_EXPECTED_CERT_SHA256="AA:BB:CC:..."
# The app then reports "Verified" only when the runtime signing
# certificate matches this value.
```

When `WAHARI_EXPECTED_CERT_SHA256` is empty (default public-key / local builds),
the app reports `UNPINNED` — authentic Wahari signed with the project key, but
not pinned to a private release key. That is expected for sideloading.
If the signature cannot be read at all, the card reports `UNKNOWN`.

## Signing-key note

The repository ships a public **project keystore**
(`android/keystore/needle-release.p12`) so any clone can produce an installable
APK. It is good for sideloading, **not** for Play Store uploads. For a private
release, set the `KEYSTORE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
repository secrets (see `README.md` → Signing).
