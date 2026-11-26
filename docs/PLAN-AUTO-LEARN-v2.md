# 自动学习 v3：三级探针方案（已确认）

> 本文件是 `PLAN-AUTO-LEARN-v2` 的修订版，替代原“充电时 adwatch 扫描”方案。
> 基线：模块 v1.8.1 / App 0.3.0-m11；目标版本：模块 v1.8.2 + App 0.3.1-m12。

---

## 一、结论

自动学习不再由模块的 shell 常驻循环驱动，改成 **App 内的事件探针 + 清单指纹 + JobScheduler 延迟扫描**。

一句话：

> **能用界面类名认出来的，不解包；能用清单认出来的，不扫 dex；只有都认不出的，才在充电、熄屏、空闲时扫一次，并且有硬预算。**

模块侧只负责“导入学习结果 + 写 hosts”，不再扫描 APK。

---

## 二、为什么换架构

旧方案的问题不是参数没调好，而是路径本身不省电：

- WebUI 的“自动学习”开关只控制 `learn_auto`（是否自动写 hosts），真正控制扫描的 `learn_off` 没有 UI 入口；用户关了开关，`adwatch` 还在跑。
- `adwatch` 每 30 秒两次 `dumpsys`，熄屏也不停。
- `adlearn` 对每个候选域名 fork 两次 `awk` + 一次 `grep`，复杂度 O(域名 × 候选文件)，首次学一个新 App 就起上千个子进程。
- `unzip -p classes*.dex` 全量解到 `/data` 再整包 `grep`，大包一次几十秒 CPU + 大量读写。
- `learn.cand` / `learned.txt` 无上限，越学越慢、越学越热。

继续在 shell 里加 `sleep`、加 `nice`、加冷却，只能缓解峰值，不能消除“常驻 + 定时 + 全量解包”这三个根。

---

## 三、三级探针

### L0：界面探针（接近 0 成本）

**触发**：无障碍服务收到 `TYPE_WINDOW_STATE_CHANGED`，且已经为跳过判定构建了节点快照。

**做法**：

- 只检查每个节点的 `viewIdResourceName` 和 `className`，不读 `text`、不读 `contentDescription`、不截图。
- 匹配广告 SDK 的类名前缀，例如：
  - `com.bytedance.sdk.openadsdk`（穿山甲）
  - `com.qq.e.ads`（广点通）
  - `com.kwad.sdk`（快手）
  - `com.google.android.gms.ads`（AdMob）
  - `com.applovin`
  - `com.unity3d.ads`
  - `com.ironsource`
  - `com.vungle`
  - `com.mintegral`
  - `com.sigmob`
  - `com.baidu.mobads`
- 命中后写一条 `包名 + versionCode + SDK token + 时间` 到内存队列；每个包每个版本只记一次。
- 用内置 `SDK → 域名` 表直接得到候选域名，**完全不读 APK**。
- 批量落盘：内存里攒着，亮屏期间每 5 秒或熄屏时写一次；不按事件写文件。

**成本**：复用已有的节点遍历，只多几次字符串前缀比较；不额外 binder 调用，不额外进程。

**边界**：只认类名/ID 里带 SDK 包名的；混淆到看不出 SDK 的，进入 L1/L2。

### L1：清单探针（低成本）

**触发**：某个包第一次出现，或 `versionCode` 变了。

**做法**：

- 用 `PackageManager.getPackageInfo(pkg, GET_ACTIVITIES | GET_PROVIDERS | GET_SERVICES)` 读清单里的组件类名。
- 匹配同一张 SDK 前缀表；很多 SDK 会把自己的 Activity/Provider 写进清单，例如 `com.bytedance.sdk.openadsdk.activity.TTAdActivity`、`com.qq.e.ads.ADActivity`、`com.kwad.sdk.api.proxy.app.AdActivity`。
- 命中就由 `SDK → 域名` 表出候选，不读 dex。
- 在后台线程执行，单包预算 50ms；结果按 `pkg + versionCode` 缓存。

**成本**：一次 PackageManager 查询，几十毫秒；没有解压、没有 grep、没有子进程。

**边界**：清单里不暴露 SDK 的，进入 L2。

### L2：延迟扫描（只处理未知 SDK）

**触发**：L0/L1 都没认出来，或认出了 SDK 但域名表里没有对应域名。

**做法**：

