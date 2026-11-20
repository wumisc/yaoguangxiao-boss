# 姚广孝 · 跳过 —— 前后端设计

> 一句话：**后端只回答「点不点」，前端只回答「你看得懂吗」**，两者共用同一份状态，所以它们本来就是一体。

## 一、先看别人踩过的坑

| 产品 | 它做对了什么 | 它踩了什么 |
|---|---|---|
| 李跳跳 | 一条通用规则覆盖九成开屏；规则可自己写；全程离线、体积小 | 2023 年被律师函后停更。"屏蔽/过滤广告"这条线不能碰 |
| GKD | 规则是声明式的，可导入导出、按包名分组，社区能共建 | 规则表达力强 = 误伤面也大，全靠用户自己把关 |
| 各种"跳过神器" | 装完即用，不需要配置 | 索要应用列表、联网、上报，隐私账算不清 |

所以这个 App 的三条硬约束：
1. **只点「跳过」，不屏蔽、不注入、不改别人的界面**（法务红线，也是产品红线）。
2. **不联网、不申请 QUERY_ALL_PACKAGES**，界面里显示的应用名从包名推导。
3. **默认就能用**：一条通用规则 + 两个场景包，用户只在遇到没覆盖的广告时才动手。

## 二、前端：四个页面，各管一件事

底部四个页签，不做二级菜单——每页只有一个主题，看一眼就知道在哪。

| 页面 | 它回答的问题 | 关键元素 |
|---|---|---|
| 首页 | 现在在不在工作？今天点了几次？ | 状态胶囊（运行中/未连接/等待心跳）、今日圆环、总开关、无障碍未开启提示条、最近一次命中 |
| 规则 | 它凭什么点？我能不能改？ | 「内置 / 我的」分段、通用包一行开关、视频包逐 App 列名、自定义规则增删 |
| 记录 | 它到底点过什么？ | 按天分组、每条显示 应用 + 点掉的词 + 置信度 + 时间、清空 |
| 设置 | 它什么时候停手？ | 工作窗口 / 冷却 / 单次上限（步进器）、震动、深色、保留记录、无障碍入口、免责声明 |

视觉规则（写进 `ui/theme/Theme.kt` 与 `ui/components/Kit.kt`，所有页面复用，不各写一套）：
- 只有**一个强调色**：金色 `#E3B25C`，对应图标里的和尚金像；红只用于"未连接/删除"，绿只用于"运行中"。
- 卡片统一 22dp 圆角、`surface` 底色、18dp 内边距；标题用 `subtitle`，正文 `body1`，说明 `footnote2`——三级字号，不再多。
- 动效只有两处：圆环进度（500ms）、页签下划线（跟随选中）。其余一律静止，避免"花"。
- 主开关的文案随状态变：**正在工作 / 已暂停**，而不是干巴巴的 on/off。

## 三、后端：四层，依赖只朝一个方向

```
engine/            纯 Kotlin，零 Android 依赖，可在 JVM 上跑单测
  Model.kt         Box / UiNode / WindowSnapshot（引擎眼里的世界）
  Rule.kt          SkipRule / EngineConfig / WindowState / Verdict
  Safety.kt        黑名单与危险词（红线）
  BuiltinRules.kt  内置规则库（通用 + 视频）
  Scoring.kt       打分：文字 0.62 + 顶部 0.10 + 尺寸 0.10 + 可点 0.12/0.06
  SkipEngine.kt    decide(window, rules, config, state, now) -> Verdict

data/              状态与持久化，只依赖 engine 的数据类型
  Settings.kt      设置 + 心跳（StateFlow）
  RuleStore.kt     内置规则 + 用户规则（URL 编码存 SharedPreferences）
  HitStore.kt      命中记录（内存 300 条 + JSONL 落盘）

platform/          唯一碰 Android 无障碍 API 的地方
  Snapshot.kt      把 AccessibilityNodeInfo 树压平成 UiNode 列表（深度 14 / 节点 240 上限）

service/           只做三件事：取快照、执行点击、上报
  SkipAccessibilityService.kt

ui/                只读状态、只发意图，不直接调 service
  AppRoot / MainActivity / theme / components / home / rules / log / settings
```

