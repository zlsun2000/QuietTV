<div align="center">

# 静幕 QuietTV

**打开电视，接着看。**

一个免费、开源的 Android TV 桌面，把常用应用、继续观看和输入源放在同一屏。

**中文** · [English](README.en.md)

[下载 APK](https://github.com/zlsun2000/QuietTV/releases/latest) · [安装指南](docs/INSTALL.md) · [AI 适配 Prompt](RULE-PROMPT.md) · [社区规则](playback-rules/community/README.md)

</div>

![QuietTV 实机桌面：左侧常用应用，右侧 Netflix 继续观看卡片，下方输入源](docs/images/quiettv-home.png)

<p align="center"><sub>QuietTV 0.14.2 · Android 12 实机 · 用户选择公开的运行画面。卡片展示已有记录，实际续播由媒体应用决定。<a href="docs/SCREENSHOTS.md">截图来源与第三方素材说明</a></sub></p>

## 让遥控器少按几下

- **从桌面接着看**：支持的应用可显示最近内容、封面和真实进度，最多保留 8 条记录。确认键尝试续播，具体能力如实标在卡片上。
- **常用应用就在手边**：收藏、排序、隐藏图标和分类；方向键移动，长按确认键整理。
- **直接进入输入源**：电视提供可用入口时，从桌面切换 HDMI、TV 或 AV；名称和状态以设备提供的信息为准。
- **把桌面变成自己的**：内置山湖背景，也能导入图片或静音循环视频；时间常驻，天气按需开启。
- **保留原厂桌面**：基础安装无需 ADB，不需要 root、刷机或停用系统包。默认桌面由你选择，也可以切回去。

桌面免费、无广告。播放记录和天气在全新安装时默认关闭；记录保存在电视本地。静幕不提供影视内容，播放仍使用原应用及其账号、会员和服务规则。

## 三步开始使用

适用于**允许安装第三方应用的 Android 6.0+ 电视或盒子**。

1. 从 [Releases](https://github.com/zlsun2000/QuietTV/releases/latest) 下载 `.apk` 安装包，普通用户不需要下载源码或编译。
2. 用 U 盘或文件传输工具送到电视，在文件管理器里打开并安装。
3. 从应用列表打开“静幕”，按提示整理应用；电视允许时，再选择设为默认桌面。

**安装成功、Home 键返回静幕、开机进入静幕是三件事。** 厂商可能分别限制它们；部分机型需要额外设置或 ADB 排查。“基础安装无需 ADB”不代表所有机型、权限和适配调查都无需 ADB。请保留原厂桌面。

**升级：** 官方 0.13.0–0.14.1 可直接安装 0.14.2，保留设置。0.12.0 及更早版本涉及签名变更；卸载会删除配置、背景和记录，先看[升级与恢复说明](docs/INSTALL.md#升级)。

## 续播：把“支持到哪一步”说清楚

静幕通过电视的 **Watch Next（继续观看记录）**、应用广播或 **MediaSession（当前播放信息）** 获取记录，再通过经过校验的入口交回原应用播放。应用没有提供的信息，静幕不会凭空补齐。

| 适配文件 | 已有证据 | 使用边界 |
| --- | --- | --- |
| [Netflix Watch Next](docs/playback-rules/netflix-watch-next.json) | Android 12、Netflix 13.2.1 / build 27207，所有者确认点击续播与海报显示 | 需系统节目表权限；进度由 Netflix 决定，其他版本、资料切换与冷启动未验证 |
| [TCL 奇异果 16.9](docs/playback-rules/qiyiguo-tcl-16.9.json) | 单台 Android 9 电视个人测试版确认按应用历史续播 | 需要 QuietTV 0.14.2；可能进入应用当前所看的集数，公开 APK 尚未在该机重验 |
| [SmartTube 32.56](docs/playback-rules/smarttube-32.56-session.json) | 同机个人测试版确认关闭客户端后按进度续播 | 限指定包、版本和签名；公开 APK 尚未在该机重验，旧 SmartYouTube 不适用 |
| [TCL 渠道 B 站](docs/playback-rules/bilibili-tcl.json) | 早期渠道版本曾成功 | 当前 1.7.7 尚未成功适配，不能视为通用 B 站支持 |

导入路径：**桌面设置 → 继续播放 → 高级设置 → 应用适配规则 → 导入 JSON 规则**。先核对版本和说明，保存后手动启用，再到原应用正常播放一次。所需权限按适配方式分别开启。

能打开应用、能打开内容、能恢复当前播放、能传递进度，是不同能力。**显示进度或通过配置检查，不等于已验证精确续播。** [完整兼容说明](docs/COMPATIBILITY.md) · [配置使用步骤](docs/PLAYBACK-RULES.md#在电视上使用)

## 和其他方案怎么选

根据各项目官方资料整理于 **2026-10-20**。cleanyourtv 原名 tv-vibehack；Projectivy 使用其正式名称。表中的“无需 ADB”均指普通安装和打开应用，不包含每种固件上的默认桌面替换。

| 你关心的事 | **QuietTV** | **cleanyourtv** | **FLauncher** | **Projectivy Launcher** |
| --- | --- | --- | --- | --- |
| 主要用途 | 简洁桌面 + 可导入的续播适配 | AI 辅助精简系统应用 + 定制桌面 | 简洁应用桌面 | 功能丰富的可定制桌面 |
| 是否需要 ADB | 基础安装不需要；进阶操作可选 | 官方改造流程需要 | 安装不需要；可选默认桌面方案使用 ADB | 安装不需要；默认桌面行为依设备与设置 |
| 是否动系统包 | 不停用、不卸载原厂包 | 精简流程会按用户停用系统包，提供恢复工具 | 普通使用不需要；官方另列停用原桌面的可选方案 | 普通桌面使用不需要；额外设备工具另计 |
| “变砖”与恢复边界 | 不刷机、不改系统分区；保留原桌面可切回 | 不刷机；误停依赖包可能影响 Home 或系统功能，需恢复 | 普通使用不涉及刷机；停用原桌面的方案有额外风险 | 普通使用不涉及刷机；不把额外系统工具算入普通安装 |
| 桌面续播 | 按应用规则和实测版本限定 | 官方主项目未列出通用续播；衍生项目另计 | 官方功能列表未列出续播 | 支持 Watch Next；依赖应用提供记录与入口 |
| 预编译 APK | [GitHub Releases](https://github.com/zlsun2000/QuietTV/releases/latest) | 官方说明不提供，需自行构建 | [GitLab Releases](https://gitlab.com/flauncher/flauncher/-/releases) / Google Play | [GitHub Releases](https://github.com/spocky/miproja1/releases) / Google Play |
| 收费 | 免费，MIT 开源 | MIT 开源；AI 服务费用另计 | 免费、开源 | 基础功能免费，部分个性化功能需 Premium |

来源：[cleanyourtv README](https://github.com/Gathin23/cleanyourtv#readme) · [FLauncher 仓库](https://gitlab.com/flauncher/flauncher) · [Projectivy 官网与 Premium 说明](https://projectivylauncher.com/) · [Projectivy 商店说明](https://play.google.com/store/apps/details?id=com.spocky.projengmenu)。

这是功能与操作范围对比，不是同机性能测试，也不是故障概率排名。**不刷机不等于零风险**；停用系统依赖与普通安装的影响不同。未列出功能不代表绝对不存在，欢迎附官方来源纠正。

## 把这段话交给 AI

在 Claude Code、Codex 等能读文件和执行命令的助手中使用。先填方括号；AI 服务是否收费由你选择的平台决定。

### 帮我安装 QuietTV

```text
请帮我安装 QuietTV：https://github.com/zlsun2000/QuietTV
电视型号：[填写]；Android 版本：[填写或未知]。
安装方式：[U 盘 / ADB]；若选 ADB，目标地址：[仅在本机提供]。

先读仓库 README 和 docs/INSTALL.md。优先下载官方最新稳定 Release
中的 APK，核对发布页哈希与签名说明，不要为了普通安装从源码构建。
U 盘方式请给我能照着做的步骤；我选择 ADB 时，授权你只向上述设备
安装或同签名覆盖升级官方 APK，保留数据，不因签名冲突擅自卸载。
保留原厂桌面和系统包。默认桌面、额外权限、规则启用与播放测试
以我的明确选择为准；已有授权无需重复询问，未授权时不要扩大操作。
最后告诉我安装结果、如何打开和如何恢复原桌面；不要公开设备地址或日志。
```

### 帮我改造自己的桌面

```text
请基于 https://github.com/zlsun2000/QuietTV 定制我的桌面。
目标：应用网格每行 4 个，时钟字号 48；其他想法：[填写]。

先读 AGENTS.md（如有）、docs/BUILD.md、docs/ARCHITECTURE.md、
docs/CUSTOMIZATION.md 和 docs/WRITING.md，保留我已有的未提交修改。
已有 UI 参数优先改 res/raw/ui_defaults.properties，保持遥控器操作、
焦点、完整行显示、窗口缩放和原有数据。媒体适配优先用独立 JSON。
用我自己的签名构建，保管密钥并通过安全输入提供密码，不写入 Git。
先完成基线构建和核心检查，再修改并重新验证，交付 diff、APK 和验证边界。
这次只在电脑上改造；安装到电视或发布前等我明确授权。
```

### 帮我给常用应用做续播适配

**复制 [RULE-PROMPT.md 的快速版](RULE-PROMPT.md#快速版只填应用名)**，只填应用名即可开始，生成的规则标为 `unverified`。不确定时用[完整版](RULE-PROMPT.md#完整版)，补充版本和有权提供的接口资料。AI 会先整理证据；资料不足时列出缺口，不编造可用配置。

**一段 Prompt → 一份有来源的 JSON → 一台设备的验证结果 → 一份社区适配。**

规则无需重新编译桌面，导入后默认停用，由用户核对再开启。[规则规范与 Schema](docs/PLAYBACK-RULES.md) · [现有适配的开发过程](docs/QIYI-SMARTTUBE-DEVELOPMENT.md)

## 把验证结果留给下一个人

[社区规则库](playback-rules/community/README.md) 收录经审阅的独立适配及版本证据。可以[提交你的设备验证结果](https://github.com/zlsun2000/QuietTV/issues/new?template=device_verification.yml)，或按[贡献格式](playback-rules/community/TEMPLATE.md)提交规则。

成功、只能打开内容、未成功、升级后失效，都值得记录。请写清设备、系统、应用版本和实际结果；只分享脱敏说明与合成样例，不上传账号、完整播放链接、私人日志或第三方 APK。收录不代表所有电视都兼容，也不会自动下发到用户设备。

分享自己的桌面或原创规则时，可以带上 `#vibecoding` `#claudecode`，附项目链接和实际验证范围。

## 使用前知道这几件事

- **权限可选**：媒体会话识别使用系统“通知访问”权限，但不读取通知正文；Watch Next 另需电视节目表权限。不授权仍可使用基础桌面。
- **在线海报按需启用**：带联网海报的规则会向图片服务发送请求，对方可看到 IP 与图片路径；Netflix 示例包含这项声明。先阅读规则说明，再决定启用。
- **天气默认关闭**：手动选城市，不通过 IP 自动定位；保留 Open-Meteo / GeoNames 来源说明，当前免费天气 API 仅供非商业使用。
- **能力依设备而异**：输入源、Home 键和视频背景受固件或解码能力限制；没有接口时不承诺补齐。

## 继续了解

[安装与恢复](docs/INSTALL.md) · [兼容情况](docs/COMPATIBILITY.md) · [AI 定制指南](docs/CUSTOMIZATION.md) · [从源码构建](docs/BUILD.md) · [贡献与反馈](CONTRIBUTING.md) · [更新记录](CHANGELOG.md)

源码采用 [MIT 许可](LICENSE)。[隐私说明](PRIVACY.md) · [第三方素材与服务](THIRD_PARTY_NOTICES.md) · [来源审查](docs/LEGAL-REVIEW.md)。第三方应用、媒体、商标与服务不随源码获得 MIT 授权，也不表示官方合作。
