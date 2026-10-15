# Netflix 继续观看：已确认的数据路径与待实现部分

2026-10-15 的补充排查修正了此前结论：MediaSession 缺少影片标识，并不能说明系统没有其他继续观看来源。当前版本已确认可以通过正常授权后的 Android Watch Next 查询获得 Netflix 的继续观看记录。

## 已确认的证据

- 所有者电视上的 Netflix 为 `com.netflix.ninja`，13.2.1/build 27207，Android 12/API 31。实际播放时的 MediaSession 元数据和会话启动入口为空，队列为空，因此旧 `media_session` 候选仍不可用于内容级续播。
- 此前 ADB shell 查询没有 `READ_TV_LISTINGS` 或 `ACCESS_ALL_EPG_DATA`，电视提供者会按调用方包名过滤。查询零条不能证明 Netflix 未发布记录；应用也可能在暂停或退出后更新记录。
- 用户自行离开 Netflix 后，通过已获 `READ_TV_LISTINGS` 的普通 QuietTV 进程，按 Netflix 包名查询到了 `WATCH_NEXT_TYPE_CONTINUE` 记录；记录有进度值、时长和可解析到 Netflix 自身的入口。这证明数据路径存在，不证明点击后影片、分集或续播位置正确。
- 当前 `internal_provider_id` 没有落入已有规则接受的有界 ID 格式；尚未收集其值或 Netflix 入口参数契约，不能把它猜成旧 `mediaId` 或影片数字 ID。
- 本次只保存脱敏计数，没有保存实际片名、标识、URI、进度值或账号资料。检查后撤销临时节目表权限，保留原有设置、规则及历史身份；没有启动 Netflix、发送按键或媒体控制。

接口来源：[Android Watch Next](https://developer.android.com/training/tv/discovery/watch-next-add-programs)、[AOSP 提供者的调用方过滤](https://android.googlesource.com/platform/packages/providers/TvProvider/+/refs/heads/master/src/com/android/providers/tv/TvProvider.java)、[节目表权限声明](https://android.googlesource.com/platform/packages/providers/TvProvider/+/refs/heads/master/AndroidManifest.xml)。

## 适合 QuietTV 的实现方向

应新增通用 Watch Next 接入适配器，再用 JSON 描述目标应用的标识和路由差异。按公开系统能力、权限及目标应用版本匹配，不增加 TCL 品牌判断；其他应用和电视只有在实际发布可读取记录时才可接入。API 23–25 或没有提供者的系统保留现有降级。

1. 读取需用户启用历史、来源应用及此接入，并显式授予可撤销的 `READ_TV_LISTINGS`。固定系统 authority，验证提供者身份，以 URI 的 `package` 参数限定目标，并核对每行来源。
2. 将系统继续观看和推荐节目分开，尊重真实进度、时长及最后互动时间；记录仍最多 8 条。没有分集字段时不推测集数；已有记录和开关保持不变。
3. 先核对原生入口的字段契约，再声明提取位置和固定目标路由。不能直接转发整个未知 Intent，不能把完整链接写进历史或用标题猜对应影片。原始标识不适合保存时，应采用经过审查的稳定身份策略，防止提供者行删除或复用后打开另一条内容。
4. 生命周期沿用桌面和系统绑定监听服务的所有权，有界查询和取消，不为此常驻保活。提供者无数据、来源停用、权限撤销或目标入口失效时明确降级，不把权限失败当作空历史。
5. 在用户方便时主动点击验收：同一影片、正确分集、原生进度、切换 Netflix 用户资料后的行为，以及目标进程结束后的入口。入口解析成功只能作为预检，不能标为精确续播已验证。

0.14.0-rc3 只增加上述数据路径的只读诊断，尚未增加 `watch_next` 规则适配器。现有 Schema 仍只接受 `broadcast` 和 `media_session`，旧 Netflix 候选保持停用；不能仅改候选 JSON 的 adapter 字符串就宣称接入完成。

## 排查验证

干净构建和 393 项核心检查通过；隔离 Android TV 9/API 28 的 59 项规则检查及 8 项真实系统提供者合成检查通过。合成发布器只在虚拟设备运行，分别覆盖正常授权、按包读取、继续/下一集分类、错误目标入口、报告不含原始值及偏好保留；未在真实电视运行合成发布器。API 30、API 23 和显示全套本轮未重跑，Netflix 实际点击续播仍未测试。