依赖方向是单向的：`ui → data ← service → platform → engine`。引擎不知道 Android 存在，UI 不知道 AccessibilityService 存在——**要改判定逻辑只动 engine，要改长相只动 ui**，两边不会互相拖着走。

## 四、联动契约：一体的四个接口

不是靠广播、不是靠轮询，而是**同一个进程里的同一组对象**（`SkipperApp` 持有 `AppGraph`，Activity 和 Service 都从 `context.graph` 取）。

| 方向 | 载体 | 效果 |
|---|---|---|
| UI → 引擎 | `SettingsStore.edit {}` → `StateFlow<Settings>` | 拨总开关/改窗口时长，Service 的 collector 立刻拿到新配置，**不用重启服务** |
| 引擎 → UI | `HitStore.hits: StateFlow<List<SkipHit>>` | 点掉一次，首页数字、圆环、记录页同时变，**没有延迟、没有刷新按钮** |
| 服务 → UI | `SettingsStore.connected` + `aliveAt`（每 5s 心跳） | 首页胶囊显示"运行中/等待心跳/未连接"，超过 12s 没心跳就判定服务掉了 |
| 维护 → 引擎 | `RuleStore.activeRules(settings)` | 分组开关 + 用户规则合成一份规则快照，每次评估现取，规则改完下一帧就生效 |

## 五、一次点击的完整链路

```
AccessibilityEvent(窗口变化)
  └─ 70ms 节流 → 取 rootInActiveWindow
      └─ SnapshotMapper：树 → UiNode 列表（带 bounds / clickable 链）
          └─ SkipEngine.decide(...)
              ├─ 包名在黑名单 → Idle(BLOCKED_PACKAGE)         ← 直接返回，连树都不看
              ├─ 窗口超过 3s → Idle(WINDOW_EXPIRED)
              ├─ 本窗已点满 2 次 → Idle(WINDOW_LIMIT)
              ├─ 距上次点击 <3s → Idle(COOLDOWN)
              ├─ 逐条规则 × 逐节点打分，取最高
              └─ 最高分 <0.80 → Idle(LOW_SCORE)               ← 宁漏不误
          └─ Skip → node.performAction(CLICK)，失败则沿父链最多 4 层
              └─ HitStore.record() + 可选震动 + 心跳正常
```

窗口状态（openedAt / hits / lastHitAt）由 Service 持有，随"包名+类名"变化重置——换界面即重新计时，这是"3 秒工作窗口"的实现方式。

## 六、安全阀（缺一不可）

1. 包名黑名单：systemui、permissioncontroller、settings、installer、IME、telecom、contacts 等。
2. 关键词黑名单：bank / pay / alipay / gov / tax / insur / fund / credit / loan …（命中即整包拉黑）。
3. 文字白名单式匹配：只认整词（`跳过`、`关闭广告`、`Skip`），`跳过登录`、`跳过下载` 一律排除。
4. 几何约束：按钮必须在窗口上半屏、宽度 ≤46%、高度 ≤14%，排除整屏可点区域。
5. 可点性约束：自身可点，或四层内存在可点祖先；纯文字不可交互的不点。
6. 节流三条：3s 工作窗口、3s 冷却、单窗 2 次。
7. 阈值 0.80：`文字(0.62)+顶部(0.10)+尺寸(0.10)+自身可点(0.12)` 才够；靠祖先可点只有 0.88 的边缘情况才会命中。

## 七、验证与产物

- 引擎单测 12 条（`app/src/jvmTest/.../EngineTest.kt`）：右上角命中、视频包命中视频规则、下半屏不点、跳过登录不点、支付包不碰、超时不点、冷却、上限、多候选取优、纯文字不点、整屏尺寸视为误判、系统界面不点。`./gradlew :app:jvmTest` 全绿。
- 构建：AGP 9.5.1 + Kotlin 2.4.21 + Compose 1.12.2 + Miuix 0.9.4，minSdk 29 / targetSdk 37，release 开启 R8，产物约 1.7MB。
- 下一步（P1，未做）：规则导入导出（剪贴板文本）、命中详情页展示节点几何、按 App 单独禁用、无障碍服务自愈提示。
