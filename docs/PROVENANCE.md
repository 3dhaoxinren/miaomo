# 项目来源

本 App 在 Ink NFC Lab 的 Android 实现上增加 Shizuku 会话、日志采集、照片编辑、InkSight 本地图像处理和新界面。

| 组件/参考 | 来源 | 说明 |
| --- | --- | --- |
| Android 基础 | https://github.com/wiwiwei/ink-nfc-lab | 保留包命名与上游许可 |
| NFC 协议与数据布局 | https://gitee.com/kur0neko/huawei-epd-pn532 | PolyForm Noncommercial 1.0.0；见 NOTICE 的 Required Notice |
| Q-Poll 接口 | https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app | 参考提交 `bc4e3763ed02d6e7591f87b12293259227f9be69` |
| 图片增强、色板、Atkinson | https://github.com/datascale-ai/inksight | MIT，Java 移植 |
| 增强和 Lanczos | https://github.com/python-pillow/Pillow | 10.1.0，HPND |
| 图层和撤销交互 | https://github.com/burhanrashid52/PhotoEditor | 仅交互参考，无运行时依赖 |
| 裁切交互 | https://github.com/Yalantis/uCrop | 仅交互参考，无运行时依赖 |
| 裁切交互 | https://github.com/CanHub/Android-Image-Cropper | 仅交互参考，无运行时依赖 |

`deps/` 只包含 Shizuku 和 AndroidX 开源工件，未发布手机提取的厂商 APK/JAR、系统镜像或 NFC 固件。校验值见 `dependency-lock.json`，许可证全文见 `THIRD-PARTY-LICENSES.txt`。

图标原图、适配资源及提示词在 `assets/branding/`。图像由 `gpt-image-2` 生成，这一来源记录不随后续默认模型配置改变。
