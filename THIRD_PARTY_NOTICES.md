# 来源、数据许可与第三方名称

核对日期：2026-10-11。对应公开版 0.12.0；另见 [发布前审查](docs/LEGAL-REVIEW.md)。MIT 只适用于项目有权许可的内容，不为第三方服务、媒体或商标授权。

APK 的 `res/raw/legal_notices.txt` 同时包含完整 MIT 版权/许可证及外部数据来源说明，构建审查会核对其内容。

| 项目 | 来源与用途 | 权利及发布边界 |
| --- | --- | --- |
| Java 桌面、脚本、XML 图标和横幅 | 本项目开发；没有引入第三方运行库或第三方 APK | 项目代码采用 MIT；Android 平台 API/SDK 的许可独立适用，SDK 不随仓库分发 |
| `res/raw/nature_lake.png` | 无参考图片的 AI 生成山湖图，提示词见 BACKGROUND-ASSETS.md | 项目允许按 MIT 使用其可许可权益；不保证 AI 图像独占性或在所有地区的著作权成立 |
| 天气数据 | [Open-Meteo](https://open-meteo.com/en/licence) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)；界面署名并提供链接，温度取整、状态翻译；免费 API 须符合 [非商业访问条款](https://open-meteo.com/en/terms) |
| 城市搜索数据 | Open-Meteo geocoding，基础城市库 [GeoNames](https://www.geonames.org/about.html) | CC BY 4.0；城市选择和天气详情保留来源与许可入口；不打包数据集 |
| 运行时应用图标、媒体标题/封面 | 用户已安装应用通过 Android API 提供 | 不作为独立 APK/源码资源分发；本地显示、有限缓存，不纳入 MIT。经选择的单张文档界面截图按下一项处理 |
| `docs/images/quiettv-home.png` | 2026-10-20 所有者指定的 QuietTV 0.14.2 / Android 12 实机画面，来源及 SHA-256 见 [截图说明](docs/SCREENSHOTS.md) | 含 Netflix / YouTube 标志、Netflix 系统海报、标题和进度，以及用户选择的天气城市与输入名称；这些第三方元素不纳入 MIT，不表示合作或背书。只用于完整界面说明，不作为独立媒体素材或 APK 资源分发；用户公开选择不等于第三方再许可 |
| FLauncher / Emotn UI | 交互/视觉参考 | 未纳入其源码、资源、截图或商标；这些项目不为静幕背书 |
| B 站、奇异果、TCL、Netflix、Disney、Apple Music、SmartTube、SmartYouTube、YouTube | 说明兼容性所用第三方名称或包标识 | 权利属于各自权利人，无官方合作或授权暗示。不发布其应用、账号工具、音乐/视频或 DRM 实现；SmartTube 的续播配置独立编写，不包含其源码或 APK |

源码 MIT 允许商业使用；这不改变外部 API 的访问条件。商业分支应关闭默认免费天气或配置合法服务方案，不能把服务密钥打包进公开 APK。网络数据、用户导入背景和第三方媒体不受本仓库 MIT 覆盖。

AI 生成不免除来源审查。贡献者须列明借用代码/素材的来源、版本和许可；来源不明的内容不得进入官方发布。权利人提出具体来源争议时，请按 SECURITY.md 提供文件、版本和权利依据，维护者会核查并在必要时暂停相关内容分发。

0.14.0 可选 Watch Next 系统海报由原媒体应用直接发布，按用户启用的本地规则加载并有限缓存，不作为独立 APK/源码素材再分发，不纳入 MIT。2026-10-20 的完整界面截图范围单独记录于上表及截图说明，不扩大运行时获取范围或授予第三方素材权利。没有官方合作或商标许可暗示；具体来源及服务访问限制见 [海报审查](docs/ARTWORK-REVIEW.md)。
