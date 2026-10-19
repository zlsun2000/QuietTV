# 目录与边界

| 位置 | 职责 |
| --- | --- |
| `MainActivity.java` | 遥控器导航、分类、左右分区、整行滚动裁切与设置面板 |
| `UiMetrics.java` / `UiConfig.java` | 以实际窗口缩放，读取有边界的定制参数 |
| `SystemEntrypoints.java` | 查找设置、默认桌面设置、其他 HOME，校验现有入口 |
| `HomeKeyService.java` | 可选的 Home 键过滤无障碍服务；用户开启后拦截 Home 键返回静幕，默认关闭 |
| `InputSources.java` / `DevicePolicy.java` | TCL 元数据适配、标准 Android TV 输入发现、设备专属回退规则 |
| `Wallpapers.java` / `WallpaperImportPolicy.java` | 图片/静音视频渲染；选定文件复制与大小限制 |
| `HistoryStore.java` / `PlaybackRecord.java` | 本地记录、最多 8 项、去重、删除标记和真实操作标签 |
| `MediaHistoryService.java` / `GenericMediaHistory.java` | 通过获准的 MediaSession 监听与当前会话恢复 |
| `WatchNextHistory.java` | 用户启用的系统 Watch Next 读取、来源及入口校验、点击时重新查询；不保存完整链接 |
| `PlaybackRule.java` / `PlaybackRules.java` / `RuleHistoryReceiver.java` | 有界声明式规则、导入/启停/回退、广播/会话映射、目标校验及续播；内置渠道规则在 `res/raw/rule_*.json` |
| `Covers.java` / `MediaArtwork.java` | 媒体应用提供的 bitmap/所属 content provider，本地限额缓存；WatchNextArtwork 的可选系统海报例外见 ARTWORK-REVIEW.md |
| `WeatherRepository.java` / `WeatherButton.java` | 同意后查询天气，缓存与离线状态 |
| `LocalMaintenanceReceiver.java` | 仅持系统 DUMP 权限的本地维护接口，不是公网服务 |
| `res/raw/` | 原创山湖背景和 `ui_defaults.properties` |
| `tests/` | JVM 核心检查与隔离的设备测试源 |
| `tools/` | 明确设备地址后才能执行的高级维护工具 |

## 设备与应用适配

`MainActivity` 使用每个输入的 `intent`，不直接写厂商路由。TCL 接口可用且固件报告端口时使用 TCL 适配；缺失列表只对精确匹配的已验证型号提供回退。其他设备仅展示操作系统登记且能解析播放入口的 passthrough 输入；没有接口时隐藏这一组，可用系统输入设置时保留快捷入口。不能从“这是电视”推断 HDMI 数量或设备名。

标准输入只按公开 `TvInputInfo.getParentId()` 的直接 HDMI 父子关系合并：端口是稳定卡片，单个子设备提供显示名称，多个子设备保留逐个选择的入口。父入口缺失、关系循环或类型不符时保留独立卡片，不按名称猜测重复。排序使用原始系统 HDMI 标签中的数字，不改写物理路由；自定义名称与手动顺序保留。连接、待机、断开和未提供状态分别显示；状态/标签读取失败不丢弃已验证可启动的入口。子入口的收藏、隐藏、分类、顺序和焦点引用迁移到父卡片，保留无关和暂时缺失的键。

输入原始状态与展示状态分开：AOSP 物理输入可因存在流配置而报告连接，也可能用待机表示未知线缆状态，所以标准物理 HDMI/AV 没有逻辑设备证据时显示“接入状态未确认”，不据此判已接线或未接线。有明确父子关系时使用逻辑子设备的运行/待机状态；父端口显式断开优先，未知/断开的子项不贡献设备名和计数。专用 `isInserted` 的 Boolean 保留为适配层的接入依据；异常/非 Boolean 仍未知。没有 CEC 记录不代表没有非 CEC 设备，切换入口始终保留。

设置入口使用标准 Intent，TCL 入口作为被校验的备选。打开其他 HOME 与修改默认 HOME 分开；APK 自身不执行 pm enable/disable、set-home-activity、root 或系统属性写入。Home 键修复为可选且默认关闭：应用只打开系统无障碍设置，由用户显式启用服务后过滤 Home 键并返回静幕，是否生效以系统实际启用状态为准。

添加媒体应用时先使用 MediaSession。专用适配必须保留目标包名、ID、命令与 URL 白名单，只接受匹配内容的元数据。不要从通知正文、日志、账号数据库、网络抓包或无障碍界面读历史。通用识别缺少永久路由时降级“打开应用”，不伪造精确续播。

`LegacyHistoryListener` 在所有支持系统按已启用规则注册广播，主页 Activity 和系统绑定的媒体监听服务共享注册并按所有者释放，不启动常驻服务。已有渠道 action 的定向广播保留 manifest 接收并避免重复；自定义 action 的 package-targeted 广播仍由运行时接收器处理。进程不存活、应用没有发送契约或仅允许原厂桌面时无法保证历史。每应用开关统一约束通用与渠道记录，关闭不删除旧记录。应用管理页的会话状态只是一刻的观测。

