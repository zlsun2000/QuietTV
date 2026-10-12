# 安装、升级与恢复

## 不使用电脑

1. 从 [GitHub Releases](https://github.com/zlsun2000/QuietTV/releases/latest) 下载 `QuietTV-v0.13.0.apk`，不要下载名为 Source code 的源码压缩包。
2. 放入 U 盘，或通过自己的文件传输工具发送到电视。用电视文件管理器打开 APK。固件提示时，仅允许该文件管理器安装应用。
3. 安装后从应用列表打开“静幕”。方向键选择、确认键打开、长按确认键整理。内置背景、应用整理和时间可离线使用。
4. 首次帮助页可以选择默认桌面。若按 Home 出现选择器，选择静幕并选“始终”；也可到系统“默认应用 / 主屏幕”。这些入口可能被电视厂商移除。
5. 天气和播放记录可在“桌面设置 → 天气与继续播放”按需启用。无需为了基础桌面开启通知访问权限。

安装成功、Home 返回静幕、完全重启自动进入静幕是三个不同项目。请分别核对。部分固件固定开机入口，第三方 APK 无法保证接管；不能通过删除或停用原桌面解决。按 Home 回到的仍是原厂桌面时，见「Google TV 的 Home 键」一节。

系统提示安装版本太旧时，请先核对系统版本和下载来源。本项目最低 Android 6.0；非 Android 电视及禁止第三方 APK 的固件不能使用。当前不提供 Google Play 安装。

## Play Protect 拦截安装

用文件管理器打开 APK 并允许安装后，带 Google Play 服务的设备仍可能被 Play Protect 弹窗拦截，提示类似「為保護裝置，系統已封鎖應用程式」（具体文案随系统语言和版本而异）。这是 Play Protect 对非 Play 商店来源 APK 的常规拦截：静幕不在 Play 商店分发，侧载安装时可能遇到这一提示；没有 Google Play 服务的设备不会出现。安装包请只取自 GitHub Releases。

两种常见做法，入口名称因设备、系统版本和地区而异，也可能被厂商移除：

1. 临时关闭扫描：Play 商店 → 头像 → Play Protect → 设置 → 关闭“使用 Play Protect 扫描应用”，装完后可重新打开。
2. 授权安装来源：系统设置 → 应用 → 特殊应用权限 → 安装未知应用，给实际用于打开 APK 的文件管理器或浏览器授权。这与安装步骤中“仅允许该文件管理器安装应用”是同一处授权；已授权仍被拦截时，拦截来自 Play Protect 的扫描提示。

两种做法都由用户自行取舍，静幕不修改这些设置。以上入口未在各厂商固件逐一验证；无法调整时可改用下文「使用 ADB 的高级安装」。

## Google TV 的 Home 键

部分 Google TV（例如 TCL 海外版的原厂桌面 `com.google.android.apps.tv.launcherx`）的 HOME 入口优先级更高：即使已在系统中把静幕设为默认桌面，按遥控器 Home 仍回到原厂桌面。这是 Google TV 的已知行为，不代表静幕安装或默认桌面设置失败。

静幕提供可选的 **Home 键修复**，默认关闭，两步都完成才生效：

1. 桌面设置 → Home 键修复 → 打开“Home 键修复”。
2. 按提示进入系统“无障碍”设置，启用“静幕 · Home 键修复”。返回静幕后，同一面板会核对服务是否真正启用。

开启后由无障碍服务过滤遥控器 Home 键，把静幕调到前台。该服务只处理 Home 键，不读取界面内容，也不修改原厂桌面的启用状态；关闭开关或在系统无障碍设置停用服务即恢复原行为。代价是 Home 键（含长按）交给静幕处理，部分固件为长按 Home 分配的快捷功能会随之失效。部分固件限制侧载应用的特殊权限，无障碍列表中可能没有静幕，或启用后不生效；此时保留原行为即可。

用户侧还有以下做法，静幕未验证也不执行：

- 用 ADB 停用原厂桌面：`adb -s TV_IP:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx`，恢复用 `adb -s TV_IP:5555 shell pm enable com.google.android.apps.tv.launcherx`。这会停用原厂桌面，与本项目保留原厂桌面的原则冲突，也不能解决开机入口固定的问题；是否取舍由用户自行决定。静幕自身永不执行 pm enable/disable。
- 用 Button Mapper 一类按键映射工具把 Home 映射到静幕。这类工具同样需要无障碍权限，行为以各自应用为准。

以上均未在 Google TV 实机验证，其他厂商固件的结果可能不同。

## 背景

“桌面设置 → 背景与卡片 → 自定义图片 / 动态视频 → 系统文件选择器”。支持常见 JPEG/PNG/WebP/BMP 与 MP4/WebM/MKV/MOV 文件；图片上限 50 MB，视频上限 400 MB。选择后复制到静幕自己的目录，不持续依赖 U 盘或文件授权。视频播放能力取决于系统解码器；建议从短小的 1080p H.264 MP4 开始，失败会显示内置风景。

没有系统文件选择器时，可选“U 盘 / 旧系统文件浏览”，按需授予存储读取权限。新系统可能限制旧式文件浏览，此时应使用系统选择器；拒绝授权仍能使用内置或已导入背景。

## 通用播放记录权限

启用“其他应用自动识别”，再进入“媒体识别权限”开启静幕的通知访问权限。静幕只读取媒体会话的播放字段，不读通知正文。部分新系统对侧载应用限制特殊权限，可在应用信息中查看是否有“允许受限设置”；没有此入口或厂商没有授权页时，保留基础桌面即可。

记录最多 8 项。应用不提供集数、封面或播放位置时，这些字段可能缺失。没有经验证的永久续播入口时显示“打开应用”；同一内容仍在活动会话中且可控制时显示“恢复当前播放”。Netflix/Disney+ 的具体版本尚未实测。

## 升级

从 Releases 下载更新并覆盖安装，保留收藏、分类、背景、天气及记录。部分固件更新后可能重置默认 HOME，需在帮助页检查并重新选择。官方发布使用固定签名；自己构建的不同签名不能直接覆盖官方版，Android 会拒绝。不要因此直接卸载有数据的版本。正式 versionCode 必须递增，旧 APK 通常不能原地降级。

**0.12.0 升级到 0.13.0 是例外**：0.13.0 起发布签名密钥更换（此前的固定密钥不可用），0.12.0 及更早版本无法覆盖升级，必须先卸载再安装，应用内配置、收藏与记录会丢失。0.13.0 起恢复固定签名，后续版本回到覆盖升级。

0.13.0（versionCode 21）：修复 Netflix 等无标题播放记录丢失，新增可选的 Home 键修复，安装指南补充 Play Protect 说明。升级注意见上一段。

0.12.0（versionCode 20）可覆盖开发工作区此前的 0.11 与回退包 19。已有配置和记录保留；全新安装默认关闭天气和记录。公开版移除专用音乐续播、自动 IP 定位、目录补资料与网络封面；旧天气须确认新服务条件并手动选城市才联网，旧音乐卡片仅打开应用。详见 [审查与范围](LEGAL-REVIEW.md)。

## 恢复原桌面

在系统“默认应用 / 主屏幕”重新选择原桌面。静幕设置中的“打开其他桌面”只切换一次画面，不修改默认值。恢复默认后，可保留或卸载静幕；卸载会删除它自己的配置和背景。

原厂桌面始终保留启用。本项目不改系统分区、启动属性、应用启用状态、CEC、屏保、开机广告或出厂恢复规则。恢复出厂设置由厂商实现，会清除数据；恢复原桌面一般无需出厂重置。

## 使用 ADB 的高级安装

设备地址用 `TV_IP:5555` 代替，不是统一的固定地址。需要自己在电视授权 ADB。

```powershell
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r QuietTV-v0.13.0.apk
# 打开界面（会切换电视画面）
adb -s TV_IP:5555 shell am start -n local.quiettv.launcher/.MainActivity
```

也可使用 `install-preview.ps1 -Device TV_IP:5555 -Apk .\QuietTV-v0.13.0.apk`。默认仅安装；加 `-Launch` 才打开画面。默认不修改安装验证设置。仅遇到已确认的固件安装验证兼容问题时，可显式加 `-UseAdbVerifierWorkaround`，它会暂时修改 ADB 验证开关并在 finally 中核对恢复。

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
