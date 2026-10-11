# QuietTV contributor and AI instructions

Scope: this repository. Read docs/BUILD.md, docs/ARCHITECTURE.md and docs/CUSTOMIZATION.md before changing the launcher. Keep changes focused and explain any unverified device behavior.

## Structure and commands

- Native Java (source compatibility Java 8), Android min API 23 / target API 28. No Gradle or external runtime dependencies.
- Windows: build.ps1 with explicit Android SDK / JDK paths, then tests/run-core.ps1 using the same paths.
- Linux/macOS: QUIETTV_RUN_CHECKS=1 bash build.sh with ANDROID_HOME and JAVA_HOME.
- res/raw/ui_defaults.properties is the first choice for existing supported UI parameters.
- Device/OEM entry points belong in DevicePolicy, InputSources and SystemEntrypoints. Media integration belongs in its adapter, not scattered through UI.

## Invariants

- Do not root, disable/uninstall OEM packages, write system partitions/properties, force display resolution, enable CEC, replace boot activities or change system volume.
- Default HOME selection must stay explicit and reversible. Keep opening another HOME distinct from setting it as default. Retain the original launcher.
- Handle missing system Activities, input APIs, permissions, decoders and network without breaking the basic desktop. Never invent physical input ports, device names, episode numbers or exact resume capability.
- Preserve remote-only operation, focus retention, complete-row clipping, left/right pane bounds and directional overflow arrows. Scale to the actual app window.
- Preserve existing preferences, backgrounds, record IDs and progress across upgrades. New optional network/history features are opt-in for fresh installs; migration must preserve existing choices.
- Keep at most 8 history items, validated routes, local artwork checks, bounded imports/caches and provider identity verification. No credentials, account databases, notification text or playback URLs in launcher storage or logs.
- Credentials, signing keys, unreviewed device captures, generated APKs, SDKs, user media/configuration and private rollback packages stay out of Git. Only documentation screenshots explicitly requested for publication and reviewed for privacy/provenance may enter docs/images/, with their source recorded. Never print credentials.

## Validation and publishing

Run a clean build and meaningful core checks. Device tests require the device owner's authorization; do not send keys, launch playback, capture the screen or reboot a user's TV just because ADB is available. The CI device tests operate only on an explicitly created emulator.

Distinguish compilation/JVM checks, emulator checks and real OEM tests. Do not claim broad compatibility from a single device. See docs/COMPATIBILITY.md and docs/RELEASING.md.

Use the contributor's own signing key for custom builds. Official private keys must not enter CI or pull requests. Keep package/signature identity consistent for upgrades; versionCode must increase. Review the complete public file list and Git history before publishing.

Public release scope: read docs/LEGAL-REVIEW.md and THIRD_PARTY_NOTICES.md. Do not reintroduce music-client patches, account helpers, DRM/advertisement/subscription circumvention, automatic IP geolocation, undocumented catalog fetches or remote-cover downloads without a separate provenance/service review. Preserve weather attribution, terms consent and noncommercial API conditions; MIT does not license service access.
