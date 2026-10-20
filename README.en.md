<div align="center">

# QuietTV

**Turn on your TV. Pick up where you left off.**

A free, open-source Android TV launcher with your favorite apps, continue watching, and inputs on one screen.

[中文](README.md) · **English**

[Download APK](https://github.com/zlsun2000/QuietTV/releases/latest) · [Installation guide](docs/INSTALL.en.md) · [AI rule prompt](RULE-PROMPT.md#english) · [Community rules](playback-rules/community/README.md#english)

</div>

![QuietTV on a real TV: favorite apps on the left, a Netflix continue-watching card on the right, and inputs below](docs/images/quiettv-home.png)

<p align="center"><sub>QuietTV 0.14.2 · Android 12 TV · An actual screen selected by its owner for publication. The card shows an existing record; playback behavior belongs to the media app. <a href="docs/SCREENSHOTS.md">Capture provenance and third-party artwork (中文)</a>.</sub></p>

## Fewer remote presses

- **Continue from your home screen.** Supported apps can show recent content, artwork, and reported progress, with up to eight records. Press OK to attempt playback; cards describe the available action.
- **Keep favorite apps close.** Pin, reorder, hide, and categorize apps. Navigate with the D-pad; long-press OK to organize.
- **Open your inputs.** Jump to HDMI, TV, or AV when the device exposes a usable entry point. Names and status come from the device.
- **Make it yours.** Use the included landscape, your own images, or a silent looping video. Keep the clock visible and enable weather if you want it.
- **Keep the stock launcher.** Basic installation needs no ADB, root, flashing, or disabled system packages. Choosing the default home screen is explicit and reversible.

QuietTV is free and has no launcher ads. Playback history and weather start disabled on fresh installations; records stay on the TV. Media still plays through the original app, with its own accounts, subscriptions, and service rules.

## Get started

For **Android 6.0+ TVs and boxes that allow third-party apps**.

1. Download the `.apk` from [Releases](https://github.com/zlsun2000/QuietTV/releases/latest). No source checkout or build is needed.
2. Transfer it by USB drive or your usual file-transfer tool, then open it with the TV's file manager.
3. Open QuietTV from the app list. Organize your apps, then choose it as the default launcher if your TV allows it.

**Installing, handling the Home button, and starting at boot are separate capabilities.** Firmware can restrict each one. Some devices require extra setup or ADB troubleshooting; “no ADB for basic installation” does not cover every permission or adaptation workflow. Keep the stock launcher installed and enabled.

**Upgrading:** official versions 0.13.0–0.14.1 can upgrade directly to 0.14.2 while retaining settings. Version 0.12.0 and earlier used a different signing identity. Uninstalling deletes settings, backgrounds, and history; see the [upgrade and recovery guide](docs/INSTALL.en.md#upgrading) first.

To switch back, choose the stock launcher in the TV's default-app settings and turn off QuietTV's optional Home-key accessibility service if enabled. Opening another launcher once does not change the default.

## Resume playback, with clear limits

QuietTV uses **Watch Next**, app broadcasts, or **MediaSession** data, then hands playback back to the original app through a validated route. It cannot manufacture history or progress that an app does not provide.

| Rule | Evidence available | Limits |
| --- | --- | --- |
| [Netflix Watch Next](docs/playback-rules/netflix-watch-next.json) | Owner-confirmed playback and artwork on Android 12 with Netflix 13.2.1 / build 27207 | Requires TV-listings permission; Netflix chooses the position. Other versions, profile changes, and cold starts are unverified |
| [TCL Qiyiguo 16.9](docs/playback-rules/qiyiguo-tcl-16.9.json) | App-managed resume on one Android 9 TV using a personal test build | Requires QuietTV 0.14.2; may select the episode in the app's current history. Public APK not retested on that device |
| [SmartTube 32.56](docs/playback-rules/smarttube-32.56-session.json) | Position resume after closing the client on the same personal test setup | Pinned package, version, and certificate. Public APK not retested there; older SmartYouTube is not supported by this rule |
| [TCL Bilibili variant](docs/playback-rules/bilibili-tcl.json) | Historical success with an earlier TV distribution | Current 1.7.7 adaptation has not succeeded; this is not general Bilibili support |

In the Chinese UI, open **桌面设置 → 继续播放 → 高级设置 → 应用适配规则 → 导入 JSON 规则** (Launcher settings → Continue playing → Advanced → App adaptation rules → Import JSON). Review the target version and notes, save, then enable the rule manually and play something normally in the original app. Enable the permissions required by that adapter.

Opening an app, opening content, restoring an active session, and passing a playback position are different capabilities. **A progress bar or a valid configuration does not prove exact resume.** See [compatibility details (中文)](docs/COMPATIBILITY.md).

## How the options compare

Based on official documentation reviewed **2026-10-20**. cleanyourtv was previously called tv-vibehack. “No ADB” below means ordinary installation and opening the app, not every firmware's default-home workaround.

| What matters | **QuietTV** | **cleanyourtv** | **FLauncher** | **Projectivy Launcher** |
| --- | --- | --- | --- | --- |
| Main focus | Minimal home + importable playback rules | AI-assisted debloating + custom home | Minimal app launcher | Extensive home customization |
| ADB required? | No for basic installation; optional advanced work | Yes for the documented workflow | No to install; optional default-home method uses ADB | No to install; Home behavior depends on device/setup |
| System packages changed? | No disabling or uninstalling OEM packages | Debloating disables packages per user; restore tools provided | Not for ordinary use; optional instructions disable stock Home | Not for ordinary launcher use; additional device tools are separate |
| “Bricking” / recovery boundary | No flashing or system-partition writes; stock Home retained | No flashing; disabling dependencies can disrupt Home or other functions and require recovery | No flashing for ordinary use; disabling stock Home adds risk | No flashing for ordinary use; additional system tools excluded from this comparison |
| Continue watching | Bounded by rule and tested app version | General resume not listed in the main project; derivatives differ | Not listed in the official feature list | Watch Next; relies on app records and routes |
| Prebuilt APK | [GitHub Releases](https://github.com/zlsun2000/QuietTV/releases/latest) | Officially build it yourself | [GitLab Releases](https://gitlab.com/flauncher/flauncher/-/releases) / Google Play | [GitHub Releases](https://github.com/spocky/miproja1/releases) / Google Play |
| Cost | Free, MIT source | MIT source; AI services may cost extra | Free, open source | Free core features; some personalization requires Premium |

Sources: [cleanyourtv README](https://github.com/Gathin23/cleanyourtv#readme), [FLauncher repository](https://gitlab.com/flauncher/flauncher), [Projectivy website / Premium FAQ](https://projectivylauncher.com/), [Projectivy store listing](https://play.google.com/store/apps/details?id=com.spocky.projengmenu).

This compares documented features and operations, not measured failure rates or performance on identical hardware. **No flashing does not mean zero risk.** An unlisted feature is not proof of absence; corrections with primary sources are welcome.

## Give your AI a starting point

Use these with Claude Code, Codex, or another assistant that can read files and run commands. Fill in the brackets first. Any AI service fees are separate from QuietTV.

### Install QuietTV

```text
Help me install QuietTV: https://github.com/zlsun2000/QuietTV
TV model: [fill in]; Android version: [fill in or unknown].
Method: [USB drive / ADB]; if ADB, target: [provide locally, never publish].

Read README.en.md and docs/INSTALL.en.md first. Prefer the APK from
the latest official stable Release; check its published hash and signing
information. Do not build from source just to install the official app.
For USB, give clear steps. By selecting ADB, I authorize installation or
a same-signature update of the official APK on this target only. Preserve
data; do not uninstall to work around a signature mismatch.
Keep OEM apps and the stock launcher. Default Home, extra permissions,
rule enablement, and playback tests require my explicit choices. Honor
existing authorization without asking again; do not extend its scope.
Report the result, how to open QuietTV, and how to restore the stock home.
Do not publish device addresses or private logs.
```

### Customize my home screen

```text
Customize https://github.com/zlsun2000/QuietTV for me.
Goal: four apps per grid row, clock size 48. Other changes: [fill in].

Read AGENTS.md if present, docs/BUILD.md, docs/ARCHITECTURE.md,
docs/CUSTOMIZATION.md, and docs/WRITING.md. Preserve my uncommitted work.
Prefer supported parameters in res/raw/ui_defaults.properties. Preserve
remote navigation, focus, complete rows, window scaling, and existing data.
Use a separate JSON rule for media adaptation when existing adapters fit.
Build with my own signing key; handle passwords through secure input and
keep signing material out of Git. Establish a clean build/core-check
baseline, make focused changes, and repeat the relevant checks.
Deliver the diff, APK, and validation limits. Work on this computer only;
wait for explicit authorization before installing on a TV or publishing.
```

### Adapt another media app

**Copy the [quick rule prompt](RULE-PROMPT.md#quick-version-fill-in-the-app-name)** and fill in just the app name. Generated rules are marked `unverified`. When unsure, use the [full version](RULE-PROMPT.md#full-version) with version information and interfaces you are authorized to inspect. Missing evidence should produce a list of unknowns, not an invented working integration.

**One prompt → one sourced JSON rule → one device report → one community contribution.**

Rules do not require rebuilding the launcher. Imported and updated rules stay disabled until the user reviews and enables them. [Specification and schema (中文)](docs/PLAYBACK-RULES.md) · [An adaptation case study (中文)](docs/QIYI-SMARTTUBE-DEVELOPMENT.md)

## Leave the next person a useful result

Browse the [community rule library](playback-rules/community/README.md#english), [submit a device report](https://github.com/zlsun2000/QuietTV/issues/new?template=device_verification.yml), or contribute a rule using the [template](playback-rules/community/TEMPLATE.md).

Success, content-only opening, failure, and regressions after an app update are all useful. Include the device, OS, app version, and observed result. Share sanitized notes and synthetic samples, not accounts, full playback links, private logs, or third-party APKs. Inclusion does not imply universal compatibility or automatic installation.

Sharing your own setup or original rule? Add `#vibecoding` `#claudecode`, the project link, and what you actually verified.

## Permissions and privacy

- **Optional media access.** MediaSession recognition uses Android's notification-access permission without reading notification text. Watch Next uses a separate TV-listings permission. Basic home functionality works without either.
- **Optional online artwork.** Enabling a rule with remote artwork contacts an image service, which sees your IP and image path. The Netflix example includes this declaration; review it before enabling.
- **Weather is opt-in.** Pick a city manually; there is no automatic IP geolocation. Open-Meteo / GeoNames attribution is retained. The current free weather API is for noncommercial use.
- **Device-dependent features.** Inputs, Home handling, and video backgrounds depend on firmware and decoders; missing interfaces cannot be supplied by a rule.

## More

[Install / recover](docs/INSTALL.en.md) · [Compatibility (中文)](docs/COMPATIBILITY.md) · [AI customization (中文)](docs/CUSTOMIZATION.md) · [Build (中文)](docs/BUILD.md) · [Contribute (中文)](CONTRIBUTING.md) · [Changelog (中文)](CHANGELOG.md)

Source code is [MIT-licensed](LICENSE). See [privacy (中文)](PRIVACY.md), [third-party notices (中文)](THIRD_PARTY_NOTICES.md), and [source review (中文)](docs/LEGAL-REVIEW.md). Third-party media, trademarks, apps, and service access are not licensed by this repository's MIT license. No affiliation or endorsement is implied.
