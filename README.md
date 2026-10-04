# 静幕 QuietTV

一个简洁的原生 Android 电视桌面，包名 `local.quiettv.launcher`，当前版本 **0.7**。界面行为参考 FLauncher，视觉布局参考 Emotn UI，以原生 Java 与 Canvas 独立实现。

![内置自然山湖背景](res/raw/nature_lake.png)

## 功能

- 五列大卡片、时钟、日期、选中应用信息和快捷设置入口。
- 遥控器导航，长按确认键整理应用；支持常用、隐藏、手动排序、自定义分类、横排和网格。
- 滚动时只绘制完整卡片行，焦点进入下一行时将整行带入视野。
- 自然图片背景、缓慢缩放平移动效、自定义图片和静音循环视频；可从 U 盘或本地导入。
- 在已验证的 TCL 固件上直接进入 TV、HDMI 1、HDMI 2、AV 1，读取接入状态及可获得的设备名称。
- 支持 Direct Boot；用户解锁前显示内置背景，解锁后读取原有收藏和背景。
- 无广告，不申请网络、无障碍或设备管理权限。媒体读取权限仅在用户导入背景时申请。

自定义背景和桌面配置只保存在电视本地。离开桌面后图片动画停止，视频播放器释放；不修改系统音量，不保持屏幕唤醒。

## 兼容范围

最低 Android API 23，构建目标 API 28。当前实机验证型号为雷鸟 **50F295C-JN**；输入源和“电视设置”使用该固件的 TCL 接口，不能假定兼容其他电视。

该电视的物理面板为 3840×2160，但固件向应用提供 1920×1080 的桌面窗口。界面以 1280×720 为设计坐标，按实际窗口缩放，可计算 1080p 和 4K 布局；这台电视未实测原生 4K 桌面缓冲区。内置图片实际为 1672×941，可以自行导入更高分辨率图片。

## 构建

这是使用 Android SDK 命令行工具的轻量项目，不依赖 Gradle。需要 Windows PowerShell、JDK 14 或以上版本、Android SDK Platform 28 和 Build Tools。源码位于 `src/`，资源位于 `res/`。

```powershell
.\build.ps1 `
  -BuildTools 'C:\Android\Sdk\build-tools\28.0.3' `
  -AndroidJar 'C:\Android\Sdk\platforms\android-28\android.jar' `
  -JavaBin 'C:\Program Files\Java\jdk-17\bin'
```

脚本会安全提示输入本地签名密码；自动构建也可通过 `QUIETTV_KEYSTORE_PASSWORD` 环境变量提供。首次构建在本地生成 `local-test.jks`；之后用同一密钥更新应用。可用 `-Keystore` 指定另一密钥，密钥别名应为 `quiettv`。

输出为 `QuietTV.apk`。签名密钥、密码、APK、构建产物、SDK 工具、电视诊断记录和旧版备份不提交到仓库。已有电视安装使用的私有签名密钥没有公开，从仓库重新生成的密钥不能直接覆盖原签名安装。

本地原工作目录仍支持从相邻 `android-tools/` 读取构建工具和平台 JAR；其他电脑应显式提供上述路径。JDK 可由 `-JavaBin`、`JAVA_HOME` 或 PATH 中的 `javac.exe` 定位。

## 安装与默认桌面

先在电视设置中启用 ADB，通过同一局域网连接并在电视端授权。下面的 `TV_IP` 是占位符，请替换为实际地址。

```powershell
adb connect TV_IP:5555
.\install-preview.ps1 -Device 'TV_IP:5555' -AdbPath 'C:\Android\platform-tools\adb.exe'
```

安装脚本保留应用数据，临时调整 ADB 安装验证并在 `finally` 中恢复原值，然后打开静幕。如果静幕原本就是默认 HOME，会在 APK 替换后恢复并核对该偏好；首次预览不会主动设为默认桌面。

确认可用后，可以单独设置默认 HOME：

```powershell
adb -s TV_IP:5555 shell cmd package set-home-activity --user 0 local.quiettv.launcher/.MainActivity
```

原厂桌面保留启用。无需 root，不修改系统分区、厂商开机属性或原厂启动组件。当前版本不接管自动黑屏、屏保、开机广告或应用内部页面。

## 恢复

在已验证的 TCL 固件上恢复原厂默认桌面：

```powershell
.\Restore-System-Home.ps1 -Serial 'TV_IP:5555' -AdbPath 'C:\Android\platform-tools\adb.exe'
```

脚本恢复 `com.tcl.cyberui` 的默认启用状态，并将其设为默认 HOME；默认不切换画面，加 `-Launch` 才打开原厂桌面。静幕及其配置仍保留。也可从静幕的“桌面设置”返回原厂桌面。

卸载静幕会删除它的收藏、分类和背景数据。恢复出厂设置的具体范围由电视固件决定。

## 已验证与限制

0.3 曾验证四个输入源直接切换、遥控器导航、排序、隐藏与分类保存。0.5 经一次用户授权的实际重启，启动日志和用户观察均确认第一个 HOME 直接进入静幕。0.7 安装后核对默认 HOME 与 Direct Boot 资格，用户手动下翻后确认卡片显示正常；未重复完整冒烟测试。

尚未用实际接入设备验证 HDMI 热插拔、信号播放和具体设备名。没有读取到设备名时显示“设备名未知”，不猜测型号、不开启 CEC。自定义视频能否播放取决于电视解码器；失败时回退内置风景。电视设置进出尺寸一致性没有单独完成自动化确认。

## 参考与资源

[FLauncher](https://gitlab.com/flauncher/flauncher) 提供分类、卡片和长按交互参考；[Emotn UI](https://app.emotn.com/ui/) 提供风景背景与布局参考。项目没有复制这些产品的代码、商标、截图或壁纸。

内置自然山湖由 imagegen 生成，来源与提示词见 [BACKGROUND-ASSETS.md](BACKGROUND-ASSETS.md)。Direct Boot 行为遵循 [Android 官方说明](https://developer.android.com/privacy-and-security/direct-boot)。版本记录见 [CHANGELOG.md](CHANGELOG.md)。