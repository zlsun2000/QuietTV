# Install, upgrade, and use QuietTV

[中文](INSTALL.md) · **English** · [Back to README](../README.en.md)

For **Android 6.0+ TVs and boxes that allow third-party apps**. TVs running other operating systems cannot install this APK. QuietTV is currently distributed through GitHub Releases, not Google Play.

The app's menus currently use Chinese. This guide keeps the actual menu labels alongside English explanations so you can find them on screen.

## Install without a computer

1. Open the [official download page](https://github.com/zlsun2000/QuietTV/releases/latest) and download the file ending in `.apk`. **Source code** archives are for developers.
2. Transfer the APK to your TV using a USB drive or your usual file-transfer tool. Open it with the TV's file manager.
3. If Android blocks this installation source, follow the prompt to allow the file manager you are using to install apps. Menu names vary by firmware.
4. Open **静幕 / QuietTV** from the app list. Use the D-pad to navigate, OK to open, and long-press OK on an app card to organize it.
5. If the TV offers a default-home chooser, select QuietTV and **Always**. Other devices may provide Settings → Default apps → Home, or no supported choice at all.

App organization, the clock, and imported backgrounds work offline. Weather and playback history are disabled on fresh installations; enable them only if you want them.

**Successful installation, Home-button behavior, and starting at boot must be checked separately.** Some TVs still open the stock launcher at boot. Keep it installed and enabled so you can switch back.

## Upgrading

- **Official 0.13.0–0.14.1 to 0.14.2:** install the new APK over the existing app. Favorites, backgrounds, settings, and history are retained.
- **0.12.0 or earlier:** the signing identity changed, so these installations cannot be updated directly with the newer official APK. Reinstallation requires uninstalling the old app, which **deletes its settings, favorites, backgrounds, and playback history**. Decide whether that data loss is acceptable before proceeding.
- **Custom builds or APKs from other sources:** they may use a different signature. Check the source and error before uninstalling an app that contains data you need.

Official versions from 0.13.0 use the same release signing identity. Android normally prevents installing an older version over a newer one. If Home returns to the stock launcher after an update, choose your default again where the firmware allows it.

Earlier personal builds had features that are absent from the public release, including a dedicated Apple Music integration and automatic city detection. Public music support depends on information exposed by the media app; weather uses a city you choose manually.

## If installation fails

Keep the exact error text. Allowing unknown sources does not bypass separate manufacturer restrictions on third-party apps or launchers.

| What you see | What to try |
| --- | --- |
| This source cannot install apps | Grant installation permission to the file manager actually opening the APK |
| Google Play Protect message | Read the specific warning and scan result; see [Google's official guidance](https://support.google.com/googleplay/answer/2812853?hl=en) |
| Installation still fails after granting permission | Try the manufacturer's file-manager installation entry point; report the model, QuietTV version, method, and exact error if it still fails |
| Conflict with an installed app | Check the signing/source differences and the upgrade section before uninstalling; uninstalling loses data |
| Older version or unsupported system | Check the downloaded release and Android version before trying the same APK again |

Additional installation restrictions were confirmed on one TCL TV; other models may differ. **Allowing third-party apps does not necessarily mean allowing third-party launchers.** QuietTV cannot change an installation policy before it is installed.

For error codes and the investigated manufacturer behavior, see [advanced installation notes (中文)](INSTALL-ADVANCED.md). An explicitly selected workaround for a confirmed firmware issue temporarily changes one ADB-install verification setting and restores it afterward. It is not a universal fix. Do not disable all security checks or remove the OEM installer.

Reports need only the model, versions, method, and error text. Redact accounts, network addresses, and private content before sharing screenshots; do not publish complete private logs.

## Home button on Google TV

Some Google TV devices still open the stock launcher when Home is pressed, even after selecting another default. QuietTV offers an optional **Home 键修复 / Home-key fix**:

1. Open **桌面设置 → 系统与桌面 → Home 键设置** (Launcher settings → System and home → Home-key settings), then turn on **Home 键修复**.
2. Follow the prompt to Android's accessibility settings and enable **静幕 · Home 键修复**.
3. Return and press Home to observe what happens. An enabled switch alone does not prove the firmware permits the behavior.

The feature starts disabled, handles the Home key, and does not read screen content. Turn off the QuietTV switch or disable its accessibility service to restore the previous key behavior. Firmware may restrict it, and it may affect long-press Home shortcuts; disable it if unsuitable.

This feature has not been confirmed on a real Google TV device. It is separate from selecting a default launcher and does not guarantee launching QuietTV after a full power-off.

## Backgrounds

Open **桌面设置 → 背景与外观 → 自定义背景 → 从系统文件选择器导入** (Launcher settings → Background and appearance → Custom background → Import with system file picker).

Try common image/video formats such as JPEG, PNG, and MP4. Images are limited to 50 MB and videos to 400 MB. Selected files are copied into QuietTV, so you can remove the USB drive afterward. For video backgrounds, start with a small 1080p MP4. If the TV cannot play it, QuietTV shows the included landscape instead.

If no system file picker is available, try **U 盘 / 旧系统文件浏览** (USB / legacy file browser) and grant file access when prompted. Without that permission, the included background and already imported files remain usable. See [format and device notes (中文)](COMPATIBILITY-DETAILS.md).

## Continue playing

For recognition through the media app's current playback session:

1. Open **桌面设置 → 继续播放** (Launcher settings → Continue playing) and enable **显示播放记录** (Show playback history).
2. Under **记录来源与权限** (Sources and permissions), enable **自动识别媒体播放** (Automatically recognize media playback). Open **媒体访问权限** (Media access) and follow Android's notification-access prompt.
3. Play something normally in the original app, then return to QuietTV. Use the app list in settings to exclude apps you do not want recorded.

Android calls the permission “notification access.” QuietTV uses media-session information such as title, artwork, and progress; **it does not read notification text**. Basic launcher functionality works without this permission. Some TVs omit the permission entry or require **Allow restricted settings** in app information first.

QuietTV stores at most **eight records**. Missing artwork, episode information, or progress cannot be reconstructed. **打开应用 / Open app** only launches the app. **恢复当前播放 / Resume current playback** needs the original session to remain controllable. The media app ultimately determines whether playback continues from the previous position.

### Import an app rule

Open **高级设置 → 应用适配规则 → 导入 JSON 规则** (Advanced → App adaptation rules → Import JSON), within Continue playing settings. Select a local JSON file, review its target version and notes, save it, and enable it manually. Updated rules also stay disabled until reviewed. If the TV lacks a file picker, a file-manager app may be needed.

Watch Next rules such as the Netflix example use the separate **电视节目表 / TV-listings** permission. Notification access is not required solely for Watch Next. A JSON file cannot supply a missing system provider, permission, or app-published record.

Netflix resume has been confirmed only for specific app/device versions. Bilibili 1.7.7 adaptation has not succeeded; Disney+ remains untested. See [compatibility (中文)](COMPATIBILITY.md), [rule specifications (中文)](PLAYBACK-RULES.md), or the [English AI rule prompt](../RULE-PROMPT.md#english).

Rules with online artwork contact the declared image service, which can see your IP address and image path. Read the rule's network disclosure before enabling it. The Netflix example includes online artwork; playback and artwork are separate capabilities. See [privacy (中文)](../PRIVACY.md).

## Weather

Open **桌面设置 → 天气** (Launcher settings → Weather), read the service conditions, enable the feature, and search for your city. QuietTV does not locate you automatically by IP address.

Weather requires network access. The current free API is for noncommercial use, with Open-Meteo / GeoNames attribution. You can turn it off at any time.

## Restore the stock launcher

Choose the stock launcher in Android's **Default apps / Home** settings. QuietTV's **打开其他桌面 / Open another launcher** opens it once without changing the default.

If you enabled the Home-key fix, turn it off or disable the corresponding accessibility service as well. Once Home opens the stock launcher, you can keep or uninstall QuietTV. Uninstalling deletes QuietTV's settings, imported backgrounds, and history; a factory reset is normally unnecessary.

QuietTV retains OEM apps and does not write system partitions or change boot ads and screensavers.

## Optional ADB installation

ADB is for users who explicitly choose computer-assisted installation or troubleshooting. Ordinary USB/file-manager installation does not require it. Use only the device you own or are authorized to manage.

For commands, error-code interpretation, and recovery details, see [advanced installation (中文)](INSTALL-ADVANCED.md#使用-adb-的高级安装). Do not uninstall a data-bearing app merely to resolve a signature mismatch, or disable the stock launcher to make Home work.
