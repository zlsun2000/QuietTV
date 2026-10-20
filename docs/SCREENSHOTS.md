# README 与发布页截图来源

## 当前 README 图片：0.14.2 实机

`images/quiettv-home.png` 为设备所有者在 2026-10-20 明确选定用于 README 的实际桌面画面，同时用于中英文 README。源文件为本机忽略目录中的 `build/diagnostic-frame.png`；原视频和其他设备捕获不纳入 Git。

- 环境：一台 TCL Android 12（API 31）电视，系统报告型号 Smart TV Pro；画面 1920 × 1080。型号字符串不能代表所有同名或同品牌电视。
- 应用：官方 [QuietTV 0.14.2](https://github.com/zlsun2000/QuietTV/releases/tag/v0.14.2)，versionCode 39；对应发布源码 `d767c17085128fafbc1e5636d85f01b76cadd635`。
- 来源：所有者授权的 ADB 录屏中约 0.1 秒处提取的 PNG；本次按用户指定原样复制，没有裁剪、拼接、改绘或替换界面内容。
- 文件：754,025 字节；SHA-256 `b344154df7e0f2e9622dcd107641d3d937ff1e86a4d4d9e902d4b595938e2584`。
- 画面：内置山湖背景、Netflix / YouTube 应用卡片、Netflix 继续观看卡片、输入源、时间和天气。

### 内容与公开范围

已查看完整画面：没有账号、资料名、邮箱、设备地址、凭据、通知正文或完整播放链接。画面**包含**所有者主动选择公开的《絕命律師》标题、12:20 / 1:00:33 进度、香港天气城市及 PlayStation 5 设备名称；不能把它描述为“无观看记录”或模拟器示例。所有者于 2026-10-20 审阅 README 稿件后明确授权发布。

Netflix 海报来自该应用提供的系统 Watch Next 记录；运行时来源与范围见 [ARTWORK-REVIEW.md](ARTWORK-REVIEW.md)。应用标志、节目标题、角色形象和海报属于各自权利人，仅作为这一张真实界面截图中的组成部分说明产品行为；不抽取为壁纸、独立素材或 APK 资源，也不纳入项目 MIT 许可。用户同意公开自己的画面不等于取得第三方海报的再许可，不表示 Netflix / YouTube / Sony 对项目授权或背书。本次来源记录不构成普遍再分发许可或无争议保证。

内置背景来源见 [BACKGROUND-ASSETS.md](../BACKGROUND-ASSETS.md)。天气界面保留 Open-Meteo 署名；天气与城市数据许可仍按 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)执行。

这张静态图片能说明桌面显示了已有记录和焦点，**不能证明点击后的播放位置、指定集数、冷启动或跨设备兼容性**。原录制文件不足 10 秒，不将其宣传为完整续播演示。行为证据与局限以 [COMPATIBILITY.md](COMPATIBILITY.md) 为准。

## 历史 README 图片：0.12.0 模拟器

以下保留原图片的来源记录。它已被上面的实机画面替换，不再对应当前 `images/quiettv-home.png` 的字节。旧图可在替换前的 Git 历史中找到。

### 生成记录

- 日期：2026-10-11。
- 环境：隔离 Android TV 9（API 28）x86 模拟器，1920 × 1080。
- 来源提交：`7de6033b0d62511cdfb47adb1bc899660733ae6d`。其应用源码、Manifest、资源和构建脚本与 0.12.0 发布提交 `4c533dbac2dab6b3251add58ac57c8cae6a19cee` 相同；变更仅涉及文档截图流程。
- [截图任务与检查结果](https://github.com/zlsun2000/QuietTV/actions/runs/38137315277)：构建、安装、导航、导入和升级检查成功后，在新建模拟器内配置桌面并截图；没有连接用户电视。
- 使用 Android 的 `screencap` 直接导出 PNG，确认前台窗口属于静幕。文档保存原始字节，没有裁剪、缩放、叠加或改绘界面。
- PNG SHA-256：`79d837933dc02f6b242f10c059d335da48f12b5badddcbb6173fa0d0ee1e6400`。

### 内容与公开检查

公开截图由用户明确要求。人工查看完整画面，确认应用卡片与遥控器焦点正常，无私人账号、观看记录、城市位置、用户导入背景或影视封面。天气与播放记录关闭，使用独立配置和内置风景；不导入个人电视的备份。

背景为项目内置图，来源见 [背景资源说明](../BACKGROUND-ASSETS.md)。画面中的 System Tracing、Videos by Google 图标与名称来自模拟器的系统应用，属于各自权利人，不纳入项目 MIT 许可，也不表示合作或背书；没有把这些应用或图标作为独立资源打包分发。另见 [素材与服务来源](../THIRD_PARTY_NOTICES.md)。

这张图片展示公开版的基础桌面，不表示天气、所有媒体应用续播或其他厂商电视均已实测。兼容边界见 [兼容说明](COMPATIBILITY.md)。
