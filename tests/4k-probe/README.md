# 独立 4K 缓冲区研究原型

本目录面向开发者，是一个使用合成图形的独立实验，不属于静幕安装包。它比较普通 View 与 `SurfaceView` 的缓冲区、绘制回调和进程内存，并检查一个原生焦点控件；不能据此宣称静幕已经实现 4K。测量方法、结果和限制见 [4K 渲染研究](../../docs/4K-RENDERING-RESEARCH.md)。

Windows 构建需要 Android SDK Platform 28、Build Tools 34.0.0 和 JDK 11 或以上：

```powershell
.\tests\4k-probe\build.ps1 -Sdk 'C:\Android\Sdk' -JavaBin 'C:\Program Files\Java\jdk-17\bin'
```

脚本不连接设备；每次在已忽略的 `emulator-local/` 新建目录，生成随机的临时测试签名并输出 APK 路径。该签名不用于静幕发布，每次构建也不能覆盖上一次使用不同临时密钥安装的原型。不要把 APK 或密钥提交到 Git。

安装和运行只面向明确选择的隔离模拟器；以下 `emulator-5576` 和 ADB 端口需要替换为实际值，不要省略 `-s`。未经设备所有者授权，不要在真机安装或操作。

```powershell
adb -P 5041 -s emulator-5576 install '构建脚本输出的 probe.apk 路径'
adb -P 5041 -s emulator-5576 shell am start -n local.quiettv.renderprobe/.ProbeActivity --es mode view
adb -P 5041 -s emulator-5576 logcat -d -s 'Q4KPROBE:I' '*:S'
adb -P 5041 -s emulator-5576 shell am force-stop local.quiettv.renderprobe
adb -P 5041 -s emulator-5576 shell am start -n local.quiettv.renderprobe/.ProbeActivity --es mode surface
adb -P 5041 -s emulator-5576 logcat -d -s 'Q4KPROBE:I' '*:S'
```

在 `surface` 模式的启动命令末尾加 `--es format rgba`，可另行请求 `RGBA_8888`；不加该参数使用系统默认格式。应用启动后等待约 12 秒查看 `RESULT` 和 `CAPTURE`。条纹检查验证 PixelCopy 得到的缓冲区内容，不证明物理电视最终显示了对应细节。原型有意连续重绘，仅适合探索；两种模式的绘制调用计时口径不同，不能直接作性能结论。原型在 API 34 模拟器编译和运行过，其他 API 与真机未验证。
