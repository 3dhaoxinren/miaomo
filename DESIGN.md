# NFC 照片滤镜设计

2026-10-24：基于 GitHub 0.5.9 增加两种照片滤镜。改动只涉及量化、照片编辑记录、编辑器滤镜入口、预览元数据和对应测试；没有引入软路由服务、InkSight 客户端或首页改版。

## 决策与边界

`PhotoEdit.colorProfile` 选择经典 `InkSightQuantizer` 或自然暖调 `EinkWarmQuantizer`。只改变编辑器显示会让预览和刷屏不一致，因此由 `ImageCodec.prepareEdited` 同时生成最终预览和 NFC 载荷，保留 K/W/Y/R 编号及原列方向、逆序扫描和 2bpp 协议。算法参数与来源见 [滤镜说明](docs/EINKWARM.md)。

照片先量化，再将文字和相框映射到最近屏幕颜色。先合成后抖动会打散文字，未采用该顺序。整张四色图跳过重复抖动，缩放走最近邻，普通照片继续使用高质量缩放。

v2 编辑记录保存滤镜，旧 v1 默认自然暖调，保留 `scene_v1` 偏好键和原图缓存。编辑器用状态副本与历史记录，只有「完成」提交。后台预览有代次检查、取消和位图释放，快速切换及关闭编辑器不会提交过期效果。文字模式保留原有选择手柄。

## 信任边界与限制

滤镜完全在手机本地运行，不增加网络端点、账户、密钥或服务配置。图像读取、原有尺寸上限和编辑记录解析限制保持原有行为。参考样本均由生成器合成，不包含用户照片。已有 NFC、Shizuku、权限和配置恢复流程未改动。

旧四色文件不能恢复原始 RGB。界面和模拟协议测试不能证明真实手机的唤醒、D1 或刷屏成功。保留既有紧凑 Java 格式，不在滤镜更新中重构 NFC 活动和手势处理。

## 复现

设置 `JAVA_HOME`、`ANDROID_SDK_ROOT` 后运行 `python test_images.py` 和 `python build.py`，再用 Gradle 8.7 运行 `gradle -p tests/android --no-daemon --console=plain test`。测试直接编译当前编辑器，显式使用 UTF-8，避免 Windows JDK 17 默认编码损坏中文断言。

Gradle 默认下载 Robolectric 4.16 的原生图形运行库。已有缓存可通过 `MIAOMO_ROBOLECTRIC_NATIVE_RUNTIME` 指向 `nativeruntime-dist-compat-1.0.18.jar`，通过 `MIAOMO_ROBOLECTRIC_SDK` 指向 SDK 缓存；仓库没有本机绝对依赖路径。测试截图可选写入 `MIAOMO_FILTER_SCREENSHOTS` 指定的目录。

GitHub 发布包沿用 0.5.9 的调试证书，私钥只由 `MIAOMO_KEYSTORE` 在本机提供，不入库。CI 临时调试包不保证与发布包签名相同。
