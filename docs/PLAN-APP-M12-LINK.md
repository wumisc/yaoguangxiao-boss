# 姚广孝 APK 新功能联动计划（m12）

> 定位：APK 负责“发现”，模块负责“拦截”，WebUI 负责“把两边接起来”。
> 目标版本：APK `0.3.1-m12`，模块 `v1.8.3`（P0 的 `v1.8.2` 已发布）。
> 一句话：不让手机发烫，也不让自动学习变成摆设。

---

## 一、这次要解决什么

P0 已经把模块侧发烫的根源拆掉了：旧 `adwatch` 停用、开机守护只查挂载、WebUI 三开关独立。但 P0 只完成了“停下来”，还没有完成“学起来”。

m12 要补的是 APK 侧的学习引擎，并把结果真正交回模块：

1. **APK 会学**：界面探针、清单探针、充电空闲时的延迟补扫。
2. **模块会用**：APK 写出 `learned.tsv`，模块 `import-learn` 校验后并入 `lists/learned.txt`。
3. **两边能互相看见**：WebUI 能看到 App 学习引擎状态；App 能看到模块上次导入结果。
4. **默认不发热**：L0/L1 不读 dex、不额外 binder、不后台常驻；L2 只在充电 + 熄屏 + 空闲时跑，每天最多 1 个 App。
5. **默认不误伤**：自动写入 hosts 默认关，保护域/白名单/无点域名/通配全部过滤，候选需要确认。

---

## 二、联动架构

