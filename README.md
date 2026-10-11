# 静幕 QuietTV

一个开箱可用，也方便借助 AI 定制的开源 Android 电视桌面。原生 Java 实现，无广告，以应用、自然背景、时间和可选的继续播放为中心。

[下载 APK](https://github.com/zlsun2000/QuietTV/releases/latest) · [安装与恢复](docs/INSTALL.md) · [AI 定制指南](docs/CUSTOMIZATION.md) · [兼容范围](docs/COMPATIBILITY.md)

![内置自然山湖背景](res/raw/nature_lake.png)

## 0.12.0 首个公开版本

基础桌面适用于允许侧载 APK 的 Android 6.0 及以上电视/盒子；不要求 root、ADB 或停用原桌面。固定签名 APK 在 Releases 发布，源码采用 [MIT](LICENSE)。**跨厂商兼容仍需实机反馈**，开机接管、HDMI 和精确续播分别按设备和应用能力开放。

- 遥控器方向键导航，确认打开，长按整理；收藏、隐藏、手动排序、自定义分类、横排与网格。
- 常用应用与继续播放左右分区，完整卡片行滚动，边缘箭头提示隐藏内容方向。
- 本地图片、静音循环视频、内置自然背景；支持系统文件选择器与旧电视 U 盘浏览。
- 时间、日期、紧凑可选天气；手动城市、非商业免费 API 条件、来源与许可入口；天气和播放记录在全新安装时默认关闭。
- 最多 8 条播放记录。标准 MediaSession 识别应用公开的信息；B 站、奇异果 TCL 渠道版兼容已有历史广播，不含音乐客户端补丁。
- 默认桌面状态和恢复帮助；系统入口自动检测。未知输入能力时隐藏增强入口，不沿用其他电视的 HDMI 列表。

联网失败、权限拒绝或增强接口缺失时，基础桌面仍可用。不同应用的“继续播放”“打开内容”“打开应用”按实际能力显示，不能保证所有视频 App 重启后精确续播。不查询平台目录或下载远程封面，缺失字段用应用图标或留空。音乐客户端、专用续播接口与账号工具不包含在公开项目中。

## 直接使用

下载 Release 的 APK → 通过 U 盘或文件传输安装 → 从应用列表打开静幕。首次帮助页可进入默认桌面设置，天气和记录按需开启。按 Home 返回与完全重启自动进入需分别核对，具体取决于厂商固件。

恢复原桌面时在系统默认应用中重新选择原 HOME。静幕不修改系统分区、停用应用、开机广告或屏保；一般无需恢复出厂设置。完整步骤见 [安装、升级与恢复](docs/INSTALL.md)。

## 开发与 AI 定制

Windows：运行 `build.ps1`，再运行 `tests/run-core.ps1`。Linux/macOS：`QUIETTV_RUN_CHECKS=1 bash build.sh`。需要 JDK 11+、Android Platform 28 和 Build Tools。详细命令、签名和环境变量见 [构建说明](docs/BUILD.md)。

先修改 `res/raw/ui_defaults.properties` 即可调整网格列数、时间字号、天气宽度和左右分区比例。更多布局、机型和媒体适配任务见 [AI 定制指南](docs/CUSTOMIZATION.md)、[架构](docs/ARCHITECTURE.md) 与 [AGENTS.md](AGENTS.md)。自建版本使用自己的签名，不能直接覆盖官方安装。

## 文档与参与

| 文档 | 内容 |
| --- | --- |
| [兼容范围](docs/COMPATIBILITY.md) | 已验证设备、媒体来源、未验证能力与实机验收 |
| [隐私说明](PRIVACY.md) | 本地配置、可选网络服务、权限及数据清理 |
| [贡献指南](CONTRIBUTING.md) | 反馈机型、提交适配与 AI 生成修改 |
| [安全报告](SECURITY.md) | 安全问题的私密反馈方式 |
| [来源与许可](THIRD_PARTY_NOTICES.md) · [发布前审查](docs/LEGAL-REVIEW.md) | 资源来源、外部服务条件与排除范围 |
| [版本记录](CHANGELOG.md) | 从早期个人定制到首个公开版本 |
| [发布说明](docs/RELEASING.md) | 固定签名、版本号、校验与发布流程 |

实机开发始于雷鸟 50F295C-JN；物理 4K 面板向应用提供 1080p 窗口。布局依实际窗口缩放；不会为了显示 4K 而修改系统分辨率。更多品牌欢迎携带实际型号、Android 版本和复现步骤反馈。

界面独立实现，交互参考 [FLauncher](https://gitlab.com/flauncher/flauncher)，视觉参考 [Emotn UI](https://app.emotn.com/ui/)；没有复制其代码、商标、截图或壁纸。内置风景为原创 AI 生成，见 [资源说明](BACKGROUND-ASSETS.md)。本仓库 MIT 不涵盖 Android SDK、第三方应用或用户导入的媒体。
