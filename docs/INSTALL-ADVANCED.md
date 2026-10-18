# 安装排查与电脑命令（技术资料）

本页面向熟悉电脑命令的维护者和用户。普通安装、升级与恢复步骤见 [使用指南](INSTALL.md)。下面保留具体错误码、厂商调查结果和原设置恢复方法；它们不是每位用户都需要完成的安装步骤。

## 安装失败先区分原因

“安装失败”可能发生在不同环节。先核对提示中的组件和错误码，不要仅凭“已允许未知来源”就认定是 Play Protect。

| 现象或 ADB 错误 | 排查方向 |
| --- | --- |
| 提示不允许此来源安装 | 给实际打开 APK 的文件管理器或浏览器授予“安装未知应用”权限；入口名称由固件决定 |
| 明确显示 Google Play Protect | 按下节核对 Google 的扫描提示 |
| `INSTALL_FAILED_VERIFICATION_FAILURE` / `-22` | 系统要求的安装校验器拒绝；需查看校验器身份及厂商策略，不能直接等同于 APK 损坏或 Google 扫描 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 现有应用与新包签名不一致；先核对版本、证书及备份，不要直接卸载 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 新包 versionCode 更低；下载同签名的更新版本 |
| 系统版本或 ABI 不支持 | 核对最低 Android 版本及 ARM/x86、32/64 位；静幕自身没有 JNI 架构限制 |

### Play Protect 提示

带 Google Play 服务的设备可能对商店外来源 APK 提示扫描或阻止安装。按 [Google 官方说明](https://support.google.com/googleplay/answer/2812853?hl=zh-Hans)核对具体提示、扫描结果或误报申诉。扫描开关通常在 Play 商店 → 头像 → Play 保护机制 → 设置；Google 建议保持保护开启。来源授权与扫描是两个不同环节，静幕不自动修改它们。

### 厂商安装校验：TCL 实机发现

2026-10-17，在一台 TCL Android 9 固件上，只读查询系统安装器及其生效配置，确认启用了第三方来源白名单、第三方桌面限制及 ADB 安装限制。该设备没有 Google 服务，普通应用和静幕安装都出现过 `-22`。未知来源已开启仍会触发这些独立规则。

排查时，这台设备原始的第三方来源白名单按**包名**匹配，仅含 `com.dangbeimarket`、`com.shafa.market`、`com.ant.store.appstore`；所装 Emotn 的 `com.overseas.store.appstore` 不在其中。已启用的桌面校验还会检查 APK 中的 `MAIN` / `HOME` 声明。因此换用一个来源、重新签名或修改静幕设置 JSON，都不能保证安装成功；静幕首次安装前自身尚未运行，也无法修改系统的安装决定。

普通应用可先使用固件提供的原厂文件管理/安装入口；TCL 也有针对部分带 Safety Guard 机型的 [USB 安装说明](https://www.tcl.com/ru/ru/support-tv/faq/36461)。该文面向其他地区机型，本次未操作电视 UI 验证此路线，不能据此保证静幕桌面通过额外限制。普通 ADB 安装同样可能被拦截；只有确认包来源、哈希与发布证书后，才考虑下文的显式临时 ADB 兼容选项。

来源白名单问题可由厂商或设备所有者在确认权限和回退方法后，仅放行可信安装器；该变更不会自动解除第三方桌面限制，也可能被厂商在线配置或固件更新覆盖。本项目不自动写入厂商白名单，不提供适用于所有品牌的策略覆盖命令。

以上是单台设备的生效策略，不代表全部 TCL、所有地区或其他品牌。反馈时提供型号、Android/固件版本、安装来源包名及脱敏错误码；无需提交账号、媒体库或完整设备日志。


## 使用 ADB 的高级安装

设备地址用 `TV_IP:5555` 代替，不是统一的固定地址。需要自己在电视授权 ADB。

```powershell
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r QuietTV-v0.14.1.apk
# 打开界面（会切换电视画面）
adb -s TV_IP:5555 shell am start -n local.quiettv.launcher/.MainActivity
```

示例使用已发布的 0.14.1；下载其他版本时替换文件名。也可使用 `install-preview.ps1 -Device TV_IP:5555 -Apk .\QuietTV-v0.14.1.apk`。默认仅安装；加 `-Launch` 才打开画面。

正常 ADB 安装也返回 `-22`，且已确认是固件校验兼容问题时，可显式添加 `-UseAdbVerifierWorkaround`。脚本读取原 `verifier_verify_adb_installs`，只在本次 ADB 安装期间临时修改，并在 `finally` 恢复原值、核对恢复结果。它不会关闭全局 `package_verifier_enable`，不会改变 Emotn 等应用内安装的来源白名单，也不保证所有固件均允许此选项。安装包仅从官方 Release 获取，先核对附带哈希及证书；不要用它代替文件来源审查。

从本仓库下载 [install-preview.ps1](../install-preview.ps1)，准备 Android 官方 [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)，并将下载的 APK 哈希与对应 Release 中的 `SHA256SUMS.txt` 对照。下面是显式启用兼容选项的完整示例，文件名随实际版本替换：

```powershell
Get-FileHash .\QuietTV-v0.14.1.apk -Algorithm SHA256
.\install-preview.ps1 -Device TV_IP:5555 -AdbPath .\platform-tools\adb.exe -Apk .\QuietTV-v0.14.1.apk -UseAdbVerifierWorkaround
```

这条路径已用于本次 TCL Android 9 的静幕安装，并核对过原设置恢复；不保证其他固件开放相同开关。若脚本提示无法修改或无法确认恢复，保留错误输出、停止继续安装，向厂商或维护者提供脱敏信息。不要为解决安装失败直接卸载有数据的静幕，也不要关闭全部系统验证或停用原厂安装器。

在支持以下命令的固件上可手动选择默认桌面：

```powershell
adb -s TV_IP:5555 shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s TV_IP:5555 shell cmd package set-home-activity --user 0 local.quiettv.launcher/.MainActivity
```

恢复脚本要求明确指定查到的原 HOME 组件，不猜厂商：

```powershell
.\Restore-System-Home.ps1 -Serial TV_IP:5555 -HomeComponent 'ORIGINAL_PACKAGE/.HOME_ACTIVITY'
```

`ORIGINAL_PACKAGE/.HOME_ACTIVITY` 是占位符。脚本只接受该设备现有、已启用的 HOME。个人旧 APK 和历史回退工具不在公开版中。

系统文件选择器参考 [Android 文档](https://developer.android.com/training/data-storage/shared/documents-files)；默认桌面设置入口参考 [Settings](https://developer.android.com/reference/android/provider/Settings#ACTION_HOME_SETTINGS)。