- 把 `pkg + versionCode` 放进 App 的待扫队列（最多 50 条，按时间淘汰）。
- 用 `JobScheduler` 调度，条件：
  - `setRequiresCharging(true)`
  - `setRequiresDeviceIdle(true)`
  - `setRequiresBatteryNotLow(true)`
  - `setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)`
  - `setPersisted(true)`（需要 `RECEIVE_BOOT_COMPLETED`）
- `JobService` 放在独立进程 `:learner`，`android:process=":learner"`，`exported=false`，`permission=BIND_JOB_SERVICE`。
- 每次 Job 只扫一个包：
  - `ZipFile` 打开 `ApplicationInfo.sourceDir` 和 `splitSourceDirs`；
  - 遍历 `classes*.dex`，分块读取，块间保留 256B 重叠，防止域名跨块漏掉；
  - 总解压上限 24MB，单次 CPU 上限 10 秒；
  - 预编译域名正则，不在循环里 `Regex(p)`；
  - 不写临时 dex 文件，不 fork `unzip` / `grep` / `awk`。
- 线程优先级 `THREAD_PRIORITY_LOWEST`；开始前、每 5 秒、结束后检查 `PowerManager.currentThermalStatus`；`MODERATE` 以上直接放弃，下次再说。
- 扫过的按 `路径 + versionCode + size` 缓存，永不重复扫。
- 每天最多 1 个 App；失败指数退避，最早 24 小时后再试。

**成本**：只在充电 + 熄屏 + 空闲时发生；单次有硬上限，不影响前台使用。

### L3：规则表（可选，减少重复扫描）

- App 内置 `assets/learn/sdk_domains.tsv`：`SDK token → 域名列表`。
- 规则包更新时一起更新这张表；已知 SDK 永远不扫 dex。
- 可选匿名上报只包含 `SDK token`，不包含包名、域名、用户数据，默认关闭；不做也能用。

---

## 四、职责划分

### App 侧（学习引擎）

- `LearnProbe`：挂在 `SkipAccessibilityService` 的窗口事件里，做 L0。
- `ManifestProbe`：首次打开/版本变化时做 L1，后台线程。
- `LearnQueue` / `LearnStore`：候选、已确认、待扫队列、状态。
- `LearnJobService`：L2，JobScheduler + `:learner` 进程。
- `DexStringScanner`：流式 dex 字符串扫描，纯 Kotlin，无 shell。
- `LearnSettings`：探针开关、深度补扫开关、自动写入开关（本地持久化）。

App 写出的文件（内部 `files/learn/`）：

```text
candidates.tsv   domain<TAB>pkg<TAB>versionCode<TAB>source<TAB>count<TAB>lastAt
learned.tsv     domain<TAB>sdk<TAB>pkg<TAB>source<TAB>confidence<TAB>updatedAt
queue.tsv       pkg<TAB>versionCode<TAB>reason<TAB>addedAt
status.json     {probe, deep, queue, cand, learned, lastScan, thermalPaused}
```

`source` 取值：`ui` / `manifest` / `dex` / `manual`。

### 模块侧（只做导入与应用）

- 删除/停用 `bin/adwatch` 的扫描职责；升级时 kill 旧进程，`service.sh` 不再启动它。
- 新增 `bin/ygb import-learn`：
  - 以 root 读 App 的 `files/learn/learned.tsv`；
  - 校验域名、白名单、保护域；
  - 合并进 `lists/learned.txt`，去重、限 2000 条；
  - `learn_auto` 开着才 `apply`，否则只入库。
- `bin/ygb learn-status`：读 App 的 `status.json` + 模块侧计数，输出 JSON。
- `service.sh`：
  - 开机不再起 `adwatch`；
  - 守护循环改调轻量 `ygb mounted`；
  - 可选 `learn-import` 轻循环：仅充电时每 30 分钟检查一次 App 的 `learned.tsv` 哈希，有变化就 import + apply。它只 `cat` 一个文件，不扫 APK。
- WebUI：
  - “智能探针”开关（L0+L1）
  - “深度补扫”开关（L2，仅充电空闲）
  - “确认后自动写入 hosts”开关（默认关）
  - “立即导入并应用”按钮
  - 状态：探针/深度状态、队列、候选、已确认、上次扫描、上次导入

### 配置桥

App 是学习引擎，配置也由 App 持有。WebUI 需要控制时，走一条轻量桥：

