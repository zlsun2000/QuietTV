# 可重复的电视模拟器性能检查

本夹具面向开发和测试人员，用同一个本地合成场景比较两个构建的帧统计与进程内存。**只可在明确创建、数据可丢弃的 Android TV 模拟器运行；脚本会卸载并重装该模拟器上的静幕，清空其全部数据。** 不安装到真实电视，也不读取账号、视频或用户记录。

## 场景与控制条件

每轮清除静幕测试数据后创建 42 个合成应用、3 个常用应用和 1 条合成播放记录；使用同一种静态主题、5 列网格、关闭动态壁纸和应用横幅。初始焦点在第一个常用应用。等待页面稳定后重置 Android 的帧统计，再发送 3 轮固定的遥控输入：每轮依次向右 4 次、向下 5 次、向左 4 次、向上 5 次，每次按键后等待 200 毫秒。记录实际耗时、每次的焦点类别、前后进程 PSS/Java 堆使用量及 `gfxinfo framestats`。

两轮必须使用同一台可丢弃模拟器、同一系统镜像、分辨率、字体大小、动画倍率、内存、图形渲染模式及背景运行负载；分别使用不同标签保存输出。`RendererLabel` 是操作者对模拟器启动模式的记录，不是脚本对渲染硬件的验证；`environment.txt` 也会记录系统报告的 GLES 信息（如果可用）。开始前请核实模拟器日志中的渲染模式。软件渲染的数据不能代表真实电视或用于推断真实设备的瓶颈。

帧统计来自 Android 的 `dumpsys gfxinfo`，PSS 来自应用进程的 `Debug.getMemoryInfo()`；这不是设备总内存，也不是峰值内存。分别对比输入路径、实际时间、前后 PSS、帧数和帧耗时分布；路径不同、设备条件不同或系统不提供帧数据时不能认作同条件比较。

## 准备

1. 使用相同的**临时测试密钥**分别构建旧版和新版 APK，保留各自同一次构建的 `build/classes.jar`。不要使用或替换官方签名包。测试 APK 和被测 APK 必须同签名；脚本会验证证书。
2. 在安全的构建环境中设置 `QUIETTV_KEYSTORE_PASSWORD`，不要将密码写进命令历史、脚本或 Git。传给 `-Keystore` 的密钥需包含别名 `quiettv`。
3. 启动明确创建的可丢弃 TV 模拟器，确认渲染配置与设备序列号，例如 `emulator-5574`。脚本要求明确传入该序列号及 `-ConfirmDisposable`，并检查 QEMU、虚拟硬件和模拟器指纹。

临时签名的测试包可能触发模拟器中的安装提示；只在确认是该隔离测试包时于模拟器中处理，不改变真实设备的安全设置。

下面的路径是示例变量，必须指向自己的隔离构建和临时密钥。每轮标签不可重复；已有目录不会被覆盖。

```powershell
$baselineRoot = 'D:\Project\QuietTV\baseline-build-local'
$candidateRoot = 'D:\Project\QuietTV\candidate-build-local'
$testKey = 'D:\Project\QuietTV\temporary-test-key-local.jks'
$common = @{
    Sdk = 'D:\android-tools\sdk'
    JavaBin = 'D:\android-tools\jdk-17.0.20.1+1\bin'
    Keystore = $testKey
    Serial = 'emulator-5574'
    ServerPort = 5043
    RendererLabel = 'swiftshader'
    ConfirmDisposable = $true
}
.\tests\device\performance\run.ps1 @common -Apk "$baselineRoot\QuietTV.apk" -AppClasses "$baselineRoot\build\classes.jar" -Label 'baseline-01'
.\tests\device\performance\run.ps1 @common -Apk "$candidateRoot\QuietTV.apk" -AppClasses "$candidateRoot\build\classes.jar" -Label 'candidate-01'
```

结果写在 Git 忽略的 `emulator-local/performance-baseline-01/` 和 `emulator-local/performance-candidate-01/`，分别包含 `environment.txt` 和 `results.txt`。每轮的测试应用和被测 APK 要用同一个临时密钥签名；如果签名不一致，脚本会在连接模拟器之前停止。需要仅验证测试 APK 能否构建时可加 `-BuildOnly`，此时无需设备序列号、渲染标签或 `-ConfirmDisposable`，也不会操作模拟器。

模拟器不代表 TCL Android 9 或 Google TV Streamer 实机；`lowRam=true` 也必须由系统实际报告，不能仅按模拟器内存容量推断。真实设备的验证需另行记录。
