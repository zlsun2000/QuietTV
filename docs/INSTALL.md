# 安装、升级与恢复

## 不使用电脑

1. 从 [GitHub Releases](https://github.com/zlsun2000/QuietTV/releases/latest) 下载对应版本的 `QuietTV-v<版本号>.apk`，不要下载名为 Source code 的源码压缩包。以 Release 页面实际发布的文件为准。
2. 放入 U 盘，或通过自己的文件传输工具发送到电视。用电视文件管理器打开 APK。固件提示时，仅允许该文件管理器安装应用。
3. 安装后从应用列表打开“静幕”。方向键选择、确认键打开、长按确认键整理。内置背景、应用整理和时间可离线使用。
4. 首次帮助页可以选择默认桌面。若按 Home 出现选择器，选择静幕并选“始终”；也可到系统“默认应用 / 主屏幕”。这些入口可能被电视厂商移除。
5. 在“桌面设置 → 天气”和“桌面设置 → 继续播放”分别按需开启。设置自动保存，修改后保留当前页面，返回键逐级返回。无需为了基础桌面开启通知访问权限。

安装成功、Home 返回静幕、完全重启自动进入静幕是三个不同项目。请分别核对。部分固件固定开机入口，第三方 APK 无法保证接管；不能通过删除或停用原桌面解决。按 Home 回到的仍是原厂桌面时，见「Google TV 的 Home 键」一节。

系统提示安装版本太旧时，请先核对系统版本和下载来源。本项目最低 Android 6.0；非 Android 电视及禁止第三方 APK 的固件不能使用。当前不提供 Google Play 安装。

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

## Google TV 的 Home 键

部分 Google TV（例如 TCL 海外版的原厂桌面 `com.google.android.apps.tv.launcherx`）的 HOME 入口优先级更高：即使已在系统中把静幕设为默认桌面，按遥控器 Home 仍回到原厂桌面。这是 Google TV 的已知行为，不代表静幕安装或默认桌面设置失败。

静幕提供可选的 **Home 键修复**，默认关闭，两步都完成才生效：

1. 桌面设置 → 系统与桌面 → Home 键设置 → 打开“Home 键修复”。
2. 按提示进入系统“无障碍”设置，启用“静幕 · Home 键修复”。返回后，面板会分别显示开关申请、系统启用状态和服务连接状态。

开启后由无障碍服务过滤遥控器 Home 键，把静幕调到前台。可在面板中准备验证，关闭面板后按一次 Home；只有返回静幕时才记为“最近一次请求已返回”。该服务只处理 Home 键，不读取界面内容，也不修改原厂桌面的启用状态；关闭开关或在系统无障碍设置停用服务即可恢复原行为。Home 键（含长按）交给静幕处理时，部分固件的长按快捷功能可能失效。固件也可能拒绝侧载应用的特殊权限，或允许启用但不转发按键；此时关闭功能并保留原行为。

Home 键过滤和默认 HOME 是两条独立路径。不要停用、卸载或替换原厂桌面来解决 Home 路由；系统的默认应用设置始终保留为可恢复入口。该功能尚无本轮真机验证，Google TV 与其他固件的按键结果可能不同。

## 背景

“桌面设置 → 背景与外观 → 自定义背景 → 从系统文件选择器导入”。支持常见 JPEG/PNG/WebP/BMP 与 MP4/WebM/MKV/MOV 文件；图片上限 50 MB，视频上限 400 MB。选择后复制到静幕自己的目录，不持续依赖 U 盘或文件授权。视频播放能力取决于系统解码器；建议从短小的 1080p H.264 MP4 开始，失败会显示内置风景。

没有系统文件选择器时，可选“U 盘 / 旧系统文件浏览”，按需授予存储读取权限。新系统可能限制旧式文件浏览，此时应使用系统选择器；拒绝授权仍能使用内置或已导入背景。

## 通用播放记录权限

在“桌面设置 → 继续播放”开启“显示播放记录”，再进入“记录来源与权限”开启“自动识别媒体播放”，通过“媒体访问权限”开启静幕的通知访问。应用名单可单独允许或关闭各应用记录；“高级设置”用于导入应用适配规则和清空桌面记录。静幕只读取媒体会话的播放字段，不读通知正文。部分新系统对侧载应用限制特殊权限，可在应用信息中查看是否有“允许受限设置”；没有此入口或厂商没有授权页时，保留基础桌面即可。

记录最多 8 项。应用不提供集数、封面或播放位置时，这些字段可能缺失。没有经验证的永久续播入口时显示“打开应用”；同一内容仍在活动会话中且可控制时显示“恢复当前播放”。可选 Watch Next 规则还需要电视系统提供 `android.media.tv` 和节目表读取权限；修改 JSON 无法补齐缺失的系统接口。Netflix 特定版本的限定验证见 [接入说明](NETFLIX-INTEGRATION.md)，不代表所有版本或设备支持；Disney+ 尚未实测。

## 升级

从 Releases 下载更新并覆盖安装，保留收藏、分类、背景、天气及记录。部分固件更新后可能重置默认 HOME，需在帮助页检查并重新选择。官方发布使用固定签名；自己构建的不同签名不能直接覆盖官方版，Android 会拒绝。不要因此直接卸载有数据的版本。正式 versionCode 必须递增，旧 APK 通常不能原地降级。

**0.12.0 升级到 0.13.0 是例外**：0.13.0 起发布签名密钥更换（此前的固定密钥不可用），0.12.0 及更早版本无法覆盖升级，必须先卸载再安装，应用内配置、收藏与记录会丢失。0.13.0 起恢复固定签名，后续版本回到覆盖升级。

0.14.1（versionCode 38）沿用 0.13.0 的发布签名，可覆盖 0.13.0、0.14.0 及同签名的本地候选版，保留应用数据。0.14 系列新增设置导航、播放规则与可选 Watch Next；旧个人版 Apple Music 专用续播、自动 IP 定位和目录补资料不在公开版范围内。

0.13.0（versionCode 21）：修复 Netflix 等无标题播放记录丢失，新增可选的 Home 键修复，安装指南补充 Play Protect 说明。升级注意见上一段。

0.12.0（versionCode 20）可覆盖开发工作区此前的 0.11 与回退包 19。已有配置和记录保留；全新安装默认关闭天气和记录。公开版移除专用音乐续播、自动 IP 定位、目录补资料与网络封面；旧天气须确认新服务条件并手动选城市才联网，旧音乐卡片仅打开应用。详见 [审查与范围](LEGAL-REVIEW.md)。

## 恢复原桌面

在系统“默认应用 / 主屏幕”重新选择原桌面。静幕设置中的“打开其他桌面”只切换一次画面，不修改默认值。恢复默认后，可保留或卸载静幕；卸载会删除它自己的配置和背景。

原厂桌面始终保留启用。本项目不改系统分区、启动属性、应用启用状态、CEC、屏保、开机广告或出厂恢复规则。恢复出厂设置由厂商实现，会清除数据；恢复原桌面一般无需出厂重置。

## 使用 ADB 的高级安装

设备地址用 `TV_IP:5555` 代替，不是统一的固定地址。需要自己在电视授权 ADB。

```powershell
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r QuietTV-v0.14.0.apk
# 打开界面（会切换电视画面）
adb -s TV_IP:5555 shell am start -n local.quiettv.launcher/.MainActivity
```

示例使用已发布的 0.14.0；新版本发布后替换文件名。也可使用 `install-preview.ps1 -Device TV_IP:5555 -Apk .\QuietTV-v0.14.0.apk`。默认仅安装；加 `-Launch` 才打开画面。

正常 ADB 安装也返回 `-22`，且已确认是固件校验兼容问题时，可显式添加 `-UseAdbVerifierWorkaround`。脚本读取原 `verifier_verify_adb_installs`，只在本次 ADB 安装期间临时修改，并在 `finally` 恢复原值、核对恢复结果。它不会关闭全局 `package_verifier_enable`，不会改变 Emotn 等应用内安装的来源白名单，也不保证所有固件均允许此选项。安装包仅从官方 Release 获取，先核对附带哈希及证书；不要用它代替文件来源审查。

从本仓库下载 [install-preview.ps1](../install-preview.ps1)，准备 Android 官方 [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)，并将下载的 APK 哈希与对应 Release 中的 `SHA256SUMS.txt` 对照。下面是显式启用兼容选项的完整示例，文件名随实际版本替换：

```powershell
Get-FileHash .\QuietTV-v0.14.0.apk -Algorithm SHA256
.\install-preview.ps1 -Device TV_IP:5555 -AdbPath .\platform-tools\adb.exe -Apk .\QuietTV-v0.14.0.apk -UseAdbVerifierWorkaround
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