规则接入与续播分开：广播提供规范化记录，标准会话可附加持久路由，执行层只构造固定目标包内的声明式 Intent。原始广播及来源声明不认证发送者，依然校验 ID/命令/字段，不存任意 Intent、URL 或脚本。旧渠道键与进度保留，更新/停用/回退不删记录。规范及能力边界见 [PLAYBACK-RULES.md](PLAYBACK-RULES.md)。

桌面内容重建复用根视图与未变更的背景层，视频 TextureView、Surface 与解码器不因设置/历史刷新重建。背景选择改变时才更换渲染层，生命周期仍暂停/释放解码器。纵向视口由 `RowViewport` 在焦点行约束内选择能显示最多完整行的位置，左/右同带去重，卡片和标题仍只绘制完整行。

焦点显露先检查卡片和滚动窗口的有效布局；重建后的新节点尚未测量时，等待全局布局完成再计算滚动位置。回调核对当前页面、窗口挂载和最新焦点，过期回调不滚动或重新抢焦点。API 30 可通过暂时延后布局的合成用例复现旧行为；同一回归还覆盖重建期间方向键改变选中项。

后台内存回调可释放图片缓存；返回前台时独立检查并重新解码已选择的背景，不依赖视图重建或再次 attach。内置图片、自定义图片及视频静态回退沿用原配置，主题配色不额外加载图片。隔离模拟器使用不播放媒体的前台 Activity，覆盖隐藏/后台/完全回收级别及真实视频恢复出帧。

## 本地数据与迁移

设置分为外观、应用、继续播放、天气和系统五组。设置面板记录各页面的操作焦点，保存后重绘桌面并重开同页；返回键通过明确的父页面逐级返回。说明区域和操作列表分别滚动，权限、导入和取消操作保留返回路径。继续播放首页不直接堆叠规则预检与清理操作；这些操作位于高级设置。界面重组不修改偏好键、自动授予权限或默认启用网络来源。

`TvHistoryInspection` 是显式 DUMP 维护请求触发的只读排查工具，独立检查 `READ_TV_LISTINGS` 授权及历史开关，只在固定系统提供者中按目标包查询有界 Watch Next/预览数据；保存脱敏计数，不自动采集历史或启动提供者给出的 Intent。它区分权限过滤和空结果，另有 `WatchNextHistory` 供用户主动启用的 `watch_next` JSON 规则采集记录。

`home` preferences 保存收藏、分类顺序、主题、自定义背景路径和 Home 键修复开关；`playback_history` 保存 JSON 记录及开关；`playback_rules` 保存本地规则、前版和启用选择；`weather` 的具体键由 WeatherRepository 管理。用户媒体位于本应用 external-files / files，缓存可丢弃，配置不能在更新时重置。维护导出包含规则，导入有界校验并兼容不含规则的旧备份。

0.12.0 为 home 与 playback_history 增加 `config_schema=1`。发现旧 preferences 时保留字段及旧默认行为；全新记录 preferences 明确默认关闭 enabled/music_enabled/generic_enabled。迁移没有删除旧键，未知播放字段仍由宽容 JSON 读取处理。后续迁移应逐级、幂等、先判断存在性，并测试升级保留配置。

Direct Boot 解锁前只显示内置资源，不读取凭据保护的收藏、记录或导入背景。天气需确认当前服务条件、手动选城市并启用后工作；旧天气选择保留，但联网须重新确认。可选权限和网络失败不妨碍基础桌面。详情见 [隐私说明](../PRIVACY.md)。

Watch Next 适配器仅在 API 26+、用户解锁、历史/通用/每应用开关和规则开启、节目表读取权限及目标签名/版本匹配时工作。验证固定系统 authority，以 package 查询参数并逐行核对来源；每规则至多读取最新 32 行，整体约 7 秒并支持取消，仍只保留 8 项历史。Activity/系统绑定服务共享 ContentObserver 所有权，没有独立保活服务，返回主页会刷新。权限、提供者或字段缺失时不影响基础桌面。

原始 internal_provider_id 只生成包名域内摘要；记录保存摘要、数字行定位、真实标题和进度。点击时按摘要重新匹配系统记录，行复用或删除不会打开另一内容。系统 Intent 使用 parseUri(raw, 0) 兼容两种 Android 序列化格式；只按 JSON 固定的 action、可选组件、scheme/host、路径和参数名重建，不转发 flags、extras、selector、ClipData 或任意组件。封装参数只在此次查询/跳转内存中存在，完整 URI 不写历史、缓存或日志。原生入口能力为 native，进度由应用决定；读取成功不等于精确续播成功。

同一应用存在可解析的进度或应用自身历史续播入口时，展示层隐藏同应用的 MediaSession 应用占位项；具体条目仅按明确身份或限定的同名/分集规则隐藏，不合并进度或删除原键。停用或失效的入口不能隐藏备用项。同一配置、同一内容 ID 的会话在暂时失去标题时保留原始身份和完整标题，使用新进度，不能跨规则或内容继承。海报参数由独立可选 Watch Next JSON 声明，传递于读取任务内存，存储仍只接受 localart 摘要；本次普通广播/会话配置不新增网络封面。
