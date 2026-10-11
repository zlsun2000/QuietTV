# 从源码构建

项目为原生 Java + Android SDK，使用平台 API，不使用 Gradle、Flutter、Node 或云端构建服务。发布 APK 不需要网络才能运行基础桌面。

## 工具

- JDK 11 或以上（本地验证 JDK 14；CI 使用 JDK 17），javac 使用 `--release 8`。
- Android SDK Platform 28：`platforms/android-28/android.jar`。
- Android SDK Build Tools（本地 Android 11 工具；CI 固定 34.0.0）。需要 aapt、zipalign、lib/d8.jar、lib/apksigner.jar。
- Windows PowerShell / PowerShell 7，或 Linux/macOS 的 bash。

SDK 从 [Android 官方工具](https://developer.android.com/tools) 安装，依赖工具不包含在本仓库。先用 SDK Manager 安装 Platform 28 和 Build Tools。

## Windows

```powershell
git clone https://github.com/zlsun2000/QuietTV.git
cd QuietTV
.\build.ps1 -BuildTools 'C:\Android\Sdk\build-tools\34.0.0' -AndroidJar 'C:\Android\Sdk\platforms\android-28\android.jar' -JavaBin 'C:\Program Files\Java\jdk-17\bin'
.\tests\run-core.ps1 -AndroidJar 'C:\Android\Sdk\platforms\android-28\android.jar' -JavaBin 'C:\Program Files\Java\jdk-17\bin'
```

设好 `ANDROID_HOME`、`JAVA_HOME` 后也可直接运行 `build.ps1`。没有 SDK 环境变量时，会尝试相邻 `android-tools/` 的旧开发布局；不要求他人复制开发者的电脑目录。

## Linux/macOS

```bash
export ANDROID_HOME="$HOME/Android/Sdk" # 换成自己的 SDK 目录
export JAVA_HOME="/path/to/jdk-17"
export QUIETTV_BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"
export QUIETTV_RUN_CHECKS=1
bash build.sh
```

可设置 `QUIETTV_ANDROID_JAR` 和 `QUIETTV_KEYSTORE` 指定平台与密钥。Windows 用 `-Keystore` 指定密钥；别名为 `quiettv`。

## 签名

首次构建提示输入密码并生成 `local-test.jks`，输出 `QuietTV.apk`。自动化通过 `QUIETTV_KEYSTORE_PASSWORD` 环境变量提供密码，脚本用 env 参数传给签名工具；不要把密码写入脚本、命令历史或 Git。

保存并离线备份自己的密钥，后续更新使用同一密钥且递增 versionCode。官方签名私钥不公开，自建 APK 无法覆盖官方安装；可使用独立包名安装自己的分支，见 [定制指南](CUSTOMIZATION.md)。不要将 CI 临时测试签名当作稳定签名。

每次构建会清理项目内固定 `build/` 目录，避免已删除 Java 类进入 APK。签名验证失败会停止。输出只含 manifest、项目资源和编译代码，不打包电脑或电视上的 preferences、导入背景与账号数据。

## 检查

核心 JVM 检查覆盖输入元数据、布局边界、导入类型/大小、媒体身份、进度、集数、路由以及封面主机限制。它们不能替代遥控器和固件实测。

CI 会从干净检出构建，使用临时密钥运行核心检查，并在独立 Android 模拟器核对全新安装与 HOME。维护者从 Releases 发布固定签名 APK；CI 不持有发布私钥。

公开源码与构建排除音乐补丁、账号工具和专用续播层。`tests/media-fixture/` 用于明确授权的媒体回调验证，不能把测试应用、测试记录或音频自动安装到用户电视。
