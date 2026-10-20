# 应用 / 渠道 / 版本 — App / channel / version

复制本模板到自己的规则目录 `README.md`；将提示替换为事实。中英文任选其一，可同时提供。未知项写“未知 / unknown”，未测试写“未测 / not tested”；不要把本模板当成已经验证的规则。

Copy this template into your rule directory's `README.md` and replace prompts with facts. Use either language or both. Unknown and untested are valid results.

## 范围 / Scope

| 项目 / Field | 实际值 / Value |
| --- | --- |
| 规则 ID / revision | |
| 应用名称与渠道 / App and distribution | |
| 包名 / Package | |
| versionName / versionCode | |
| 签名证书 SHA-256 / Signer certificate SHA-256 | 只写公开证书摘要，不提交证书私钥 / Public digest only; never a private key |
| QuietTV 最低版本 / Minimum version | |
| 实测 QuietTV 版本及来源 / Tested build and source | 官方 Release / 个人构建及源码提交；未测就注明 / Official release or personal build + commit; mark untested |
| 设备、Android、固件 / Device, OS, firmware | 不含序列号、IP 或账号 / No serials, IP addresses, or accounts |
| 日期 / Date | |
| adapter / capability | broadcast、media_session、watch_next；content、position、native |
| verification.status | unverified / historical / emulator / device |
| 作者与原创配置许可 / Author and original rule license | |
| 设备报告 / Device report | 公开、脱敏的 Issue 或 PR 链接 / Public sanitized issue or PR |

## 接口来源 / Interface provenance

填写官方文档链接，或说明有权分析的应用版本、所用方法及观察到的接口形状。公开说明应能解释字段、时间单位、入口、版本和签名的依据，不包含目标 APK、反编译实现、原始观看事件或完整原生链接。找不到证据的字段不能编造。

Link official documentation or describe the authorized app version, method, and observed interface shapes. Explain evidence for fields, units, routes, versions, and signers without redistributing APKs, decompiled implementations, real events, or full links.

## 导入与关闭 / Import and disable

写明所需开关和权限、导入后手动启用的路径、是否与其他规则重叠，以及如何停用或回退。新规则默认停用；记录最多 8 条。不要要求用户停用系统包或开放公网 ADB。

List required switches and permissions, manual enablement, possible overlapping rules, and disable/rollback steps. New rules remain disabled; history is capped at eight records. No system-package disabling or public ADB exposure.

## 实际行为 / Observed behavior

每行写“成功 / 失败 / 未测”，再补具体结果。用合成名称如“测试内容 A”，不提交真实片名。位置精度必须有观察依据；只读到进度不能算验证过续播。

Mark each row passed / failed / not tested, with observations. Use synthetic content labels and no real titles. A reported position alone does not prove resumed playback.

| 场景 / Scenario | 结果与限制 / Result and limits |
| --- | --- |
| 导入与预检 / Import and preflight | |
| 记录出现 / Record appears | |
| 点击打开正确内容及分集 / Correct content and episode | |
| 实际续播位置 / Observed resume position | |
| 返回后进度更新 / Progress updates after returning | |
| 客户端关闭后的再次打开 / Reopen after app closes | |
| 完全重启、账号资料切换 / Reboot, profile changes | 未授权或未测试时写未测 / Mark untested when unauthorized or untested |
| 重复事件、缺字段或无权限 / Duplicates, missing fields or permission | |
| 版本或签名不匹配 / Version or signer mismatch | 使用合成/隔离检查，不修改用户的媒体应用 / Synthetic or isolated checks; do not modify the user's media app |
| 停用、更新及回退 / Disable, update and rollback | |

## 合成样例 / Synthetic samples

说明 `samples.json` 中每个用例的输入与预期结果，包括至少一个有效形状和一个应拒绝的形状。不要注入真实电视。Watch Next 只描述字段和过滤形状，不构造可播放的原生 payload。

Describe inputs and expected results in `samples.json`, including an accepted shape and a rejected one. Never inject them into a real TV. Watch Next samples describe shapes and filters, not playable payloads.

## 隐私与联网 / Privacy and networking

是否省略 `artwork`？若使用海报，列出已经审查的来源、主机声明、对外传输、缓存和用户选择，并链接独立审查。不提供真实图片地址。

State whether artwork is omitted. If present, link its separate review and describe source, host declarations, network disclosure, caching, and user choice without real image URLs.

## 已知限制与变更 / Limits and changes

注明未测环境、失败情形、接口可能变化，以及每次 revision 的改动。说明是打开内容、传递位置还是由应用自身历史决定；不要承诺所有版本、所有设备或精确秒数。

Record untested environments, failures, interface uncertainty, and revision changes. Distinguish opening content, passing a position, and app-managed history. Do not promise every version, device, or exact second.
