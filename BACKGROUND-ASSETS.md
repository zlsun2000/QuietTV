# 自然山湖背景

- 文件：`res/raw/nature_lake.png`
- 实际输出：1672×941 PNG（约 16:9），未将其放大并冒称原生 4K。
- 工具：内置 imagegen，生成模式，无参考图片。
- 用途：静态自然图片背景与缓慢缩放平移动效；代码叠加遮罩以保证卡片文字可读。
- 自定义图片和视频只在电视本地保存，不上传。

最终生成提示词：

> Use case: photorealistic-natural. Asset type: premium Android TV launcher background wallpaper. Generate an original, realistic nature photograph of a tranquil alpine lake, pine forest shoreline, layered rugged mountains in the distance, early evening blue hour with the last gentle warm light on distant peaks. Wide 16:9 landscape, requested output 3840x2160 for a 4K television. Natural photographic textures, calm cinematic composition, muted deep teal and slate blue water and forest with restrained warm highlights. The upper quarter should have smooth open sky for a large white clock, and the lower half should be darker calm lake and forest so white text and application cards remain readable. No buildings, people, animals, vehicles, signs, text, lettering, logos, watermark, interface or mockup. Full-bleed landscape photo, avoid illustration or geometric mountains.

视频测试素材：W3C 托管的 Sintel 预告片 `https://media.w3.org/2010/05/sintel/trailer.mp4`，仅用于本地验证，不加入 APK；验证后移除电视上的测试副本。视频格式参考 [Android 官方媒体格式文档](https://developer.android.com/media/platform/supported-formats)，实际播放仍取决于电视原生解码器。
