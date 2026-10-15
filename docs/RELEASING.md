# 发布流程

官方 APK 使用维护者本地保存的固定密钥。此密钥未纳入仓库和 CI；开发者、Fork 与模拟器使用自己的密钥。不要将 CI 的临时签名 APK 上传成官方稳定版。

本地候选版 0.14.0-rc1/rc2/rc3 使用 code 25/26/27，后续本地迭代至 rc12/code 36（0.13.1/2/3-test 分别为 22/23/24），不等于已发布。下一次覆盖这些候选版的正式构建须继续递增 code，并按本流程准备公开发布；模拟器结果不能替代真实应用及 OEM 验收。

1. 选择版本，递增 manifest versionCode，更新 versionName、CHANGELOG、安装说明和 Release notes。当前公开版 0.13.0 的 code 为 21；此前 0.12.0 的 code 为 20，本地回退包使用 19。随后升级必须高于所有已发布覆盖版本。
2. 完成 [法律与来源审查](LEGAL-REVIEW.md)、源码与隐私审查：查看 Git 历史、公开路径与媒体来源，排除凭据、用户数据、截图及第三方 APK。运行 `python tools/check-release.py --apk QuietTV.apk`；未厘清内容须移除后重建，不能只在说明中免责。
3. 在干净检出构建，运行核心检查、CI 模拟器和适当的实机验证。按 COMPATIBILITY.md 分别记录证据；没有验证的机型/应用保留“待实测”。不会只因测试环境通过就声明全品牌兼容。
4. 用固定私钥构建官方 APK，运行 apksigner verify --verbose --print-certs；记录公开证书 SHA-256。把同名版本 APK、SHA256SUMS.txt 和 release-info.json 放在本地 release/（不进入 Git）。release-info 包含版本/code、Git commit、文件 SHA-256、签名证书指纹、检查结果与兼容边界；不包含设备个人数据。
5. 提交源代码并创建 `vX.Y.Z` tag；先准备 GitHub draft release，附 APK 与校验文件。文件大小/哈希/签名以及提交关联确认后公开 Release。
6. 新设备按安装指引完成全新安装；已有用户覆盖安装并核对收藏、分类、背景和权限。安装测试需要该设备主人授权。修改源码后重新构建、检查和更新 artifact，不能给旧 APK 贴新版本。

升级通常保留本应用数据。因 Android versionCode 及签名规则，不承诺任意旧版本原地降级。优先恢复系统默认 HOME；开发者自己的同签名回退包应仅保留旧代码并使用更高 code，私有备份不属于公开发布。

CI 的 contents 权限只读，不拥有发布权限或正式私钥；外部 PR 不接触 secrets。发布者另用已授权的账号上传 Release。GitHub Release 的源码压缩包与 APK 分开，普通用户下载 APK。

## 0.14.0-rc12 本地候选验证记录（2026-10-16）

- 干净签名构建、393 项 JVM 核心检查通过。
- API 28 与 API 30 隔离 Android TV：各 59 项规则、8 项真实系统 provider 合成检查、37 项 Watch Next 集成检查通过；各 159 项整行焦点/背景显示回归通过。API 28 另通过全新安装 30 项、覆盖升级保留 11 项。
- Netflix build 27207 在所有者 Android 12 电视显示两条系统记录，所有者确认点击可续播、占位重复消失、海报显示正常；旧记录身份、HOME 偏好/背景和天气选择保留，用户 10 仍未安装。未做资料切换、冷启动或逐帧位置核对。
- 公开 Netflix 与 TCL Bilibili JSON 通过实际导入解析器。B 站当前 1.7.7 仍未适配成功，独立配置与内置历史契约保持一致。
- 106 个公开文件及 APK 已完成隐私/来源扫描，固定证书 v1/v2/v3 验证通过。设备备份、截图和生成包排除；仅提交/推送源码、文档及无私人数据的规则，不创建 tag 或 Release。
