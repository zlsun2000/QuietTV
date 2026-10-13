# 兼容范围与验证

## 支持边界

最低 Android 6.0/API 23，当前 target SDK 28。APK 无原生 JNI 库，代码不按 ARM/x86 区分，但系统是否允许侧载、文件选择器、通知访问和默认 HOME 由固件决定。不兼容非 Android 系统，也未达到 Google Play 上架要求；提高 target SDK 需要另做权限、包可见性和生命周期迁移。

## 设备

| 环境 | 已知结果 | 限制 |
| --- | --- | --- |
| 雷鸟 50F295C-JN，Android 9 | 0.7–0.11 使用期间验证了导航、应用整理、重启 HOME、动态背景、左右分区与部分精确续播 | 物理面板 4K，应用窗口 1080p；0.12.0 新安装引导与通用分支独立验证，不假定同机旧版本测试覆盖所有新改动 |
| 所有者当前 TCL 电视，系统型号 Smart_TV_Pro / G09，Android 12/API 31 | 0.13.1-test 反馈确认重复/排序异常；0.13.2-test 所有者确认顺序和游戏机运行/待机显示正常，但空 HDMI1/3 与 AV 错报已连接 | 0.13.3-test 改为区分物理端口的模糊状态与逻辑设备状态，修正展示仍待验收；实际切换、拔插和背景导入尚未受控验证；不能代表其他 OEM |
| 隔离 Android TV 6 / 9 / 11 模拟器 | 已配置安装、首次打开、焦点、背景导入及升级；9/11 另测选定 HOME，结果以 workflow 为准 | Android 6 无对应 shell 命令，未模拟默认桌面选择；虚拟环境不能验证厂商开机限制、物理 HDMI、真实解码器及 DRM |
| 其他品牌电视/盒子与新版系统 | 能力检测和无权限降级已实现 | 尚需社区实机反馈；不承诺全部厂商完全兼容 |

布局的 720p/1080p/4K 比例经过 JVM 检查，原生 4K 缓冲区、低内存设备和非 16:9 设备仍需实测。跨设备报告要包含实际应用窗口分辨率，不能只写面板型号。

已观察到部分 Google Android TV 镜像原厂 HOME 具有更高优先级：`set-home-activity` 返回成功，实际解析仍可能是原厂。CI 单独记录 HOME 组件和未执行/受限项目，不能把这些当作接管成功；公开版保留原桌面，不为接管而停用它。为此提供可选、默认关闭的 Home 键修复（无障碍服务过滤 Home 键），尚未在 Google TV 实机验证过滤是否生效，也未核对长按 Home 的固件快捷功能，不作兼容承诺。API 30 的 `dumpsys window windows` 不包含当前焦点字段，截图检查使用完整 `dumpsys window`，并要求实际前台窗口属于静幕。

CI 保留 API 23/28/30 的电视镜像：Android 6 使用官方 720p 电视配置，9/11 使用 1080p。API 23/30 使用 [Google 官方存档](https://developer.android.com/studio/emulator_archive) 的 35.2.10 模拟器并核对官方 SHA-256，API 28 使用 SDK 安装的版本。旧 Android 6 镜像的 DMA 分配器在 1080p、720p 均发生内核崩溃，因此只对该模拟器关闭 GLDMA 加速传输；API 30 还出现过首次启动 ADB 离线。这些属于测试环境失败，不能记作桌面通过。界面含持续背景动画，设备检查等待窗口创建及实际绘制帧，不依赖全局消息队列空闲。私有截图由限定的模拟器 ADB 直接导出，并检查前台窗口和 PNG，避免 API 28 的 UiAutomation 连接超时及 API 30 的应用私有外部目录访问限制；兼容性 CI 的公开附件只保留文本日志。手动 README 截图流程仅导出隔离环境的离线桌面，人工检查后再加入文档。各次结果以 CI 日志为准，模拟器工具不进入 APK。

## 继续播放

| 来源 | 记录 | 点击行为 | 证据 |
| --- | --- | --- | --- |
| B 站 TCL 渠道版 | 应用广播提供内容/集数/进度；不联网补资料 | 匹配接口时指定内容与位置 | 开发电视授权测试成功；不是全部 B 站版本保证 |
| 音乐应用（含 Apple Music） | 可选标准 MediaSession；兼容旧记录 | 活动会话可恢复，否则打开应用 | 公开版排除专用接口，不承诺重启后恢复队列 |
| 奇异果 TCL 渠道版 | 已有历史广播或 MediaSession | 已核对专用路由打开内容，否则打开应用 | 合成广播及真实媒体记录已验证；真实精确位置续播未验证 |
| Netflix、Disney+、其他媒体应用 | 仅应用公开的 MediaSession 字段 | 同一活动内容可恢复，否则打开应用 | 具体版本尚未实机验证；不能承诺集数、封面或永久跳转 |

2026-10-13/14 所有者在当前 Android 12 电视使用 0.13.1-test 时报告 Netflix、YouTube 有桌面记录，B 站 `com.xiaodianshi.tv.yst` 1.7.7 无记录；没有受控验证内容、进度或续播。授权只读快照当时无 B 站活动会话，不能据此断言播放时也没有会话。0.13.2-test 增加标准会话状态快照，并修复 Android 8+ 兼容渠道隐式广播仅注册在 manifest 的接收缺口，仍需确认该版本 B 站是否发送现有契约；没有媒体接口的应用仍不能自动产生历史。背景导入尚未在该电视测试。

2026-10-14 所有者反馈 0.13.2-test 的 B 站记录仍无效，按要求推迟后续调查；本轮 0.13.3-test 只修输入状态展示，不增加媒体来源或接口。

标准输入的父子合并及待机状态依据 [TvInputInfo](https://developer.android.com/reference/android/media/tv/TvInputInfo#getParentId()) 与 [TvInputManager](https://developer.android.com/reference/android/media/tv/TvInputManager#INPUT_STATE_CONNECTED_STANDBY) 的公开契约；渠道广播运行时注册依据 [Android 8 广播限制](https://developer.android.com/about/versions/oreo/background#broadcasts)。这些契约不保证固件准确报告连接状态、提供端口标签或实际允许切换。未提供关系时保留独立输入，未提供排序数字时保持稳定排序；无输入登记的盒子不生成假 HDMI 卡片。

[AOSP 硬件实现](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r3/services/core/java/com/android/server/tv/TvInputHardwareManager.java) 的 `getInputStateLocked` 可在未更新线缆状态时依据流配置返回连接，HDMI 热插拔未知/关闭也可报告待机。[逻辑设备实现](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/tv/TvInputManagerService.java) 则根据 HDMI 设备电源状态更新子输入。0.13.3-test 因此单独处理逻辑设备状态与物理接入证据，标准物理端口未知时不显示已连接，也不凭缺少 CEC 条目断言未连接；这不是型号特判，不能保证检测所有 HDMI/AV 线缆或视频信号。

当前开发电视没有可利用的 Watch Next Provider。首版不采集各平台账户的完整历史，也不绕过订阅、DRM、应用内广告或清晰度限制。

## 实机验收

贡献适配时分别记录：全新安装、首次打开、Home、重启、休眠唤醒、遥控器全部功能、长按/排序/隐藏、左右滚动边界、背景导入与循环、拒绝权限、断网、升级保留配置、恢复原桌面。HDMI 实测应另列端口、接入/拔出、设备名可读性与实际切换；未知时就记未知。

不要在别人正在看电视时发送按键、打开内容或重启。模拟器通过与厂商实测是两类证据。旧个人版本与公开版功能边界不同，见 CHANGELOG 和 LEGAL-REVIEW。
