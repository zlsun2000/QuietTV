# 社区续播规则 / Community playback rules

**中文** · [English](#english) · [回到首页](../../README.md)

这里收录社区贡献的 **JSON 适配 + 来源说明 + 设备验证结果**。一个应用在一个版本上成功，也能帮助下一个人；失败与升级后的失效记录同样有用。

**目前尚无已收录的社区贡献。** 项目已有的 [Netflix、奇异果、SmartTube 与历史 B 站配置](../../docs/PLAYBACK-RULES.md#可下载的应用配置)仍保留在原目录，不把它们重新标为外部贡献。

## 找到适合自己的规则

收录后，维护者在下表增加一行，链接到对应目录的说明。先看包名、versionCode、签名、QuietTV 版本和设备结果，再下载 JSON。应用名字相同不代表渠道和接口相同。

| 应用 / 渠道 | 应用 versionCode | QuietTV 版本 | 设备 / 系统 | 实际能力与限制 | 来源 / 验证报告 |
| --- | --- | --- | --- | --- | --- |
| 暂无收录 | — | — | — | — | — |

没有自动订阅或下载规则。每次导入、更新或回退后都保持停用，由用户核对并启用；同应用、同适配器的重叠版本规则不能同时启用。失效规则应明确标注或停用，不能直接放宽签名、版本或入口校验。

## 贡献一份规则

1. 使用 [RULE-PROMPT.md](../../RULE-PROMPT.md) 整理你有权检查的接口资料，或直接按[规范](../../docs/PLAYBACK-RULES.md)编写。
2. 按下面目录组织文件，复制 [TEMPLATE.md](TEMPLATE.md) 作为说明。目录使用可读的小写名称，例如 `app-channel-12345`；其中数字为核实的 versionCode，不是兼容所有版本的声明。
3. 在获准的设备上验证，或明确标为 `unverified`。使用[设备验证 Issue](https://github.com/zlsun2000/QuietTV/issues/new?template=device_verification.yml)记录结果；不会提 PR 也可以只提交报告。
4. 发起 PR，附接口来源、配置许可和报告链接。维护者审阅结构、身份限制、来源和隐私，再决定是否收录；不得把未测规则标为已验证。

```text
playback-rules/community/<app>-<channel>-<versionCode>/
  rule.json      # 可导入规则；单文件不超过 64 KiB
  README.md      # 来源、许可、版本、权限、行为结果与限制
  samples.json   # 合成输入和预期结果，不是真实观看事件
```

`samples.json` 是供人工或开发检查阅读的样例，**不是导入电视的配置**。Watch Next 样例只写字段形状/过滤条件，不提供完整原生链接或伪造可播放记录。规则 ID 应稳定且避免冲突；更新需增加 revision，保留 identity 的语义和顺序。

默认不声明远程海报。增加联网海报须有单独的[来源和服务审查](../../docs/ARTWORK-REVIEW.md)，不能继承其他应用的审查结论。贡献文件不含第三方 APK、反编译代码、账号、真实片名/历史、完整链接、私人日志或未经审查的截图；本目录只接收上面列出的文本文件。

收录代表经过文档与配置审阅，**不等于维护者在每台电视复测过**，也不保证接口永久可用。规则只能使用原应用已经提供的接口，不改变会员、广告、地区或 DRM 流程。源码许可不授予第三方服务和媒体的使用权。

## English

This directory is for community-authored **rules, source notes, and device evidence**. No community submissions have been accepted yet. Existing project-maintained [downloadable rules](../../docs/PLAYBACK-RULES.md#可下载的应用配置) remain in their original location.

Before importing a rule, match the package, versionCode, signer, minimum QuietTV version, and documented device behavior. There is no automatic rule feed. Imported, updated, and rolled-back rules remain disabled until reviewed and enabled by the user.

To contribute:

1. Use the [English rule prompt](../../RULE-PROMPT.md#english) or the specification.
2. Create an app/channel/version directory with `rule.json`, `README.md`, and `samples.json` as shown above. Start with the bilingual [template](TEMPLATE.md).
3. Record authorized device observations in the [device report](https://github.com/zlsun2000/QuietTV/issues/new?template=device_verification.yml), or explicitly mark the rule unverified.
4. Submit a PR with sources, a license for your original rule text, and report links. Maintainers review it before adding an index entry.

Synthetic samples are documentation, not TV imports. Do not include real viewing events, titles, full links, accounts, APKs, decompiled code, private logs, or unreviewed captures. Omit remote artwork unless there is a separate source/service review.

Keep IDs stable, increment revisions, preserve identity semantics, and record regressions without weakening validation. Acceptance is not universal device testing or a guarantee of continued compatibility. Rules do not bypass subscriptions, ads, regional access, or DRM.
