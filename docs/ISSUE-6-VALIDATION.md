# 1.0.1 界面与性能验证记录

本文面向开发与测试人员，记录 Issue #6 的基线、可测目标和验证结果。合成截图与性能原始数据保存在不提交 Git 的 `emulator-local/`；模拟器结果不能代表电视实机。

## 改动前基线与目标

| 项目 | 1.0.0 基线 | 可测目标 |
| --- | --- | --- |
| 移动应用 | 隔离 API 34 TV 模拟器的原版截图保存在 `emulator-local/issue6-baseline/`。进入移动、换位和结束移动都会替换桌面视图；并排常用、分行常用和跨行网格均可复现。维护者另在电视上观察到移动常用应用时闪屏。 | 移动时保留桌面和壁纸，只更新受影响卡片；顺序持久化，移动目标、焦点、长按菜单和返回保持正确。 |
| 上下切换 | 同一 API 34、1920×1080、SwiftShader 软件渲染、42 个合成应用、3 个常用、1 条合成续播记录、54 次相同遥控输入：571 帧中 59 帧（10.33%）被系统标记为卡顿，帧耗时 P50 32 ms、P95 42 ms。原始数据在 `emulator-local/performance-issue6-baseline-02/`。 | 在相同设备、数据和输入下记录候选版帧耗时、卡顿和内存，检查上下切换、焦点恢复与整行裁切；动画不得遮挡焦点或改变低内存退路。单次软件渲染结果不能归因于 Streamer。 |
| 4K | 内置背景素材为 1672×941。已连接的 TCL Smart TV Pro（Android 12）报告物理输出 3840×2160、应用逻辑窗口 1920×1080；不是 Google TV Streamer 的测量。现有模拟器为 1080p，不能凭电视输出尺寸证明应用以 4K 渲染。 | 区分系统窗口和资源分辨率，优先使用公开能力；不改全局显示设置，不降低低内存设备的稳定性。Streamer 的窗口及实际观感须由该设备验证。 |
| 左下提示 | `baseline-navigation.png` 显示原版主页提示；代码已使用透明背景和局部文字阴影，但包含多条基础操作说明。 | 精简常驻提示，保留与整理相关的可发现操作；文字浮在壁纸上，仅使用局部阴影，不加全宽背景条。 |

## 已完成检查

- 原版干净构建及 406 项核心检查通过。API 34 模拟器新增的移动回归在原版验证了以上三种布局的桌面替换。
- 移动修复后，干净构建及 406 项核心检查通过；API 34 TV 模拟器的 27 项专项检查通过，包括顺序、焦点、菜单、边界和重新渲染后恢复。两轮检查均使用临时测试签名。
- 提示精简后，干净构建及 406 项核心检查通过；API 34 模拟器的 33 项移动与 8 项切换/提示专项检查通过。`hint-after.png` 是英文模拟器截图，检查确认提示无背景条、有局部文字阴影，进入和退出移动时显示对应提示。
- TCL Android 9 和 Google TV Streamer 当前不可连接，尚未验证；TCL Android 12 仅只读确认版本和显示信息，不能代替另外两台设备的测试。

## 上下切换方案对照

三次原版基线的卡顿帧比例为 10.33%、10.56%、10.49%，P95 为 42、42、44 ms。整行淡入和仅缩小滚动重绘的初版均未显示稳定收益；在随后交替测试中，原版自身的卡顿比例也波动到 14.16%、15.32%。因此不采用整行淡入，也不把模拟器结果当成真实设备的根因。

保留原有整行裁切与滚动，只在上下切换时复用焦点卡片动画，增加短距离位移；低内存和系统关闭动画时不增加位移。两组交替比较使用相同模拟器、数据与遥控输入，焦点路径一致：

| 轮次 | 原版卡顿帧 / P50 / P95 | 候选版卡顿帧 / P50 / P95 | 原版 PSS 开始 / 结束 | 候选版 PSS 开始 / 结束 |
| --- | --- | --- | --- | --- |
| 1 | 57/567（10.05%） / 31 / 44 ms | 62/555（11.17%） / 30 / 42 ms | 47,994 / 55,067 KB | 49,147 / 39,231 KB |
| 2 | 61/573（10.65%） / 31 / 46 ms | 65/557（11.67%） / 31 / 46 ms | 49,072 / 38,995 KB | 49,072 / 39,182 KB |

这两轮无法证明卡顿改善；系统标记的卡顿比例略高，P95 接近，PSS 只是在开始和结束时各采一次。原始日志保存在 `emulator-local/issue6-baseline/` 和 `emulator-local/issue6-fast-transition/`，不能代表 Streamer 的 GPU 或遥控手感。普通路径、系统关闭动画及仅在本地测试副本强制的低内存分支各通过 6 项方向键、焦点和整行检查；不是对真实低内存电视的验证。

## 4K 窗口核对