1. 优先：WebUI 以 root 写 App 外部目录的 `learn_config.json`，App 用 `FileObserver` 监听并重载；
2. 备选：显式广播 `com.wumisc.skipper.action.LEARN_CONFIG`，App 内受控 Receiver 处理；
3. 都不可用：以 App 设置页为准，WebUI 只读状态。

不论哪种，最坏只能改“探针/补扫/自动写入”三个开关，不能执行任意命令。

---

## 五、省电与温控预算

| 项 | 预算 |
| --- | --- |
| L0 单次窗口事件 | ≤1ms，无额外 binder |
| L0 落盘 | 每 5 秒或熄屏批量一次，不按事件写 |
| L1 单包 | ≤50ms，仅 PackageManager 查询 |
| L2 单包 | ≤24MB dex、≤10 秒 CPU、只 1 个包 |
| L2 频率 | 每天 ≤1 个 App；失败 24 小时退避 |
| L2 运行条件 | 充电 + 熄屏 + 设备空闲 + 电量 >30% |
| 温度 | `MODERATE` 以上暂停；降回 `LIGHT` 再恢复 |
| 模块导入 | 开机一次 + 充电时每 30 分钟查一次哈希 |
| 模块轮询 | 不调用 `dumpsys`，不读前台，不扫 APK |
| hosts 重建 | 只在导入有变化或用户点“应用”时；不在前台按事件重建 |

---

## 六、数据与确认规则

### 候选

- 一个域名进 `learned.tsv`，满足任一：
  1. 出现在 ≥2 个不同包名；
  2. 同一包出现在 ≥2 个不同 versionCode；
  3. 用户手动加入。
- 保护域、白名单、主域永不进入。
- 保护域做后缀匹配：`qq.com` 保护 `foo.qq.com`；白名单沿用 `*.foo.com` 放行整棵子树。

### 上限与清理

| 文件 | 上限 | 清理 |
| --- | --- | --- |
| `candidates.tsv` | 3000 | 超限按 `lastAt` 淘汰最旧；30 天未出现淘汰 |
| `learned.tsv` | 2000 | 超限淘汰最旧，手动加入的保留 |
| `queue.tsv` | 50 | 超限丢最旧 |
| 模块 `lists/learned.txt` | 2000 | import 时按同样规则合并 |
| `logs/learn.log` | 500 行 | 保留最近 300 行 |

### 迁移

- 旧 `learned.txt` 原样保留，不重新验证、不删除。
- 旧 `learn.cand` 不再使用；可保留作参考，或 `ygb learn-clear` 清掉。
- 旧 `state/learn_off`：存在则探针 + 深度补扫都关；不存在则用新默认。
- 旧 `state/learn_auto`：保留，继续控制自动写 hosts。
- 旧 `adwatch.pid`：升级时 kill 并删除；`service.sh` 不再启动。

---

## 七、默认值

| 开关 | 默认 | 说明 |
| --- | --- | --- |
| 智能探针（L0+L1） | 开 | 成本接近 0，只读类名/清单 |
| 深度补扫（L2） | 仅充电空闲 | 每天最多 1 个 App |
| 自动写入 hosts | 关 | 保持“只收集，用户确认才生效” |
| 模块自动导入 | 开 | 只读 App 结果，不扫描 |

如果用户只想要“绝对零后台”，把智能探针也关掉，只留“学习当前 App”手动按钮。

---

## 八、实施阶段

### P0：拆掉旧路径，建立新接口（1 天）

1. `service.sh` 不再启动 `adwatch`；升级时 kill 旧 `adwatch.pid`。
2. `bin/ygb` 新增 `import-learn`、`learn-status`、`learn-now`（转调 App 或提示）。
3. 模块 `ui-status` 增加学习字段；计数改为读 `state/counts`，不再每次 `count_valid`。
4. WebUI 去掉“一个开关管所有”，改成三个独立控制 + 状态。
5. `customize.sh` 把 `rules.ver` / `app.ver` 种子移到 `return 0` 之前。

验收：

- 升级后 `ps -A | grep adwatch` 为空。
- 关掉探针/深度后，模块不产生任何 `dumpsys`、不读前台、不扫 APK。
- 开机守护不再调完整 `status`。

### P1：App L0 + L1（2 天）