```text
┌──────────────────────── APK（无 INTERNET、无 root） ────────────────────────┐
│ AccessibilityService                                                       │
│   └─ L0 界面探针：只读 viewId / className / 包名，匹配 SDK 类名前缀          │
│ PackageManager（当前前台包，不枚举全量）                                     │
│   └─ L1 清单探针：Activity / Service / Provider 类名，几十毫秒               │
│ JobScheduler（:learner 进程）                                               │
│   └─ L2 延迟补扫：充电 + 熄屏 + 空闲，ZipFile 流式读 classes*.dex 字符串      │
│ LearnStore                                                                  │
│   ├─ files/learn/candidates.tsv   候选                                      │
│   ├─ files/learn/learned.tsv      已确认，交给模块                          │
│   ├─ files/learn/status.json      App 状态，给 WebUI 读                     │
│   └─ files/learn/learn_config.json 联动配置，给 App/WebUI 双向读写           │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ 文件握手（root 可读写 App filesDir）
┌───────────────────────────────▼─────────────────────────────────────────────┐
│ KSU 模块                                                                     │
│   bin/ygb import-learn：校验、去重、合并 lists/learned.txt                    │
│   bin/ygb learn-request：写 learn_request.json，通知 App 立即学习              │
│   bin/ygb learn-config：读写 learn_config.json                               │
│   service.sh：开机导入一次，不轮询、不扫描                                     │
│   WebUI：三开关 + App 引擎状态 + 学习当前 App + 导入并应用                     │
│   module_status.json：导入完成后写回 App，显示“模块已导入 N 条”                │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 职责边界

| 能力 | APK | 模块 |
| --- | --- | --- |
| 发现候选 | 负责 | 不参与 |
| 读 dex / 清单 | 负责 | 永不读 |
| 确认候选 | 负责 | 只读结果 |
| 保护域 / 白名单 | 提供保护提示 | 最终校验（权威） |
| 写 hosts / 挂载 | 不碰 | 负责 |
| 自动应用 | 只写 `learned.tsv` | `learn_auto` 开才 apply |
| 配置入口 | App 设置页 + 学习中心 | WebUI 三开关 |
| 回写状态 | 写 `status.json` | 写 `module_status.json` |

**关键约束：APK 不申请 root，不直接改 `/data/adb`。** 所有跨边界动作都走 App `filesDir/learn/` 的文件；模块用 root 读写这个目录。这样 App 被卸载、模块被卸载都不会互相拖死。

---

## 三、文件契约（先冻结，再写代码）

统一目录：`/data/data/com.wumisc.skipper/files/learn/`

### 1. `learn_config.json`（配置，双向）

```json
{
  "schema": 1,
  "probe": true,
  "deep": true,
  "autoImport": true,
  "autoApply": false,
  "deepMaxPerDay": 1,
  "thermalLimit": 2,
  "updatedAt": 1764163200
}
```

- `probe`：L0 + L1 智能探针。
- `deep`：L2 深度补扫。
- `autoImport`：App 自动把确认结果写进 `learned.tsv`。
- `autoApply`：模块导入后是否立即重建并挂载 hosts；默认 `false`。
- `thermalLimit`：`0` 不限制 / `1` 温和 / `2` MODERATE 以上中止（默认）。
- App 是默认写入方；WebUI 通过 `ygb learn-config` 写入；App `FileObserver` 监听后重载。

### 2. `learned.tsv`（已确认，模块唯一读取的学习文件）

```text
# domain	sdk	pkg	source	confidence	updatedAt
ad.example.com	pangle	com.demo.app	ui	0.92	1764163200
```

- 只有 `source=ui|manifest|dex` 且已确认的域名才写入。
- 模块只读第一列；其余列用于 App 展示与排障。
- 上限 2000 条，超出按最近确认保留。

### 3. `candidates.tsv`（候选，App 内部 + WebUI 只读）

```text
# domain	sdk	pkg	versionCode	source	count	lastSeenAt	state
ad.example.com	pangle	com.demo.app	42	ui	2	1764163200	candidate
```

- `count`：同包同版本累计命中次数；默认 2 次才自动确认。
- `state`：`candidate|confirmed|ignored`。
- 同一域名多包出现时保留分行，模块导入时去重。

### 4. `queue.tsv`（待补扫队列）

```text
# pkg	versionCode	reason	enqueuedAt	attempts
com.demo.app	42	no-l0-l1	1764163200	0
```

- 只有 L0/L1 都没认出的包才进队列。
- 同包同版本只排一次；版本变化重新排队。
- 队列长度上限 50，超出丢最旧。

### 5. `status.json`（App 状态，WebUI 读）

```json
{
  "schema": 1,
  "appVersion": "0.3.1-m12",
  "running": false,
  "phase": "idle",
  "probe": true,
  "deep": true,
  "queue": 2,
  "candidates": 12,
  "learned": 5,
  "lastLearnAt": 1764163200,
  "lastScanAt": 0,
  "thermal": 0,
  "battery": 86,
  "error": ""
}
```

- 写入用“临时文件 + rename”，失败不覆盖旧文件。
- 状态更新节流 ≥5 秒，不在每个无障碍事件里写盘。

### 6. `module_status.json`（模块回写，App 读）

```json
{
  "schema": 1,
  "moduleVersion": "v1.8.3",
  "lastImportAt": 1764163200,
  "newCount": 2,
  "totalLearned": 7,
  "mounted": true,
  "rules": 15617,
  "error": ""
}
```

- 模块 `import-learn` 成功后写；失败写 `error`。
- App 只读展示，不据此执行任何 root 动作。

### 7. `learn_request.json`（WebUI → App）

```json
{"schema": 1, "action": "learn_now", "pkg": "", "nonce": "a1b2c3", "at": 1764163200}
```

- WebUI“学习当前 App”写这个文件，App `FileObserver` 收到后执行 L0/L1；不依赖广播权限。
- 同 `nonce` 不重复执行；App 处理后删除或标记。
- `pkg` 为空表示当前前台包。

---

## 四、APK 新功能与联动矩阵

| 新功能 | 用户看到什么 | 联动动作 | 默认 |
| --- | --- | --- | --- |
| **学习中心** | 探针状态、已学/候选/队列、上次学习、模块导入状态 | 读 `status.json` + `module_status.json` | 开 |
| **学习当前 App** | 一键对当前前台包跑 L0/L1 | 写候选，不读 dex | 开 |
| **深度补扫** | “仅充电 + 熄屏 + 空闲，每天最多 1 个” | 入 JobScheduler 队列；完成写 `learned.tsv` | 开 |
| **自动导入** | “确认后自动交给模块” | App 写 `learned.tsv`；模块开机/手动导入 | 开 |
| **自动应用 hosts** | “导入后立即生效” | 模块 `learn_auto`；默认只入库不挂载 | 关 |
| **候选确认** | 每个候选显示 SDK、包名、来源、命中次数 | 2 次自动确认；可手动确认/忽略 | 2 次 |
| **已学域名** | 按包/按 SDK/按来源筛选 | 可一键移出、加入白名单 | 只读 + 删除 |
| **模块联动状态** | “模块已导入 7 条 / 已挂载 / 规则 15617” | 模块写 `module_status.json` | 开 |
| **无障碍诊断** | 服务未启用、受限设置、HyperOS 引导 | 跳系统设置；不自动改系统 | 开 |
| **学习日志** | L0/L1/L2 事件、跳过原因、耗时 | 只在内存环形缓冲；调试开关才落盘 | 关 |
| **省电/温度保护** | 当前温度档、电量、下次允许扫描 | 温度 ≥ MODERATE 中止；低电不扫 | 开 |
| **一键暂停学习** | 停止探针和队列，保留已学 | 写 `probe=false/deep=false` | 开 |

---

## 五、分阶段实施

### M12-0：契约与无障碍底座（1–2 人日）

- 冻结上述 7 个文件 schema；写 JSON/TSV 编解码与单测。
- `accessibility_service_config.xml` 补 `android:isAccessibilityTool="true"`。
- 无障碍诊断：服务是否启用、Android 13+ 受限设置、HyperOS 入口提示。
- `AppGraph` 增加 `LearnStore`、`LearnSettings`、`LearnCoordinator`，不改变现有跳过链路。
- 性能底座先修：`Scoring` 正则缓存、`SnapshotMapper` 子节点 `break` 漏兄弟、`SuggestStore` 主线程整文件写、`markAlive` 5 秒写盘节流。
- 验收：现有 72 项靶场不回归；App 可启动、可开无障碍、可读写 7 个文件。

### M12-A：L0 + L1 + 学习中心（3–4 人日）

- `SdkCatalog`：`assets/learn/sdk_domains.tsv`，SDK 类名前缀 → 域名。
- `LearnProbe`：只在 `TYPE_WINDOW_STATE_CHANGED` 跑；复用已有节点快照，只读 `viewIdResourceName` / `className` / 包名。
- `ManifestProbe`：当前前台包首次打开/版本变化时查 Activity/Service/Provider 类名；`<queries>` 只声明 launcher 可见性，不申请 `QUERY_ALL_PACKAGES`。
- `LearnStore`：候选、确认、忽略、状态、队列；原子写、节流。
- `LearnCenterScreen`：首页卡片进入；学习当前 App、候选列表、已学列表、模块状态、开关。
- 模块侧 `import-learn` 接真实 `learned.tsv`；WebUI `appReady` 从 `status.json` 读取。
- 验收：10 个样本 App 中 L0/L1 能识别 ≥7 个；不读 dex、无后台服务、无 WakeLock；模块导入幂等。

### M12-B：L2 深度补扫（3–4 人日）

- `LearnQueue` + `LearnJobService`（`:learner` 进程，`BIND_JOB_SERVICE`）。
- Job 条件：`requiresCharging` + `requiresDeviceIdle` + `requiresBatteryNotLow` + `NETWORK_TYPE_NONE` + `setPersisted(true)`。
- `DexStringScanner`：`ZipFile` 流式读 `classes*.dex` 的 string table，不加载类、不执行代码；总解压 ≤24MB、CPU ≤10 秒、每天最多 1 个 App。
- 温度保护：`PowerManager.currentThermalStatus` ≥ MODERATE 中止；电量 <20% 不扫；屏幕亮不扫。
- 缓存：`pkg + versionCode + size + lastModified` 命中不重复扫；队列同包同版本只一次。
- 验收：L2 只在充电空闲触发；扫描后无临时 dex 文件、无 `unzip` 子进程；30 分钟典型使用温升 ≤3°C、耗电 ≤1%。

### M12-C：双向联动收口（2–3 人日）

- `ygb learn-config`：WebUI 三开关写入 `learn_config.json`；App `FileObserver` 重载。
- `ygb learn-request`：写 `learn_request.json`；App 执行后删除。
- `import-learn` 写 `module_status.json`；WebUI/状态页展示“上次导入 N 条”。
- `service.sh` 开机导入一次；不轮询、不扫描。
- App 未安装 / 模块未安装 / 配置损坏 / 文件不可写 的降级路径。
- 验收：WebUI 点“学习当前 App” ≤2 秒内 App 开始；导入后 ≤5 秒 App 显示模块结果；双方任一卸载不影响另一方。

### M12-D：测试与发布（2 人日）

- 单测：SDK 匹配、候选确认、TSV/JSON 编解码、Dex 字符串过滤、温度/电量闸门。
- 集成：模块沙盒 109 项 + P0 39 项 + WebUI 31 项 + App 现有 72 项 + m12 新增 ≥40 项。
- 真机：Android 12/13/14/15/16 各 1 台；HyperOS 4、KSU 4.2.0；30 分钟温度/耗电对比。
- 发布：APK `0.3.1-m12`（versionCode 14）；模块 `v1.8.3`；GitHub 只推源码；123 云盘 `GitHub项目/姚广孝BOSS/`。

---

## 六、省电与温度预算

| 层 | 触发 | 单次成本 | 频率上限 | 失败策略 |
| --- | --- | --- | --- | --- |
| L0 | 窗口状态变化 | <2ms，复用快照 | 每次窗口 1 次 | 无命中就过 |
| L1 | 包首次/版本变化 | 20–50ms，PackageManager | 每包每版本 1 次 | 查不到就跳过 |
| L2 | 充电 + 熄屏 + 空闲 | ≤10s CPU，≤24MB 解压 | 每天 1 个 App | 温度/电量/时间到就中止，下次再排 |

硬约束：

- 不加 `WakeLock`、不加前台服务、不加常驻轮询、不加 `AlarmManager` 精确闹钟。
- L2 不读网络、不下载 SDK 表、不上传任何东西。
- 不在 `TYPE_WINDOW_CONTENT_CHANGED` 上跑学习探针；跳过引擎照旧。
- `status.json` 写盘 ≥5 秒节流；`SuggestStore` 从主线程整文件写改为 IO 追加 + 节流。
- 学习队列和候选都有上限，避免越用越慢。

---

## 七、安全、隐私与权限

- **不联网**：APK 继续不申请 `INTERNET`；SDK 表随 APK 内置。
- **不枚举全量应用**：不申请 `QUERY_ALL_PACKAGES`；用 `<queries>` 只让 launcher 可见，L1 只查当前前台包；查不到就降级 L0/L2。
- **不读正文**：L0 只读类名/ID，L1 只读清单组件，L2 只读 dex 字符串；不读 text、不截图、不读聊天记录。
- **不 root**：App 不调用 `su`；模块用 root 读写 App filesDir；App 不直接改 `/data/adb`。
- **保护域**：`qq.com`、`baidu.com`、`alipay.com`、`taobao.com`、`jd.com`、`weibo.com`、`weixin.qq.com`、`tencent.com`、`bytedance.com`、`kuaishou.com`、`google.com`、`apple.com`、`microsoft.com` 等永不导入。
- **白名单**：`lists/white.txt` 最终权威；App 只提示，模块导入时再校验。
- **误伤**：SDK 表用精确前缀，不用子串；`csj`、`adjust` 这类短词必须带边界；候选默认 2 次确认。
- **导出**：学习结果可导出 TSV，用户自己决定是否分享；默认不自动上传。

---

## 八、测试矩阵

| 层 | 用例 | 目标 |
| --- | --- | --- |
| 单元 | SDK 前缀匹配、域名过滤、TSV 编解码、JSON schema、队列去重 | 全部通过 |
| 模块 | 109 项沙盒 + 39 项 P0 + 31 项 WebUI | 0 失败 |
| App | 现有 72 项 + m12 新增 ≥40 项 | 0 失败 |
| 联动 | 三开关同步、学习当前 App、导入回写、App/模块卸载降级 | 5 秒内状态一致 |
| 性能 | 30 分钟典型使用、充电空闲 L2、温度 ≥ MODERATE | 温升 ≤3°C，耗电 ≤1% |
| 兼容 | Android 12–16、HyperOS 4、KSU 4.2.0 | 无崩溃、无挂载失败 |
| 安全 | 保护域/白名单/无点/通配/重复 | 0 条非法导入 |

---

## 九、版本与发布

| 产物 | 版本 | 说明 |
| --- | --- | --- |
| APK | `0.3.1-m12` / versionCode 14 | 学习中心 + L0/L1 + L2 + 联动 |
| 模块 | `v1.8.3` / versionCode 10085 | 联动配置、request、module_status 回写 |
| 规则 | 不改 base/boost/white | 学习结果只进 `learned.txt` |
| GitHub | 只推源码 | 不发布 APK 或带 APK 的包 |
| 123 云盘 | `GitHub项目/姚广孝BOSS/` | APK + 模块 zip 同步 |

---

## 十、风险与降级

| 风险 | 处理 |
| --- | --- |
| Android 13+ 受限设置导致无障碍开不了 | `isAccessibilityTool=true` + 应用内诊断 + 系统设置跳转；不绕过系统 |
| HyperOS 杀后台 | 不保活；JobScheduler `setPersisted`；引导加白，不对抗 |
| 包可见性拿不到清单 | L1 跳过，L0/L2 兜底；不申请全量权限 |
| L2 读不到其他 App APK | 先 `sourceDir`，再 root `pm path` + `cat`；都不行就跳过，不报错 |
| JobScheduler 被延后 | 学习是“有空再学”，不承诺实时；UI 显示“等待充电空闲” |
| SDK 表漏了 | 只影响覆盖率，不影响跳过；候选可手动补 |
| 误伤登录/支付 | 保护域 + 白名单 + 默认不自动写 hosts |
| 文件握手失败 | App 设置页为权威；WebUI 标记“未连接”，不假装成功 |

---

## 十一、建议的第一批交付

先做 **M12-0 + M12-A**，不要一上来就写 L2：

1. 先把 7 个文件契约和 `isAccessibilityTool` 修好。
2. 先让 L0/L1 在学习中心跑起来，模块能导入、WebUI 能看到状态。
3. 真机跑一周，确认不发烫、不误伤、候选质量可接受，再开 L2。
4. L2 默认“仅充电空闲、每天 1 个 App”，先观察两周再考虑放宽。

这样即使 L2 还没上线，用户也能看到“学习当前 App → 候选 → 导入模块”的闭环；L0/L1 本身几乎不耗电，先把体验和信任建立起来。

---

## 十二、下一步

- 确认本计划后，先落 M12-0：文件契约、`isAccessibilityTool`、学习中心空壳、模块 `import-learn` 接真实文件。
- 再落 M12-A：L0/L1 + 候选确认 + 学习中心完整交互。
- L2 单独排期，不阻塞 m12 首发。