新建的 API 34 `tv_4k` 模拟器报告物理 3840×2160、普通应用窗口 1920×1080，原版截图为 1920×1080，保存在 `emulator-local/issue6-baseline/4k-device-1080-home.png`。清除模拟器内临时窗口覆盖后系统仍限制为 1080p。Android TV 的[平台配置](https://android.googlesource.com/device/google/atv/+/7744c276792cb868d5d24bb7e0a870e40b77a7d3/overlay/TvFrameworkOverlay/res/values/config.xml)包含宽度限制；[Android 显示文档](https://developer.android.com/reference/android/view/Display.Mode#getPhysicalWidth())也说明物理输出和普通应用 UI 分辨率可以不同。该结果不证明 Streamer 固件采用相同配置。

维护者决定 1.0.1 先发布其他三项，原生 4K 界面另行研究；本版不调整电视全局显示设置，也不声称已解决 4K 界面清晰度。

## 1.0.1 候选版回归

- 使用临时测试密钥干净构建，406 项核心检查通过。独立 API 34 TV 模拟器：全新安装 31 项、显示 249 项、设置 166 项、壁纸恢复 68 项、规则 71 项、系统电视节目表 8 项和 Watch Next 37 项通过。模拟器的系统默认桌面因原厂优先级未被替换，该项未验证。
- 在隔离模拟器上实际从临时签名的 1.0.0 / versionCode 41 覆盖安装 1.0.1 / versionCode 42：12 项检查通过，核对了合成收藏、隐藏项、分类与顺序、主页布局开关、壁纸文件和动效选择、播放记录与播放选择、已授权的电视节目表权限和未授权的通知访问。
- 4K 界面、Google TV Streamer、TCL Android 9 和真实低内存电视均未验证。模拟器数据不代表这些设备的性能或体验。TCL Android 12 仅只读确认 1.0.0 / code 41 与窗口信息，未安装或测试 1.0.1。

## 最后一次低内存失败排查（2026-11-12）

- 连续运行合成 1.0.0 → 1.0.1 覆盖升级、壁纸和规则检查后，未清理测试应用数据便运行完整显示套件，报告 3 项失败：`split favorites is one horizontal row despite legacy grid preference`、`removing a favorite keeps focus on the next item`、`hiding an item keeps focus on the next visible item`。原始输出保留在 `emulator-local/emulator-5574/display-lowram-contaminated-20261112.txt`。该次运行原本只想执行移动与切换专项，用字符串数组转发开关未生效，实际跑了完整 249 项。
- 复跑前确认设备为隔离 API 34 TV 模拟器（`ro.kernel.qemu=1`、`ro.hardware=ranchu`、官方 TV 指纹），清理 `local.quiettv.launcher` 的测试应用数据，并按 `tests/device/run.ps1` 相同步骤重建检查 APK。本地终端只有 PowerShell 5.1，不满足该脚本的 `#requires -Version 7.0`，因此改用明确的 `am instrument` 参数直接运行，不再依赖字符串数组转发开关。
- 清理数据后：普通最终版完整显示 249 项通过；仅在本地副本强制 `isLowRamDevice()` 返回 true 的完整显示 249 项通过；该低内存副本的移动专项 33 项、上下切换专项 8 项（含低内存退路断言）通过。独立日志为 `emulator-local/emulator-5574/issue6-recheck-normal-display.txt`、`issue6-recheck-lowram-display.txt`、`issue6-recheck-lowram-moveonly.txt`、`issue6-recheck-lowram-transitiononly.txt`。
- 三项失败在清理数据后均未复现，指向先前合成升级、壁纸和规则检查留下的测试应用偏好状态，而非本版三项改动的产品缺陷。本次未做单变量注入复现，不把具体残留字段写成已证实结论；显示套件应在清理测试应用数据后运行。产品源码未因本次排查修改，排序、焦点、壁纸和低内存退路保持原有行为。
- 以上仍只是隔离模拟器结果，不能称为 Streamer、TCL Android 9 或真实低内存设备通过。

## CI 兼容作业与发布状态（2026-11-12）

- 推送 `301414f` 后的 [CI run 43217412166](https://github.com/zlsun2000/QuietTV/actions/runs/43217412166)：`android (28)`、`android (30)` 成功；`android (23)` 的 `fresh` 30 项、`upgrade` 12 项、`rules` 71 项通过，但 `display` 在 `hidden favorites leave recent independent in default layout` 之后不再产生输出，600 秒后被 `timeout` 终止（exit 124）。设备日志未见本应用崩溃或 ANR。
- 对失败作业发起一次重跑（attempt 2），在**同一检查之后**再次无输出并超时；不是偶发抖动，也不能归因于某项断言失败。API 28、API 30 与本地 API 34 的显示检查均通过。
- `DisplayChecks.inputVisibility()` 开头会关闭并等待合成 HDMI 输入源扫描线程（`InputSources.dispose()`、`worker.awaitTermination`、`waitForIdleSync()`），随后才安装输入源夹具。挂起位置紧接该入口，需在 API 23 环境单独复现；目前没有证据断定是产品代码缺陷、测试夹具还是模拟器运行时问题，因此不改动产品源码、不跳过或放宽该检查。
- 按「任何必要验证失败不发布」的约定：**未创建 v1.0.1 tag，未创建 draft/正式 Release，未回复 Issue #6**。源码、版本说明、验证记录与测试夹具已推送到 `origin/main`；正式密钥构建的 APK 与校验文件只在本地 `release/v1.0.1/`，未上传。待 API 23 挂起查清、CI 全绿后再核对构建证据并发布。
