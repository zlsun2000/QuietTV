# 继续播放适配规则 v1

0.14.0 起，已有渠道历史与续播参数改由本地 JSON 规则描述。规则可独立导入、启停、更新和回退，已有接入机制内的新适配无需重新编译 APK。支持范围取决于目标应用实际提供的接口；规则不能生成应用未公开的历史或续播能力。

## 在电视上使用

进入「桌面设置 → 天气与继续播放 → 继续播放适配规则」。选择「导入 JSON 规则」，通过系统文件选择器选择本地文件，查看包名、版本范围、作者及验证说明后保存。新导入及更新的规则保持停用，选择对应规则再启用。系统没有文件选择器时需安装文件管理器；此增强功能失败不影响基础桌面。

启用后在原应用正常播放一次，以采集新规则需要的标识；缺少这些字段的旧记录仍可打开应用。播放记录总开关、每应用开关仍有效。MediaSession 规则另需开启其他应用自动识别并授予通知访问；奇异果旧渠道选择也保留此开关。内置 B 站、奇异果规则继承旧功能行为，全新安装的播放记录总开关仍默认关闭。

同一应用、同一接入方式、版本范围重叠的规则只能启用一条。停用、删除、更新或回退规则都保留记录和进度。更新必须增加 `revision`；保留一份前版，回退后也保持停用。没有前版的自定义规则可删除，内置规则可停用，覆盖内置后可恢复原版。

「检查配置与最近记录」只检查字段、目标和可解析的入口，不启动目标应用。状态可区分总开关关闭、应用停用、版本/签名不匹配、缺少字段、等待事件及入口失效。只有用户点击卡片才尝试打开内容或播放。

0.14.0-rc2 起，设备所有者也可通过 ADB 做后台单规则导入：把 JSON 放到应用外部文件目录中的固定文件 `playback-rule-import.json`，向 `local.quiettv.launcher/.LocalMaintenanceReceiver` 显式发送 `local.quiettv.launcher.IMPORT_RULE`。此接收器要求系统 `android.permission.DUMP` 权限；导入使用同一 64 KB 校验、版本递增与回退机制，成功返回 `imported_disabled:规则ID:revision`，失败只返回 `rule_import_failed`，不会启动界面或播放，也不回写其他设置和历史。随后仍需用户在设置中主动启用。该入口不要求切换 HOME；APK 覆盖安装及 OEM 行为需另行观察，不能由配置导入推断播放绝无停顿。

## 文件与能力

0.14.0-rc3 提供只读 ADB 排查入口 `local.quiettv.launcher.INSPECT_TV_HISTORY`（同一 DUMP 接收器，字符串 extra `package` 为已安装目标包）。它先检查已有历史总开关、通用及每应用开关，再检查单独的 `android.permission.READ_TV_LISTINGS` 授权，输出应用外部文件目录的 `tv-history-inspection.json`。仅在显式请求时查询系统身份的 `android.media.tv`，通过 URI 的 `package` 参数限定目标，并核对返回包名；最多检查各 32 项 Watch Next/预览节目，有取消及时间边界。结果只保存字段数量、入口能否解析、目标公开 Provider 声明，不保存片名、内容 ID、链接、进度数值或账号信息，不写入历史、不播放。没有授权与确实查到零条必须分别说明；ADB shell 查询可能也受按包过滤，不能用零条直接断言应用没有发布。此入口只是排查工具，尚未增加 `watch_next` 规则适配器。

- [JSON Schema](playback-rules/schema.json)：供编辑器与 AI 检查结构。应用还检查字段引用、包名关系、数值范围及已安装入口。
- [广播模板](playback-rules/example-broadcast.json)与[媒体会话模板](playback-rules/example-session.json)：虚构接口，不表示真实应用已适配。
- 内置实例：[B 站](../res/raw/rule_bili_tcl.json)、[奇异果](../res/raw/rule_qiyi_tcl.json)。B 站旧环境曾成功，当前所有者 B 站 1.7.7 尚未成功；未设版本上限不代表全部版本支持。奇异果仅声明打开内容。

每文件最多 64 KB，最多 16 个导入规则、当前配置合计 512 KB，每规则保留一份有界前版。记录总数仍最多 8 条。配置本地存储，不自动订阅、上传、下载规则或执行代码。

`target` 必须填写包名，建议用 `minVersionCode` / `maxVersionCode` 限定实测应用版本，并用小写 SHA-256 `certSha256` 限定应用签名。`minSdk` / `maxSdk` 可表达已验证的系统限制。按应用渠道与契约匹配，无须增加品牌判断。签名校验验证的是安装的目标应用；历史广播里的来源字符串仍不能证明发送者身份。`sourceValues` 限定为目标包名或其点分后缀。

`verification.status` 为 `unverified`、`historical`、`emulator` 或 `device`，`notes` 填实际版本、环境、结果与限制。这是作者提供的测试说明。位置续播卡片显示「尝试继续播放」，不把配置声明当作播放成功的证据；同一活动会话能恢复时仍显示「恢复当前播放」。

