# 兼容性与验证范围

开发设备为小米 17 Pro Max / HyperOS 4，通过 Shizuku 的 shell 权限调用厂商 NFC 接口。无需 root 不等于所有 Android 手机兼容；普通 ISO-DEP 发现与 Q-Poll 有差异。

目标是保留完整 NFC 主控和线圈的华为 3.98 英寸 552×768 四色屏。D1 校验几何、色数和 UID 后才允许上传。部分固件报告 552×1536/2 色，协议层将其解释为 552×768/4 色。

测试覆盖协议边界、模拟上传/刷新、低电压、超时、图像量化、缩放裁切和编辑状态。模拟测试不能证明实际射频供电或面板刷新成功。图像参考来自 Pillow 10.1.0 和 InkSight，指纹见 `tests/inksight-fixtures/reference.json`；输入为合成数据，协议测试 UID 也为合成值。

开发期间已观察到寻卡、D1 和照片刷新；细密抖动照片曾出现 68CA，外部供电后观察到完成。以上是测试现象，不是通用接线指导。本仓库不包含 ESP32 固件。

0.5.5 新增 ST54L 的独立实验后端，只在运行时 `getChipId()` 明确返回 ST54L 且用户开启实验模式时选择。移植上游 `ink-nfc-lab/RootProbe.java` 的 ST vendor NCI GID=0F/OID=01 自定义轮询帧事务（读取 0x15、设置 0x13），保存原值后设置 `012A020A7A`，对设置与恢复分别读回；不依赖 AOSP RF discovery getter。共享 ReaderMode 同时解析四/五参数签名，不猜 Binder transaction 编号。

NXP 路径保留独立 profile 与 RF 快照门禁；不会因 ST 模式或无读回开关向未知控制器发送 NXP QTag 配置。ST 路径也不接受未知 ST 芯片，必须能注册标准 vendor NCI callback、暂停/恢复轮询、读出可解析的 ST polling 配置后才可写入。