1. `LearnProbe` 接入 `SkipAccessibilityService` 的 `TYPE_WINDOW_STATE_CHANGED`。
2. `ManifestProbe` + `SdkMap`（assets TSV）。
3. `LearnStore` 内存队列 + 批量落盘。
4. App 设置页：探针开关、状态。
5. JVM 测试：SDK token 识别、清单识别、去重、批量写。

验收：

- 已知 SDK 的 App 首次打开后 1 秒内出候选，不读 dex。
- 同一个包同版本只记一次。
- L0 不增加额外 binder 调用；关闭探针后零记录。

### P2：App L2（2 天）

1. `LearnJobService` + `JobScheduler` 约束 + `:learner` 进程。
2. `DexStringScanner`：ZipFile 流式、分块、24MB/10s 上限、预编译正则。
3. 队列、缓存、退避、温度/电量检查。
4. 测试：假 APK（构造 dex 字符串）、超限跳过、温度中止、版本缓存。

验收：

- Job 只在充电 + 熄屏 + 空闲时运行。
- 单包不产生临时文件，不 fork 子进程。
- 50MB dex 在中端机 ≤10 秒；超时/超温可中止。
- 同包同版本不重复扫。

### P3：模块导入 + WebUI + 桥（1 天）

1. `ygb import-learn`：校验、去重、限 2000、保护域/白名单。
2. `learn-import` 轻循环（仅充电，30 分钟查一次哈希）。
3. WebUI 三开关、状态、手动导入按钮。
4. 配置桥（外部 JSON / 广播 / App 设置页兜底）。
5. 文档：使用说明、默认值、回退。

验收：

- 导入幂等：重复 import 不增加重复行。
- `learn_auto` 关时只入库不写 hosts；开时才 apply。
- WebUI 状态与 `ps`、`status.json`、`learned.txt` 一致。
- 导入只读 App 文件，不触发扫描。

### P4：测试与发布（1 天）

1. 扩展 `tests/adlearn-range.sh`：L0/L1 映射、导入、保护域、上限、幂等。
2. 扩展 `tests/webui/test.js`：三开关独立、状态渲染、手动导入。
3. 扩展 `tests/sandbox/suite.sh`：无 `adwatch`、`import-learn`、`mounted`、计数缓存。
4. 回归：现有 101 项模块 + 26 项 APK 静态靶场全过。
5. 真机：30 分钟对比（探针开/关、深度补扫开/关）温度、`top` CPU、熄屏唤醒。
6. 发布：模块 v1.8.2 / App 0.3.1-m12；GitHub 只更新源码，不发布 APK；编译产物同步 123 云盘 `GitHub项目/姚广孝BOSS`。

---

## 九、验收总表

| 编号 | 验收项 | 通过标准 |
| --- | --- | --- |
| A1 | 旧路径拆除 | 升级后无 `adwatch` 进程；`service.sh` 不启动它 |
| A2 | 关闭探针 | 不读类名、不读清单、不扫 dex、不写候选 |
| A3 | L0 | 已知 SDK 1 秒内出候选；无额外 binder；每包每版本一次 |
| A4 | L1 | 清单识别 ≤50ms；不读 dex |
| A5 | L2 条件 | 仅充电 + 熄屏 + 空闲 + 电量 >30% |
| A6 | L2 预算 | 单包 ≤24MB、≤10 秒、每天 ≤1 个 |
| A7 | L2 温度 | `MODERATE` 以上中止，恢复后继续 |
| A8 | 无临时文件 | 扫描后无 `dex.*` 临时文件、无 `unzip` 子进程 |
| A9 | 缓存 | 同包同版本不重复扫；版本变化重扫 |
| A10 | 确认 | 2 包或 2 版本才自动确认；手动加入例外 |
| A11 | 安全 | 保护域/白名单不进 learned；`foo.qq.com` 被保护 |
| A12 | 导入 | 幂等；`learn_auto` 关只入库不 apply |
| A13 | WebUI | 三开关独立；状态准确；手动导入可用 |
| A14 | 开机 | 守护循环只查挂载，不跑完整 `status` |
| A15 | 温度 | 30 分钟典型使用，温升 ≤3°C（相对基线） |
| A16 | 回归 | 101 项模块 + 26 项 APK + 学习器靶场全过 |

---

## 十、风险与取舍

