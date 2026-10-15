# 来源、数据许可与第三方名称

核对日期：2026-10-11。对应公开版 0.12.0；另见 [发布前审查](docs/LEGAL-REVIEW.md)。MIT 只适用于项目有权许可的内容，不为第三方服务、媒体或商标授权。

APK 的 `res/raw/legal_notices.txt` 同时包含完整 MIT 版权/许可证及外部数据来源说明，构建审查会核对其内容。

| 项目 | 来源与用途 | 权利及发布边界 |
| --- | --- | --- |
| Java 桌面、脚本、XML 图标和横幅 | 本项目开发；没有引入第三方运行库或第三方 APK | 项目代码采用 MIT；Android 平台 API/SDK 的许可独立适用，SDK 不随仓库分发 |
| `res/raw/nature_lake.png` | 无参考图片的 AI 生成山湖图，提示词见 BACKGROUND-ASSETS.md | 项目允许按 MIT 使用其可许可权益；不保证 AI 图像独占性或在所有地区的著作权成立 |
| 天气数据 | [Open-Meteo](https://open-meteo.com/en/licence) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)；界面署名并提供链接，温度取整、状态翻译；免费 API 须符合 [非商业访问条款](https://open-meteo.com/en/terms) |
| 城市搜索数据 | Open-Meteo geocoding，基础城市库 [GeoNames](https://www.geonames.org/about.html) | CC BY 4.0；城市选择和天气详情保留来源与许可入口；不打包数据集 |
| 运行时应用图标、媒体标题/封面 | 用户已安装应用通过 Android API 提供 | 不打包为 APK/源码资源；仅本地显示、有限缓存，不纳入 MIT。文档截图中的图标按下一项处理 |
| `docs/images/quiettv-home.png` | 公开版在隔离 Android TV 环境中的真实截图，来源见 [截图说明](docs/SCREENSHOTS.md) | 桌面与内置背景属于项目可许可内容；画面中的系统应用图标/名称属于各自权利人，不纳入 MIT，不表示合作或背书。无私人记录、账号或用户导入素材 |
| FLauncher / Emotn UI | 交互/视觉参考 | 未纳入其源码、资源、截图或商标；这些项目不为静幕背书 |
| B 站、奇异果、TCL、Netflix、Disney、Apple Music | 说明兼容性所用第三方名称或包标识 | 权利属于各自权利人，无官方合作或授权暗示。不发布其应用、账号工具、音乐/视频或 DRM 实现 |

源码 MIT 允许商业使用；这不改变外部 API 的访问条件。商业分支应关闭默认免费天气或配置合法服务方案，不能把服务密钥打包进公开 APK。网络数据、用户导入背景和第三方媒体不受本仓库 MIT 覆盖。

AI 生成不免除来源审查。贡献者须列明借用代码/素材的来源、版本和许可；来源不明的内容不得进入官方发布。权利人提出具体来源争议时，请按 SECURITY.md 提供文件、版本和权利依据，维护者会核查并在必要时暂停相关内容分发。

0.14.0 可选 Watch Next 系统海报由原媒体应用直接发布，按用户启用的本地规则加载并有限缓存，不随 APK/源码再分发，不纳入 MIT。没有官方合作或商标许可暗示；具体来源及服务访问限制见 [海报审查](docs/ARTWORK-REVIEW.md)。