小米 15 Pro 已完成接口盘点与失败日志采集，专用寻卡和刷屏尚未成功。公开参考设备树固定提交 [ccb9915](https://github.com/admin0330/Xiaomi-15-Pro-Device-tree/blob/ccb99153989d02640af781a34c8198b53dac3443/device/xiaomi/haotian/device.mk) 包含 ST NFC 扩展，仅用于选择研究方向，不替代当前手机的接口/权限/固件探测。此版本只完成软件兼容实现及模拟事务验证，不能宣称已经寻卡或刷屏成功。

旧上游 ST 路径还使用 root 注入解决 MIFARE 重选；本 App 没有引入该组件。即便 ST 短帧读回成功，系统也可能仍不派发目标 Tag。应以厂商应答、Tag/IsoDep/D1 和实际面板刷新逐级验收。

## 0.5.7 多设备入口

原先检测/推图入口固定走 Shizuku Q-Poll，且控制器识别必须存在 `mi_nfc`。本版开放已有标准 ISO-DEP 传输流程，并将厂商识别改为可选的只读探测：优先使用实际服务返回的信息，支持小米框架位于 system_ext / system / product 的路径。缺少小米服务不会阻断标准通道；不会仅凭品牌、机型、配置文件或猜测的芯片名发送私有 NCI。

| 场景 | 本版行为 | 验证边界 |
|---|---|---|
| 任意品牌，系统能把目标屏激活为 ISO-DEP | 标准 NFC 读取 D1；目标校验通过后可传图 | Android 12 接口夹具、Android 14 框架模拟测试；其它手机的空口和刷新未实测 |
| 无 Shizuku / 无小米服务 | 可用标准检测和推图入口 | 无法用标准 API 代发 7A 唤醒 |
| 正向 NXP_MW 能力、vendor NCI 与 ReaderMode | 自动模式可选择原 NXP 后端 | 仍需对应 ROM 权限与空口测试 |
| NXP 缺少 RF getter | 默认尝试标准；明确启用无读回试验后才用 NXP 专用路径 | 无法核验 RF 配置完全恢复 |
| 实读 ST54L，已启用试验且接口齐全 | 原 ST54L 快照/读回/恢复后端 | 配置成功不等于已发现屏幕 |
| Android 12 标准 NFC 接口，没有 vendor NCI | 标准通道；报告缺少专用接口 | 不宣称支持特殊 7A |
| ST54J/K/T、未知控制器或错误接口签名 | 标准通道；不发送未验证的厂商配置 | 需要对应后端和真机证据 |

只读兼容报告不会自动证明手机能刷屏；新增机型仍以“寻卡 → ISO-DEP → 正确 D1 → 传图 → 实屏刷新”逐项记录。当前没有连接待测手机，本次新增验证为 API 夹具和 Android 框架模拟。

参考：[上游多设备分析](https://github.com/wiwiwei/ink-nfc-lab/blob/main/docs/DEVICE_COMPATIBILITY.md)、[Android 12 INfcAdapter](https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/nfc/INfcAdapter.aidl)。Android 12 参考接口包含四参数 ReaderMode，未提供本项目所用的 vendor NCI / RF getter；厂商 ROM 可能另有扩展，以实际检测为准。

## 0.5.8 ST54L 桥接 / 原生通道

小米 15 Pro（2410DPN6CC / haotian），Android 16 / HyperOS `OS3.0.309.0.WOBCNXM`，实读芯片 ST54L、固件 `02.04.1188`。vendor NCI、callback、`pausePolling(long)`、`resumePolling()` 可见；旧桥接命令 GID `0F` / OID `01` / payload `15` 返回 `15 0B`，在设置短帧、Tag、D1 和图片传输之前停止。

ST 公开实现将旧桥接 getter `15` 转为原生 **OID `1E` / 空 payload**，setter `13` 转为 **OID `1D` / 帧列表**。其 `NCI_STATUS_NOT_SUPPORTED` 定义为 `0B`。本机日志未看到对应的 HAL 发送记录，故中间件拒绝也是可能原因；这不是“线圈未对准”或屏幕低电压的证据，也不能凭参考源码证明本机使用相同中间件版本。

本版仅在未固定通道、旧 getter 明确返回 `15 0B` 时探测原生 getter；读到格式有效的原始帧后保存快照和通道，再发送 `012A020A7A`。该 7A 帧未设 CRC 自动添加位，因此原生设置 payload 与桥接帧列表相同。恢复原生配置时保留原始字节（包括已有 CRC），不再经桥接转换。设置后读回不符或应答丢失会尝试原通道恢复；恢复未核验时保留快照并阻止新会话。旧快照缺少 channel 字段时仍走 bridge；未知通道拒绝处理。

若读帧仍遭非零状态拒绝，且后台确认 `st_frames_configured=false`、`restore=not_required`、`running=false`、`q_discovery_sent=false`，拒绝码与异常一致，自动模式才回退标准 NFC。超时、设置阶段失败、清理状态不确定和显式 Q-Poll 诊断不触发此回退。标准 NFC 仍可能无法替代 7A 唤醒。

日志保留 OID、原始响应、选定的 `st_frame_transport`、拒绝状态和恢复状态。Shizuku 诊断服务升级到 10，防止旧后台代码继续运行；更新后如提示后台较旧，请重启 Shizuku 并重新连接。

纯 Java 夹具覆盖桥接/原生协商、先保存后设置、含 CRC 原配置精确恢复、读回不符、应答丢失、两路不支持、畸形响应、超时和持久快照恢复；Android 框架集成验证标准回退和无标签时不发送 APDU。**手机当前未连接，本版原生应答、Tag、D1 和实屏刷新均未实测。**

源码依据（固定 Git blob，已核对下载字节）：

- [ST 命令转换](https://api.github.com/repos/LineageOS/android_hardware_st_nfc/git/blobs/e39e459f7759cdb50e7dbd5be793a11db27dfa53)：getter 转换、setter 转换、原生响应封装。
- [ST 命令常量](https://api.github.com/repos/LineageOS/android_hardware_st_nfc/git/blobs/9bab334878d26ac36780d5b914ba5f667ed1fda6)：OID `1D/1E` 与桥接子命令 `13/15`。
- [ST nci_defs.h](https://api.github.com/repos/LineageOS/android_hardware_st_nfc/git/blobs/a7b41a7d9a90c1c7717fdc014b45f69e23dc0eda)：`NCI_STATUS_NOT_SUPPORTED=0x0B`。
