# GitHub 展示设置

2026-10-20，维护者已审阅双语 README、截图、Prompt 与社区入口，并明确授权发布。以下记录本轮采用的 GitHub 展示文案与标签。

## About

文案：

> Make your TV your own with AI — no ADB for basic setup, no system packages disabled, resume playback in supported apps.

将原提议中的 “no ADB” 限定为基础安装，将 “no system changes” 具体写为不停用系统包；默认桌面、权限与可选高级安装确实可能涉及系统设置。续播限定支持的应用，不承诺所有应用或精确秒数。

## Topics

保留当前已有 topics，增加并去重：

```text
android-tv
launcher
claude-code
vibe-coding
ai-agents
```

发布前读取实时 topics，避免用这五项覆盖现有标签；若达到 GitHub 数量限制，再向维护者列出具体取舍。

## 本轮文件

- [中文 README](../README.md)与[英文 README](../README.en.md)：新截图、安装入口、能力边界、竞品对比及安装/改造 Prompt。
- [RULE-PROMPT.md](../RULE-PROMPT.md)：中英文适配提示词。
- [社区规则目录](../playback-rules/community/README.md)与[设备反馈模板](../.github/ISSUE_TEMPLATE/device_verification.yml)：先收集实际结果，再审阅收录。
- [截图记录](SCREENSHOTS.md)、[海报审查增量](ARTWORK-REVIEW.md)、[第三方说明](../THIRD_PARTY_NOTICES.md)：记录这张用户指定画面的范围。

本轮不新增 APK Release。分享 README 或原创适配时可使用 `#vibecoding` `#claudecode`，文案注明实际验证范围，不代替用户向外发帖。
