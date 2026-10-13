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
| `Bili*.java` / `Qiyi*.java` | 兼容历史广播、内容标识和固定应用入口；不访问平台目录 |
| `Covers.java` / `MediaArtwork.java` | 媒体应用提供的 bitmap/所属 content provider，本地限额缓存；不下载网络封面 |
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

Android 8+ 的已有渠道历史隐式广播由 `LegacyHistoryListener` 在运行时接收，主页 Activity 和系统绑定的媒体监听服务共享注册并按所有者释放；不启动常驻服务。定向广播保留 manifest 接收，隐式事件避免重复处理，仍交给原有 B 站/奇异果校验器。进程不存活、应用没有发送契约或发送目标只允许原厂桌面时无法保证历史；注册接收器不等于应用已适配。每应用开关统一约束通用与渠道记录，关闭不删除旧记录。应用管理页的会话状态只是一刻的观测，不建立永久支持名单。

## 本地数据与迁移

`home` preferences 保存收藏、分类顺序、主题、自定义背景路径和 Home 键修复开关；`playback_history` 保存 JSON 记录及开关；`weather` 的具体键由 WeatherRepository 管理。用户媒体位于本应用 external-files / files，缓存可丢弃，配置不能在更新时重置。

0.12.0 为 home 与 playback_history 增加 `config_schema=1`。发现旧 preferences 时保留字段及旧默认行为；全新记录 preferences 明确默认关闭 enabled/music_enabled/generic_enabled。迁移没有删除旧键，未知播放字段仍由宽容 JSON 读取处理。后续迁移应逐级、幂等、先判断存在性，并测试升级保留配置。

Direct Boot 解锁前只显示内置资源，不读取凭据保护的收藏、记录或导入背景。天气需确认当前服务条件、手动选城市并启用后工作；旧天气选择保留，但联网须重新确认。可选权限和网络失败不妨碍基础桌面。详情见 [隐私说明](../PRIVACY.md)。
