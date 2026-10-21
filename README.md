<p align="center"><img src="assets/branding/miaomiao-squircle.png" width="144" alt="喵墨图标"></p>

# 喵墨 · Miaomo

把喜欢的照片放进墨水屏。面向华为 3.98 英寸、552×768 四色 NFC 墨水屏的 Android 照片编辑与推图 App，使用手机内置 NFC 和 Shizuku，无需 root。

当前版本 **0.5.2-test**，Android 8.0+。实验性项目，实机测试来自小米 17 Pro Max / HyperOS 4；厂商 NFC 接口支持情况因机型和系统而异。

## 功能

- 相册/文件选图，横屏和竖屏构图，旋转、镜像、拖动及双指缩放。
- 多行文字图层：字体、字号、颜色、底色、描边、角度、对齐、复制和排序。
- 留白、细线、双线、拍立得、胶片相框；撤销/重做与最近编辑状态恢复。
- InkSight 四色处理、原图/四色对比、保存预览与 NFC 推图。
- Shizuku Q-Poll 寻卡、D1 读取、传输进度和本地诊断日志导出。

照片处理在手机本地完成，无需 InkSight 服务器、API Key 或软路由。App 没有 INTERNET 权限。

## 安装与使用

1. 从本仓库 Releases 下载 APK，或自行构建。
2. 安装并启动 [Shizuku](https://shizuku.rikka.app/zh-hans/)，在喵墨「设备」页连接并授权。
3. 开启手机 NFC，将完整 NFC 墨水屏贴稳在线圈位置，先读取屏幕信息。
4. 在「照片」页选图、编辑，生成四色预览并推送。
5. 上传结束后仍保持贴合，直到 App 报告屏幕刷新完成。

`68CA` 是屏幕返回的低电压状态。刷新期间持续需要供电，上传完成不代表刷新完成。已拆除 NFC 主控、改接 ESP32 的屏幕不适用本 App 的 NFC 路径。

这是调试签名版本。自行构建、CI 构建和下载版的签名可能不同，不能保证相互覆盖安装。导出日志包含图片、标签 UID 和 APDU 等数据；提交 issue 前请筛选，避免上传私人照片及完整设备日志。

## 构建

需要 Python 3.9+、JDK 17+、Android SDK platform 35 和 build-tools 35.0.1。无需 Gradle 或 Android Studio。构建所需 Shizuku/AndroidX 开源工件在 `deps/`，指纹见 `dependency-lock.json`。

Windows PowerShell：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_SDK_ROOT = 'C:\path\to\AndroidSdk'
python test_images.py
python build.py
```

Linux/macOS：

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK_ROOT=/path/to/android-sdk
python3 test_images.py
python3 build.py
```

输出 `build/miaomo-0.5.2.apk`。首次构建在 `build/` 创建本地调试签名，私钥不入库。GitHub Actions 配置了测试、构建和 APK 附件上传；运行结果以 Actions 页面为准。

## 目录

| 路径 | 内容 |
| --- | --- |
| `src/dev/inknfclab/` | NFC 会话、协议、图片处理与 Android 原生界面 |
| `res/` | 主题、图标和 Android 资源 |
| `tests/` | 协议、模拟传输、图像与编辑状态测试 |
| `tests/inksight-fixtures/` | 合成输入和 InkSight 参考输出 |
| `assets/branding/` | 生成图标及来源记录 |
| `docs/` | 兼容性、验证范围与项目来源 |

包名沿用 `dev.inknfclab.test`。历史算法留在源码中供研究，生产构建只使用 InkSight 链路。

## 来源与许可

本项目基于 [wiwiwei/ink-nfc-lab](https://github.com/wiwiwei/ink-nfc-lab) 的 Android 实现继续开发。

- NFC 协议、D1 解析和编码来自 [kur0neko/huawei-epd-pn532](https://gitee.com/kur0neko/huawei-epd-pn532)，继承 **PolyForm Noncommercial 1.0.0**。
- Q-Poll 接口参考 [ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app)。
- 图片增强、调色板和 Atkinson 参考 [datascale-ai/inksight](https://github.com/datascale-ai/inksight)（MIT）和 Pillow（HPND）。
- Shizuku（MIT）、AndroidX annotation（Apache 2.0）许可见 `THIRD-PARTY-LICENSES.txt`。
- 照片编辑交互参考 PhotoEditor、uCrop 和 Android-Image-Cropper，未引入其运行时依赖。

这是**非商业许可的源码公开项目**，不表示允许任意商用。请阅读 [LICENSE](LICENSE)、[NOTICE](NOTICE) 和 [第三方许可](THIRD-PARTY-LICENSES.txt)。

图标由 imagegen 使用 `gpt-image-2` 生成；原始提示词中的旧名仅用于保留生成记录。来源和验证范围详见 [docs](docs/PROVENANCE.md)。
