# 姚广孝 · 跳过 m12-A 冒烟报告

日期：2026-11-27
APK：`0.3.1-m12` / versionCode `15`
模块：`v1.8.3` / versionCode `10086`

## 一、这版做了什么

### APK

- 新增「学习中心」：探针状态、候选、已学、队列、模块回写、电量/温度、学习当前 App。
- L0 界面探针：只在 `TYPE_WINDOW_STATE_CHANGED` 运行，只读 `viewIdResourceName` / `className` / 包名，复用已有无障碍快照，不额外 binder。
- L1 清单探针：每包每版本只查一次 `Activity/Service/Provider` 类名；`<queries>` 只声明 launcher 可见性，未申请 `QUERY_ALL_PACKAGES`。
- 候选：同包同版本命中 2 次自动确认；支持手动确认/忽略/移出已学。
- 文件：`files/learn/{learn_config.json,candidates.tsv,learned.tsv,queue.tsv,status.json}`；原子写、状态写盘 5 秒节流、IO 线程写盘。
- 无障碍服务：跳过总开关关闭时，状态事件仍可跑学习探针；内容事件不跑学习。
- 仍未申请 `INTERNET`，`isAccessibilityTool=true`。

### 模块

- `ygb learn-config`：三开关同步写 `learn_config.json`。
- `ygb learn-now [包名]`：写 `learn_request.json`（nonce 去重），不再依赖广播。
- `import-learn` 成功后写 `module_status.json`（版本/时间/新增/总数/挂载/规则/错误）。
- WebUI 学习卡显示「模块 v1.8.3 已导入 N 条（本次新增 N）」。
- 模块写入 App 私有目录时把文件属主改回 App UID、权限 0644，避免 App 不能重写自己的配置。

## 二、测试结果

| 项 | 结果 |
| --- | --- |
| APK JVM 单元/靶场 | 52 / 52 通过 |
| APK Release 编译 | BUILD SUCCESSFUL |
| APK 冒烟 | 11 / 11 通过（versionCode/versionName/包名/无 INTERNET/无 QUERY_ALL_PACKAGES/isAccessibilityTool） |
| 模块 P0 | 48 / 48 通过（含新增 module_status、learn_config、learn_request） |
| 模块学习器靶场 | 20 / 20 通过 |
| WebUI | 31 / 31 通过 |
| 模块完整沙盒 | **未跑完**：当前 VM 无 mount 权限，`tests/sandbox/run.sh` 在 bind mount 处失败；此前 109 项结果不能直接套到本版 |

### 包校验

- APK SHA-256：`64acb538809829d708f72280e98d6dd279c60bcc2666b054a40a73851f7b9f11`
- 模块 zip SHA-256：`269bfc5580c45292b76fc4dc99d389f01510758e03b0d6cd4b4b636af1d38ca0`
- 内置 APK SHA-256：同 APK
- 签名：Debug 证书，`apksigner verify` 通过
- 模块 `module.prop`：`version=v1.8.3`、`versionCode=10086`
- `customize.sh` / `service.sh` 的 `APP_VER=15`

## 三、没有验证到的

1. **没有真机/模拟器点击验证**：学习中心入口、无障碍开关、候选确认、模块回写都只过了编译与逻辑靶场。
2. **KSU 沙盒未装本版**：Windows 上 AVD 迁移后尚未复测快手，也没有安装 m12 APK + v1.8.3 模块做端到端点击。
3. **L1 实际覆盖率未知**：包可见性在 Android 13+ 下拉 `getPackageInfo` 可能只拿到 launcher 信息；需要 10 个样本 App 实测。
4. **深度补扫未接入**：m12-A 不读 dex、不启 JobScheduler；`deep` 默认 false，UI 只显示“待接入”。
5. **123 云盘未上传**：浏览器登录态仍缺，产物先落 CDN 链接。
6. **快手迁移后未复测**：之前 ANR 证据是 I/O + binder，不是 GPU；迁移到 NVMe/8GB/6 核后需要重跑。

## 四、下一步

- 在 KSU 沙盒装 `姚广孝跳过_v0.3.1-m12.apk` + `姚广孝BOSS_v1.8.3.zip`，点开学习中心、开无障碍、打开一个带广告 SDK 的 App，确认候选出现并导入。
- 用 10 个样本 App 统计 L0/L1 命中率；低于 7/10 再补 SDK 表。
- 快手复测；若仍 ANR，抓 `iowait`、binder、`dumpsys meminfo`。
- 123 云盘登录后补传 `GitHub项目/姚广孝BOSS/`。
