# QuietTV contributor and AI instructions

Scope: this repository. Read docs/BUILD.md, docs/ARCHITECTURE.md and docs/CUSTOMIZATION.md before changing the launcher. Keep changes focused and explain any unverified device behavior.

## Writing for users: standing maintainer requirement

The owner wants plain Chinese that people without a technical background can understand. This requirement persists across tasks, conversations, maintainers and releases. Read [docs/WRITING.md](docs/WRITING.md) before changing user-facing text or preparing a release.

- README, GitHub release descriptions, public CHANGELOG, installation/help/privacy pages, UI text and replies to the owner must lead with what the user sees and what they need to do. Avoid unexplained acronyms, engineering jargon and literal translations of internal implementation terms.
- Keep implementation details, commands, test counts and API/SDK names in clearly labelled developer documents or release metadata. User pages must still explain upgrade data loss, permissions, network/privacy effects and actual compatibility limits in everyday language.
- Prepare release copy in docs/releases/vX.Y.Z.md using docs/releases/TEMPLATE.md, review it against docs/WRITING.md, then publish that exact text. Do not paste commit messages, CI output or technical audit reports into the public release body.
- Preserve this requirement when updating project instructions. The PR checklist and release process must continue to reference it; a new feature is not a reason to revert to technical prose.
- Documentation-only changes require link/anchor and formatting checks plus the public-file/history review. They do not require rebuilding the APK or operating a user's TV. Changes to packaged UI text/resources still require the appropriate build and checks.

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
