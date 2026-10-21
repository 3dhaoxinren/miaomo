# 兼容性与验证范围

开发设备为小米 17 Pro Max / HyperOS 4，通过 Shizuku 的 shell 权限调用厂商 NFC 接口。无需 root 不等于所有 Android 手机兼容；普通 ISO-DEP 发现与 Q-Poll 有差异。

目标是保留完整 NFC 主控和线圈的华为 3.98 英寸 552×768 四色屏。D1 校验几何、色数和 UID 后才允许上传。部分固件报告 552×1536/2 色，协议层将其解释为 552×768/4 色。

测试覆盖协议边界、模拟上传/刷新、低电压、超时、图像量化、缩放裁切和编辑状态。模拟测试不能证明实际射频供电或面板刷新成功。图像参考来自 Pillow 10.1.0 和 InkSight，指纹见 `tests/inksight-fixtures/reference.json`；输入为合成数据，协议测试 UID 也为合成值。

开发期间已观察到寻卡、D1 和照片刷新；细密抖动照片曾出现 68CA，外部供电后观察到完成。以上是测试现象，不是通用接线指导。本仓库不包含 ESP32 固件。

0.5.5 新增 ST54L 的独立实验后端，只在运行时 `getChipId()` 明确返回 ST54L 且用户开启实验模式时选择。移植上游 `ink-nfc-lab/RootProbe.java` 的 ST vendor NCI GID=0F/OID=01 自定义轮询帧事务（读取 0x15、设置 0x13），保存原值后设置 `012A020A7A`，对设置与恢复分别读回；不依赖 AOSP RF discovery getter。共享 ReaderMode 同时解析四/五参数签名，不猜 Binder transaction 编号。

NXP 路径保留独立 profile 与 RF 快照门禁；不会因 ST 模式或无读回开关向未知控制器发送 NXP QTag 配置。ST 路径也不接受未知 ST 芯片，必须能注册标准 vendor NCI callback、暂停/恢复轮询、读出可解析的 ST polling 配置后才可写入。

小米 15 Pro 尚无实机验证。公开参考设备树固定提交 [ccb9915](https://github.com/admin0330/Xiaomi-15-Pro-Device-tree/blob/ccb99153989d02640af781a34c8198b53dac3443/device/xiaomi/haotian/device.mk) 包含 ST NFC 扩展，仅用于选择研究方向，不替代当前手机的接口/权限/固件探测。此版本只完成软件兼容实现及模拟事务验证，不能宣称已经寻卡或刷屏成功。

旧上游 ST 路径还使用 root 注入解决 MIFARE 重选；本 App 没有引入该组件。即便 ST 短帧读回成功，系统也可能仍不派发目标 Tag。应以厂商应答、Tag/IsoDep/D1 和实际面板刷新逐级验收。
