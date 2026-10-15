# Netflix 继续观看：Watch Next 规则接入

2026-10-15/16，所有者授权后在 Android 12/API 31 电视和 Netflix com.netflix.ninja 13.2.1/build 27207 发现、接入并验证系统继续观看路径。没有 TCL 条件判断；是否可用取决于系统提供者、权限及应用发布的记录。

## 数据与入口

Netflix 实际播放时的 MediaSession 没有有效内容元数据或会话启动入口，因此旧 media_session 候选保持停用。此前 ADB shell 零条结果来自权限/按调用包过滤，不能证明系统没有记录。普通 QuietTV 在用户明确授权 READ_TV_LISTINGS 后，以系统 URI package 参数查询到两条 Netflix CONTINUE 记录，有真实标题、进度、时长和原生入口。原厂 Google TV 桌面也显示这两条继续观看。

入口是固定 VIEW、com.netflix.ninja.MainActivity 及 https://www.netflix.com/deeplink，query 名为 payload。系统保存的是普通 HTTPS URI 加 Android Intent 附加段的序列化格式；parseUri(raw, URI_INTENT_SCHEME) 会把此格式当成普通 VIEW URL，误将附加段作为 fragment。使用 Android parseUri(raw, 0) 才能兼容该格式及 intent: 格式，然后仍逐项校验并重建新 Intent，不直接转发原对象/flags。此修正属于通用解析，已加入合成回归测试。

规则为独立本地 JSON：watch_next 适配器、目标包/实测版本/签名、continue 类型、规范字段及固定入口。发布者原始 ID 不保存，只用包名域内摘要；点击重新读取并验证同一身份，完整 payload 不进入历史、缓存或日志。capability 为 native，继续位置由 Netflix 控制，不能宣称通用精确 seek。

所有者已明确反馈点击可续播。已确认真实系统记录在静幕显示并隐藏旧应用占位重复项；记录和原键保留。没有由维护脚本直接启动影片；后续未重复触发播放。不同资料切换、冷启动、系统杀进程、其他 Netflix 版本、其他 OEM 仍未完成受控验收。

## 封面

系统提供 poster_art_uri，实测为 Netflix nflxso.net 子域的 HTTPS 图片，带 client、deviceTypeId、platform 参数。可选 artwork 声明固定主机与参数名，并要求目标签名匹配；值只来自当次系统记录，不保存完整 URL。没有目录/账号 API、远程封面搜索、Cookie、token 或第三方 APK。来源、服务及网络隐私边界单独记录在 [海报审查](ARTWORK-REVIEW.md)。两条记录均已生成本地缓存，所有者随后确认屏幕封面显示正常；没有用地址存在代替显示验收。

经所有者明确要求，已将不含私人数据的通用 [Netflix JSON](playback-rules/netflix-watch-next.json) 放入仓库，供手动导入。它与本地测试规则使用不同 ID，同一应用的重叠规则不能同时启用；本次不再操作已关闭的电视来更换本地 ID。观看记录、私人备份、图片、完整链接和截图仍不提交 Git。可贡献的合成模板、字段说明和 Schema 见 [规则指南](PLAYBACK-RULES.md)。

## 验证边界

干净构建和 393 项 JVM 核心检查通过。隔离 API 28/30 模拟器验证规则解析、普通/intent 序列化、真实系统 provider 的合成记录采集、异步点击、停用/权限/按应用开关、身份防混淆、删除标记、占位去重及本地封面缓存；网络策略用合成域名/地址检查，不向测试服务器或媒体平台发送合成请求。真实 Netflix 点击成功为所有者反馈，与合成测试分开记录；未据单台电视宣称全品牌支持。

接口来源：[Android Watch Next](https://developer.android.com/training/tv/discovery/watch-next-add-programs)、[Intent 格式契约](https://developer.android.com/reference/android/content/Intent)、[AOSP 提供者过滤与权限](https://android.googlesource.com/platform/packages/providers/TvProvider/+/refs/heads/master/src/com/android/providers/tv/TvProvider.java)。