- **L0/L1 会漏**：混淆到没有 SDK 类名/清单痕迹的，只能等 L2；不会即时生效。
- **L2 可能被系统延后**：JobScheduler 在 Doze 下不保证时间；学习是“有空再学”，不是实时。
- **读其他 App APK 可能被 SELinux 拦**：优先 `sourceDir`/`splitSourceDirs`；读不到退回 root `pm path` + `cat`；再不行跳过，不报错。
- **包可见性**：App 需要 `QUERY_ALL_PACKAGES`（侧载不受 Play 限制）；没有就只学当前前台包。
- **SDK 域名表需要维护**：表越全，L2 越少；表漏了会多扫几次，但不影响跳过。
- **隐私**：L0 只读类名/ID，L1 只读清单，L2 只读 dex 字符串；不读正文、不截图、不抓包、不上传。可选上报只含 SDK token，默认关。
- **不做**：不抓包、不代理、不 VPN、不改 DNS、不自动写 hosts、不在前台重建 hosts。

---

## 十一、下一步

按 P0 → P1 → P2 → P3 → P4 执行。P0 可以先单独出 v1.8.2（拆旧路径 + 开关 + 导入），P1/P2 的 App 学习引擎随后出 m12。

如果只想先验证“不发热”，先做 P0 + 关掉旧 `adwatch`，再在真机上跑 30 分钟对比；确认基线后再投入 L0/L1/L2。

---

## 十二、P0 落地记录（v1.8.2，2026-11-26）

### 12.1 已改文件

| 文件 | 内容 |
|---|---|
| `bin/ygb` | 计数缓存 `state/counts`；`probe_on/deep_on/import_on`；`import-learn`；`learn-status`；`mounted`；保护域/白名单/无点域名/通配/重复校验；`learn-now` 桥接占位 |
| `service.sh` | 升级时 kill 旧 `adwatch` 并删 pid；不再启动 adwatch；开机执行一次 `import-learn`；90 秒守护改为 `ygb mounted` |
| `bin/adwatch` | 默认 legacy 门，直接退出 0；只有 `state/learn_legacy_adwatch` 存在才允许旧扫描 |
| `customize.sh` | `rules.ver` 种子移到所有 `return 0` 之前，首次开机不再拉全量 |
| `module.prop` | `v1.8.2` / `10084`，description 缩短 |
| `webroot/*` | 自动学习卡拆成「智能探针 / 深度补扫 / 自动写入 hosts」三开关；新增「学习当前 App」「导入并应用」；状态新增 probe/deep/importAuto/appReady/queue/lastImport |
| `updates/manifest.txt` | 版本升到 `10084`，base/boost/white 哈希与行数刷新 |

### 12.2 开关语义

- `state/learn_probe_off`：不存在 = 智能探针开（L0+L1，App m12 读取）。
- `state/learn_deep_off`：不存在 = 深度补扫开（L2，仅充电+熄屏+空闲）。
- `state/learn_import_off`：不存在 = 开机自动导入 App 结果。
- `state/learn_auto`：存在 = 导入后自动写 hosts；默认关。
- `state/learn_off`：旧兼容，等价于探针+深度都关；新命令会同步拆成两个新标记，避免互相冒充。

### 12.3 验收结果（当前 VM）

- `tests/p0-learn.sh`：PASS=39 FAIL=0（版本、三开关独立、模式映射、导入校验、adwatch 停用、customize 种子、mounted）。
- `tests/adlearn-range.sh`：PASS=20 FAIL=0（旧学习器回归）。
- `tests/webui/test.js`：PASS=31 FAIL=0（新增三开关独立、按钮、状态字段）。
- `unshare -rm bash tests/sandbox/run.sh`：PASS=109 FAIL=0（完整模块靶场；用户命名空间内 bind mount）。

### 12.4 尚未完成

- 未在真机 KSU 上安装 v1.8.2，未做 30 分钟温升/耗电对比。
- App m12（L0 `LearnProbe`、L1 `ManifestProbe`、L2 `LearnJobService`/`DexStringScanner`、`LearnStore`、配置桥）尚未开始。
- P0 的探针/深度开关目前只是模块侧标记；App m11 不读取，真正生效要等 m12 配置桥。
- `learn-now` 在 App 未接入时返回明确错误；WebUI 按钮在 `appReady=0` 时禁用。

---

## 十三、APK 联动计划

m12 的具体功能、文件契约、分阶段实施与验收见 `docs/PLAN-APP-M12-LINK.md`。核心顺序是：先 M12-0 契约与无障碍底座，再 M12-A（L0/L1 + 学习中心 + 模块导入），L2 单独排期，不阻塞首发。