## 接入与字段

| 接入 | 可读取的数据 | 条件 |
| --- | --- | --- |
| `broadcast` | 规则指定的基本字符串或数值 extras | `event.add` 为记录 action，`event.delete` 可选；`sourceFields` 至少一项匹配 `sourceValues`；目标应用已安装并匹配版本/签名 |
| `media_session` | `mediaId`、`title`、`subtitle`、`position`、`duration` | 标准会话已获用户授权；时间输入已是毫秒，不读取通知正文 |

自定义 action 由主页及系统绑定的媒体监听服务共享运行时接收器，无拥有者时不保活。两个已有渠道 action 保留 manifest 定向接收。自定义 action 的隐式或仅包定向投递需要主页或已绑定监听服务存活；目标应用明确指定 RuleHistoryReceiver 组件时可由系统按通常广播限制投递。不承诺账户完整历史。

`fields` 允许 `id`、`episodeId`、`title`、`subtitle`、`position`、`duration`、`variant`、`channel` 八项；`id`、`title` 必填并设 `required: true`。`key` 选择源字段，`jsonKey` 可读取该字段中的一层 JSON 属性。`equalsKey` / `equalsJsonKey` 可交叉校验重复标识一致；删除事件只读取 id。

字段类型为 `id`（至多 128 个允许字符，无 URL、路径穿越）、`numeric`（1–18 位非零数字）、`enum`（固定 `values`）、`text`、`time`。标题至多 240 字，副标题 160 字；时间须注明 `ms` 或 `s`，范围为 0–72 小时。可配置 `default`；未知进度应省略，不能当成真实零进度。缺少必填、非法类型、超界值或不一致的标识不会生成记录。封面不从规则下载。

`identity` 从 `id`、`episodeId`、`variant`、`channel` 选取稳定标识，必须包含 id。版本更新时保留字段语义与顺序。新广播记录以包名及这些标识生成稳定键，规则名称和 revision 不参与键。旧 B 站/奇异果键保留；标准会话仍用原身份算法。不同接入来源缺少可靠对应关系时不按相似标题强行合并。

## 续播入口

`routes` 按顺序匹配，第一条满足 `when`（字段与枚举值）和 `requires`（已提供字段）的入口生效。`capability` 为 `content` 或 `position`；后者必须存在真实进度。建议精确续播后再放一条只打开内容的回退规则。

`intent` 指定 action，可指定目标包内完整 `component`。渠道包名可与组件的 Java 命名空间不同，实际归属由固定 ComponentName 包名与系统解析结果校验。`extras` 的每项显式指定 `type`：`string`、`int`、`long`、`boolean`，并在 `field` 引用与常量 `value` 中二选一。`type: json` 把一层有类型的对象编码为字符串 extra，不支持继续嵌套。进度/时长的 `unit: s` 将规范化毫秒向下取整为秒，`optional: true` 可省略缺少的参数。

动态参数只能引用内容标识、渠道、类型和进度/时长。深链通过固定 `data.scheme`、`host`、分段 `path` 及 `query` 构造并编码参数，不接收整个动态 URI。不允许 file/content/javascript/data/intent/http scheme，不转发广播传入的 Intent、播放 URL 或任意 flags。所有跳转限定原目标包，并校验已安装、启用、导出的入口；失败回退到标准活动会话或打开应用。

此版没有脚本、DEX、shell、私有数据库、无障碍采集、网络目录或解码适配器。新增接入机制仍需修改底层代码并审查；已有适配器支持的字段和参数差异可通过 JSON 贡献。

Netflix 已找到系统 Watch Next 数据路径，但尚未接入此 Schema，不能用旧 MediaSession 候选代替。证据、权限过滤修正与适配器待办见 [Netflix 接入调查](NETFLIX-INTEGRATION.md)。

## 用 AI 制作与贡献

将本页、Schema、一个模板，以及合法获得的接口说明和**合成/脱敏样例**提供给 AI。可使用以下任务：

> 按 QuietTV 规则规范 v1，为目标应用生成单个 JSON 配置。包名、versionCode 范围、来源字段、标识、时间单位和续播入口以我提供的接口资料为准。未知字段保留待确认，不推测协议、不读取账号库、不添加播放 URL、凭据或远程封面。标为 unverified，给出合成事件及预期内容/进度参数，列出尚需确认的能力。

先检查 JSON，再导入并预检；用户主动点击测试正确视频、分集、位置，以及目标应用被系统结束后的行为。将实际环境和结果写回验证说明。AI 输出或语法通过不能替代应用实测。

贡献 PR 只包含规则、必要说明和合成样例，不包含第三方 APK、账号、真实观看历史、未经审查截图或私有日志。说明接口来源、规则许可、已测应用版本/系统/设备、重复更新、停用、错误数据和回退结果。内置规则由维护者评审后随 APK 更新，社区规则可作为 JSON 手动导入；没有自动联网规则市场。
