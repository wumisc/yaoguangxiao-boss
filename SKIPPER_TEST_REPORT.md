# 姚广孝 · 跳过 —— 功能测试报告 / 靶场

日期：2026-11-20　被测版本：`0.2.4-m5`（versionCode 7）　模块：`v1.7.6`
结论：**四层靶场全部通过，0 失败**（L1 场景 29 条 / 引擎用例 26 项 / 专项包 5 项 / L4 APK 静态 26 项 / 装机 7 项 / 模块 81 项 / 学习器 16 项）。

## 零、这一版为什么发（真机一次都没跳过）

真机反馈「开了无效、没一次跳过成功」，靶场却全绿——因为靶场只验证判定，没验证真机运行态。
按「零命中」最可能的原因逐条查，定位到一条硬缺陷并修掉：

- **倒计时后缀没认**：真机的跳过按钮几乎都带数字，「跳过 5」「跳过广告 3s」「关闭广告5秒」。
  旧规则只认纯「跳过」，正则里连秒数都不允许，等于把绝大多数开屏都排除了。
  现在核心词表 + 可选倒计时（`数字 + s/S/秒`，单位可省），并先把「跳过登录/注册/领取…」排除再匹配。
- **文字清洗**：新增 `Labels.normalize`，去掉零宽空格、不间断空格和尾部 `| ｜ › > » · 、 ，`，
  「跳过｜」「跳过 ›」这类截图里常见的写法不再漏。
- **取树兜底**：`rootInActiveWindow` 为 null 时退到 `windows` 里找活动窗口，避免部分 ROM 上直接空转。
- **新增调试日志开关**：真机出问题不再靠猜，日志里能直接看到「事件到了没 / 节点多少 / 为什么没点」。

## 一、靶场怎么设计的

没有真机、没有模拟器，所以把"功能"拆成四层，每层都能在机器上真跑：

| 层 | 测什么 | 怎么造 | 入口 |
|---|---|---|---|
| L1 场景靶场 | 单界面判定：点不点、为什么点 | `scenarios.tsv` 一行 = 一个真实界面 | `./gradlew :app:jvmTest` |
| L2 时序靶场 | 一串事件：冷却、上限、切窗、节流、超时 | 代码驱动 `SkipSession` | 同上 |
| L3 存储靶场 | 规则/记录存取往返、脏数据 | 纯 Kotlin 编解码 | 同上 |
| L4 交付靶场 | APK 能不能装、有无越权、服务声明对不对；模块装机链路 | aapt2 / dexdump / apksigner / 假 pm | `bash tests/app-range.sh`、`bash tests/app-install-range.sh` |

## 二、结果

### L1 场景靶场（29 条场景，1 个用例聚合，全部通过）

| 场景 | 期望 | 结果 |
|---|---|---|
| 爱奇艺 右上角「跳过」 | 点 | PASS |
| 优酷「关闭广告」 | 点（视频规则） | PASS |
| 腾讯视频「Skip」 | 点 | PASS |
| 微信 顶部右侧「跳过」 | 点（通用规则） | PASS |
| 夸克浏览器「跳过」 | 点 | PASS |
| 芒果TV 按钮不可点但父可点 | 点（靠祖先） | PASS |
| 爱奇艺 文字带空格 | 点 | PASS |
| 腾讯视频 横屏「跳过」 | 点 | PASS |
| 英文「Skip Ad」 | 点 | PASS |
| 底部悬浮「跳过」 | 不点（下半屏） | PASS |
| 超宽按钮（占 55% 宽） | 不点（尺寸误判） | PASS |
| 超高区域（16.7% 高） | 不点 | PASS |
| 超小按钮（30px） | 不点 | PASS |
| 「跳过登录」 | 不点（危险词） | PASS |
| 「跳过并领取新人礼」 | 不点 | PASS |
| 纯文字不可点、无祖先 | 不点 | PASS |
| 工商银行 | 整包拉黑 | PASS |
| 支付宝 | 整包拉黑 | PASS |
| 政务 App（tax.gov） | 整包拉黑 | PASS |
| com.android.systemui | 整包拉黑 | PASS |
| 权限弹窗 | 整包拉黑 | PASS |
| 输入法 | 整包拉黑 | PASS |
| 「跳过 5」纯数字倒计时 | 点（本轮新增） | PASS |
| 「跳过广告 3s」 | 点（本轮新增） | PASS |
| 「关闭广告5秒」 | 点（本轮新增） | PASS |
| 「跳过｜」带竖线 | 点（本轮新增） | PASS |
| 「跳过 ›」带箭头 | 点（本轮新增） | PASS |
| 「跳过登录 5」带倒计时 | 不点（先排除危险词） | PASS |
| 「跳过注册领5元」 | 不点 | PASS |

### L2 时序靶场（7 条，全部通过）

同窗点一次即进冷却 · 冷却结束可再点 · 到单窗上限停手 · 换界面计数清零可重点 · 超 3 秒工作窗口不再动作 · 两条事件 50ms 内第二条被节流 · 命中分数 ≥0.8 · 规则集合为空时不动作。

### L3 存储靶场（6 条，全部通过）

用户规则存取往返（含中文、含 `|`）· 空文字不生成规则 · 脏数据不炸库 · 命中记录往返 · 按新到旧读取且截断到上限 · 坏行跳过不抛异常。

**合计 `26 tests, 0 failures`**（`app/build/test-results/jvmTest/`）。

### L4 APK 静态靶场（22 项，全部通过）

包名/版本/启动 Activity 正确 · minSdk 29 · **无 INTERNET 权限** · 无 QUERY_ALL_PACKAGES · 无外部存储权限 · 无障碍服务声明 + BIND_ACCESSIBILITY_SERVICE + meta-data · 配置文件在资源表里 · canRetrieveWindowContent=true · 监听 0x820（窗口状态 + 内容变化）· flags 0x50（viewId + 交互窗口）· 关键类在 dex 里 · 规则文字没被 R8 抹掉（19 处）· 签名可校验 · 体积 1698KB < 6MB。

### L4 装机靶场（7 项，全部通过）

安装模块即 `pm install` 内置 APK · 写版本标记 `app.ver=4` · 已装且版本一致时开机只查一次 `pm list`、不重复安装 · 标记不一致时自动重装 · 无 `pm` 环境（chroot 形态）静默返回 0 不阻断安装 · 卸载模块时 `pm uninstall` 带走 App · App 确实从系统移除。

### 模块侧回归（未受本轮改动影响）

沙箱 `PASS=72 FAIL=0` · WebUI `PASS=20 FAIL=0` · 覆盖率 94.9%（93/98）、误伤 0、白名单命中 0。

## 三、靶场揪出来的缺陷

1. **视频规则 id 全部撞车**：`id = "video.${pkg.substringAfterLast('.')}"`，`com.qiyi.video` 和 `com.youku.phone` 拿到的都是 `video.video` / `video.phone` 不可区分，多条视频规则的命中日志无法归因。已改为 `video.$pkg` 全名。
2. 测试自身两处错（不是产品问题）：`assertTrue` 参数顺序、窗口超时用例的时间基准写错，已修正。

## 四、怎么复跑

```bash
cd skipper
JAVA_HOME=/home/user/jdk21 GRADLE_USER_HOME=/home/user/.gradle-skipper \
  ./gradlew :app:jvmTest --offline --no-daemon        # L1–L3
JAVA_HOME=/home/user/jdk21 GRADLE_USER_HOME=/home/user/.gradle-skipper \
  ./gradlew :app:assembleRelease --offline --no-daemon

cd ..
bash tests/app-range.sh                               # L4 APK
bash tests/app-install-range.sh                       # L4 装机
unshare -rm -- tests/sandbox/run.sh                   # 模块回归 72
node tests/webui/test.js                              # WebUI 20
```

## 五、诚实的边界

- **没有真机/模拟器**，所以"界面长什么样、点完有没有真的切走"这两件事没做过运行态验证；本报告只覆盖判定逻辑、状态机、存储、安装包结构、装机链路。
- 真机上仍需人工确认 6 件事：
  1. 设置里能把「姚广孝 · 跳过」的无障碍打开，且首页胶囊变「运行中」；
  2. 打开一个带开屏广告的 App，看到它自动点掉；
  3. 点完不是停在广告页，而是进入内容页；
  4. 进系统设置、支付页，确认它一动不动；
  5. 记录页能看到刚才那一条；
  6. 杀掉进程再打开，记录仍在（落盘生效）。

## 七、专项规则包（本轮新增）

来源只取**有明确许可证**的公开 GKD 订阅：

| 来源 | 许可 | 收什么 |
|---|---|---|
| `gkd-kit/subscription` | MIT | 「开屏广告」类的文字、控件 id |
| `Adpro-Team/GKD_subscription` | SATA-2.0（需 star + 致谢） | 同上 |

- 扫了 276 个开屏规则组，跳过 58 个金融/政务/系统类包，最终 **64 个 App**；
- 文字正则 65 条、控件 id 正则 19 条，随 APK 放 `assets/rules/imported.tsv`（1.2KB 级）；
- 未收录 AIsouler 订阅：无许可证声明，默认保留所有权利，不碰。

专项包靶场 5 项（`ImportedRange`）：

1. 能解析、规模在 40–400 之间、id 不重复；
2. 不含任何黑名单包；
3. 无正则能点「跳过登录 / 跳过支付 / 跳过注册领券」；
4. 用 id 也能点中开屏跳过；
5. 无 hosts / block / 屏蔽类语义。

APK 静态靶场同步加 4 项：专项包在 assets 里、规模合理、无危险语义、无屏蔽语义。

## 八、主题、观测与域名学习器（本轮新增）

### 主题

- 设置项由「深色开关」换成三态 `themeMode`：跟随系统 / 浅色 / 深色，点一行循环切换；
- `MainActivity` 在 `themeMode=0` 时读 `isSystemInDarkTheme()`，系统切换即时跟随。

### 观测

- 记录页顶部新增「观测」卡：总次数、覆盖 App 数、今日次数、Top5 App；
- 「复制数据」导出 TSV（time/pkg/rule/label/score），可直接贴回来当规则原料；
- 只在本机，不联网、不上传。

### 域名学习器

链路：`adwatch`（12s 看前台）→ `adlearn`（`pm path` + `unzip -p classes*.dex` + 域名正则 + 品牌表）→ `learn.cand` → `learned.txt` → 可选 `ygb apply`。

四道闸：

1. 品牌表（pangle/umeng/gdt/ksad…），不做关键词模糊匹配；
2. 主域保护：`qq.com`、`baidu.com` 这类绝不一刀切；
3. 白名单（含 `*.foo.com` 子树）；
4. 阈值：同域名出现 ≥2 次或被 ≥2 个 App 用到才确认；默认只收集，`state/learn_auto` 存在才写 hosts。

学习器靶场 16 项（`tests/adlearn-range.sh`）：候选落盘、子域收录、主域不收、白名单不收、非广告域不收、一次不确认、默认不 apply、二次确认、多 App 确认、开关后 apply、白名单不被学习器污染、无 `unzip` 时优雅退出。

模块靶场新增 9 项（第 15 节）：learned 进 hosts、写 IPv6、learned 也受白名单、learn-list、learn-auto-on/off、learn-clear、learn-clear 不动用户规则。

**顺手修掉一个真 BUG**：`build_hosts` 是原地覆盖，但部分 ROM/命名空间下已挂载的 hosts 读不到新内容（新学的域名要等重启才生效）。现在生成后若已有挂载，会重建一次 bind（`refresh_mount`），并验证 inode 一致、内容即时可见。

## 八、主题跟随系统 / 观测 / 域名学习（本轮新增）

### 主题

设置项从布尔 `darkTheme` 换成三态 `themeMode`：0 跟随系统、1 浅色、2 深色。
`MainActivity` 用 `isSystemInDarkTheme()` 实时取值，系统切夜间模式，App 跟着变，不用重启。

### 观测

记录页顶部加「观测」卡：总次数、覆盖 App 数、今日次数、Top5 包名，右上「复制数据」导出 TSV
（time / pkg / rule / label / score）。数据只在本机，不上传。

### 域名学习器（`bin/adlearn` + `bin/adwatch`）

- `adwatch` 每 12 秒看一次前台是谁，换新 App 就 `adlearn` 扫它的 APK；
- `adlearn` 用 `unzip -p classes*.dex` 取字符串，按**品牌表**捞广告 SDK 域名，主域/白名单/至少两级三道闸；
- 候选落 `state/learn.cand`，出现 ≥2 次或被 ≥2 个 App 用到才进 `lists/learned.txt`；
- **默认只收集不生效**，`ygb learn-auto-on` 才并入 hosts；
- `ygb learn-list / learn-clear / learn / learn-auto-on|off` 全套命令。

学习器靶场 16 项：域名捞取、品牌子域、主域保护、白名单、非广告域排除、阈值确认、跨 App 确认、
自动开关、无 unzip 优雅退出。

### 靶场顺手抓到一个真 BUG

域名单测一开始全红：`build/hosts` 已经写进去了，`/system/etc/hosts` 读出来还是旧的
（行数 31238 vs 31240，inode 也不同）。根因是某些命名空间/ROM 下，对已 bind mount 的文件做原地覆盖，
新内容不会回传到挂载目标。修法是 `build_hosts` 末尾加 `refresh_mount()`：检测到挂载就重绑一次。
修完单测转绿，真机上「刚学的规则立刻生效」才有保证。

## 九、从 GitHub 在线更新规则（本轮新增）

`ygb update`：拉 `updates/manifest.txt` → 比对 version → 逐文件下载 → sha256 + 有效行数双重校验
→ 备份到 `state/backup/<版本>/` → `.new` 原子替换 → 重建 hosts → 立刻生效。

只替换 `base/boost/white` 三个文件；`user.txt`、`learned.txt`、`state/*` 永不被覆盖。
更新源可改（`ygb update-url`），只接受 https。

模块靶场新增 20 项：

| 用例 | 期望 |
|---|---|
| 同版本 | 不重复下载 |
| 新版本 | 更新成功、版本落盘、规则替换、hosts 生效、留备份 |
| 用户规则 / 学习结果 | 原样保留 |
| sha256 被篡改 | 拒绝，版本与规则都不变 |
| 行数对不上 | 拒绝 |
| `update-url http://…` | 拒绝 |
| 拉不到清单 / 没有 curl | 明确报错，不破坏现有规则 |

合计本轮靶场：APK 26 / 装机 7 / 学习器 16 / WebUI 20 / 模块 101 = **154 项，0 失败**。

## 十、酷安误点「关闭」事故复盘

**现象**：用户点开酷安想发图文，编辑页被反复自动点「关闭」，发布页打不开。日志里
`gkd.com.coolapk.market / label=关闭 / 0.9588` 30 秒内 6 次。

**根因**（两条叠加）：

1. 专项包导入器把 GKD 选择器里的裸 `text="关闭"` 当成了跳过按钮（`关闭` 是普通弹窗/编辑器
   的关闭键，不是开屏跳过）；
2. `event.className` 在部分 ROM 上会突然变 null，`SkipSession` 把 null 当成新窗口，
   计数与冷却被清零，于是同一屏被反复点。

**修法**：

- 导入器：裸「关闭 / 取消」永不收录，只收「跳过」或「关闭广告」语义；专项包从 64 个 App 收到 58 个；
- 引擎：`Scoring` 增加通用禁用词 `关闭/取消/返回/close/cancel/back`，**任何规则**（含用户自建）都不许命中；
- 会话：`activityClass` 为空时沿用旧窗口，不再重置上限/冷却；
- 回归：新增「专项包绝不点裸关闭」「class 变 null 不重置上限」等 3 个用例。

## 十一、零误伤加固：启动窗口 + 永不跳过名单（本轮）

酷安事故的根子不是阈值调低了，而是「时间窗口和界面窗口」不是一回事。这轮把安全做成结构性的，不再靠调参。

### 1. 启动窗口语义重写（`SkipSession`）

旧逻辑按 `event.className`（Activity）判断窗口：换个界面就当新开屏，计数/冷却清零 —— 于是同一屏能被反复点 6 次。

新逻辑只认一件事：**包名变了才算重新进入前台**。

- 同 App 内切 Activity（打开编辑器、进设置、发图文）→ 沿用旧窗口，不重置计数。
- 换 App → 开新窗口。
- 逛久了（超过 windowMillis）再看到「跳过」→ 不点。

编辑部、发布页、支付页这些「开屏 3 秒之后」的深层页面，天然没有可点的窗口，被结构性排除，而不是靠分数拦。

### 2. 「永不跳过」名单（`deniedPackages`）

给用户一个亲手钉死某个 App 的开关，优先级高于一切规则：

- `EngineConfig.deniedPackages` + `Safety.isDenied(pkg, denied)`（精确命中或 `.` 前缀命中）。
- `SkipEngine` 在 `Safety.isBlocked` 之后立刻判它，命中即 `BLOCKED_PACKAGE` —— 通用包、专项包、用户自定义规则一律不生效。
- 规则页新增「永不跳过」卡片：列表可点「移除」，「+ 添加」弹窗只输包名；设置存在 `SharedPreferences("skipper")` 的 `deny_packages`。

### 3. 本轮回归

| 靶场 | 结果 |
| --- | --- |
| JVM 单测 | PASS 37 / 0（新增「用户钉死的 App 再像跳过也不点」） |
| APK 静态 | PASS 26 / 0 |
| 装机 | PASS 7 / 0 |
| 学习器 | PASS 16 / 0 |
| WebUI | PASS 20 / 0 |
| 模块 | PASS 101 / 0 |

版本：App `0.2.6-m7`（versionCode 9），模块 `v1.7.8`（versionCode 10078，APP_VER=9）。

### 4. 边界

- 「永不跳过」只保证「不点」，不保证「广告不出现」，也不影响 hosts 拦截。
- 包名输入是纯文本，不做在线校验；写错了只会变成一条无效条目，不会误伤别的 App。
- 启动窗口只解决「同 App 深层页面被反复点」，不解决「开屏页按钮文字本身就像跳过」—— 那类仍需禁用词和几何 gate 兜。

## 十二、专项规则扩容：4 个开源订阅源合并（本轮）

按用户给的订阅清单逐条查许可，能收的收，不能收的写清原因。

### 收录（4 源）

| 来源 | 许可 | 说明 |
| --- | --- | --- |
| `gkd-kit/subscription` | MIT | 官方订阅，主力源 |
| `Adpro-Team/GKD_subscription` | SATA-2.0 | 需 star + 致谢，已致谢 |
| `ganlinte/gkd-subscription` | MIT | 甘霖订阅 |
| `MengNianxiaoyao/gkd-subscription` | MIT | 梦念逍遥，npm 镜像 |

结果：**82 个 App**（原 58），文字正则 76 条、控件 id 正则 29 条；扫到开屏规则组 470 个，跳过 115 个金融/政务/系统类包。

### 不收录（写清原因，不装作没看见）

- **AIsouler / JamisonLeo / kunjinkao（114514）**：仓库没有 LICENSE，默认「保留所有权利」，不碰。
- **aoguai**：GPL-3.0，本仓库未声明许可，数据一旦并入就要求整包 GPL，不做。
- **YaChengMu**：改版 MIT 附带「禁止公开推广」等额外条款，且 `dist/gkd.json5` 地址 404；宁可少收。

### 只借什么

仍只提炼两样：哪些包有开屏跳过按钮、那按钮写什么字 / 叫什么 id。层级、颜色、坐标、执行流程一概不用，也不做屏蔽和拦截。

### 零误伤约束是否被稀释

没有。新增的 24 个 App 全部走同一套门：

1. 只在**包名变化后的 3 秒启动窗口**内可点；
2. 裸「关闭 / 取消 / 返回」永不收录，`Scoring.genericLabels` 二次拦截；
3. 几何 gate（顶部 50%、宽 46%、高 14%）照旧；
4. 「永不跳过」名单优先级最高，命中即 `BLOCKED_PACKAGE`。

### 回归

JVM 37、APK 静态 26、装机 7、学习器 16、WebUI 20、模块 101 —— 全绿。

版本：App `0.2.7-m8`（versionCode 10），模块 `v1.7.9`（10079，APP_VER=10）。

## 十三、Lin-arm 订阅（本地自用版）

用户追加的 `Lin-arm/GKD_subscription`（Fork 自 AIsouler，溯源到官方 MIT 源）：

- **没有 LICENSE 文件**，README 写明「仅供学习交流」「禁止在国内平台传播」。
- 处理方式：**只并进本地自用包，不进 GitHub 公开 Release**。导入器加了 `--include-unlicensed` 开关，公开构建默认排除它。
- 结果：专项包 82 → **125 个 App**（文字正则 112、id 正则 54）；开屏规则组 638 个，跳过 166 个金融/政务/系统类包。
- 全量审计：含「关闭」字样的只剩 3 条，全是「关闭广告」开屏文案，且会被 `Scoring.genericLabels` 直接拦死，不会点。

回归：JVM 37、APK 静态 26、装机 7、WebUI 20、模块 101 —— 全绿。本地版 App `0.2.8-m9`（versionCode 11），模块 `v1.7.10`（10080，APP_VER=11）。

## 十四、Lin-arm 独立配置文件（源码内）

按用户要求，把 Lin-arm 源从「合并进 imported.tsv」改成**源码树里单独一份配置文件**：

- 新文件 `skipper/app/src/androidMain/assets/rules/linarm.tsv`，与 `imported.tsv` 完全同格式，头部注明来源与许可状态。
- 生成器支持 `--include-unlicensed`：公开包（82 App）与 Lin-arm 包（43 App，已剔除与公开包重复的条目）分别落到两个文件，互不污染。
- 读取端 `SkipperApp.importedRules` 依次读两个文件，单个文件缺失/写坏只丢那一份，不影响启动。
- 想整包撤掉：删文件、重打包即可，不需要改代码。

审计：Lin-arm 43 个 App 里含「关闭」字样的仅 2 条且都是「关闭广告」，会被 `Scoring.genericLabels` 直接拦死。

回归：JVM 37、APK 静态 26、装机 7、WebUI 20、模块 101 —— 全绿。App `0.2.9-m10`（versionCode 12），模块 `v1.7.11`（10081，APP_VER=12）。

## 十五、使用过程中的「发现」：补规则 + 补 hosts（本轮）

这一轮补的是「用着用着，自己长出规则」的两端。

### 1. App 侧：发现「像跳过但没规则」的按钮

新增 `engine/Advisor.kt` 侦察兵，只在引擎判定 `NO_NODE / LOW_SCORE` 且仍在启动窗口内时工作：

- 文字必须是明确的跳过语义（跳过 / 跳过 3s / 关闭广告 / Skip），裸「关闭 / 取消」不碰；
- 危险词（登录、支付、注册、授权、下载、确认…）直接丢弃；
- 几何门：顶部 55%、宽 60%、高 20%、可点；
- 命中的包已被「永不跳过」钉死时，提都不提。

结果写进 `data/SuggestStore.kt`（`suggest.log`，按包名+文字去重，最多 60 条）。规则页新增「发现」卡：
每行 `「跳过」com.xxx · 见过 N 次`，可以点「＋ 规则」一键变成用户规则（写进 `user_rules_v2`），或「忽略」。

**它只建议，从不自己点。** 加不加规则，还是用户那一下。

### 2. 模块侧：WebUI 把学习候选摆出来

`ygb ui-status` 增加 `learned / cand / learnAuto` 三个字段；新增命令：

- `ygb learn-cand` — 候选域名 + 被几个 App 用到，按次数排序取前 15；
- `ygb learn-ignore <域名>` — 从候选里删掉并记进 `state/learn.rej`，`adlearn` 以后不再提。

WebUI 新增「自动学习 · 可优化的 hosts」卡：显示已确认条数 / 候选数 / 是否自动写 hosts，候选列表每行可「＋ 加入自定义规则」或「× 忽略」，右上开关直接切 `learn-auto-on/off`（开启时顺带 apply 一次）。

### 3. 回归

| 靶场 | 结果 |
| --- | --- |
| JVM | PASS 44 / 0（新增 Advisor 7 条） |
| APK 静态 | PASS 26 / 0 |
| 装机 | PASS 7 / 0 |
| 学习器 | PASS 16 / 0 |
| WebUI | PASS 22 / 0（新增学习卡 2 条） |
| 模块 | PASS 109 / 0（新增学习候选 8 条） |

版本：App `0.3.0-m11`（versionCode 13），模块 `v1.8.0`（10082，APP_VER=13）。

## 十六、自动学习发烫：把「随时扫」改成「有节制地扫」（本轮）

现场表现：一开自动学习，手机明显发热。不是玄学，是三件事叠在一起：

1. `adwatch` 每 12 秒 `dumpsys activity activities` 一次，屏幕灭了也照跑；
2. 换 App 就 `unzip -p classes*.dex | grep` 整包 dex，大包几百 MB，CPU 直接拉满；
3. 开了 `learn_auto` 后，每扫到一个新 App 就 `ygb apply` 一次——重建 15615 条 hosts + 重新挂载，I/O 反复。

四项改造：

- **熄屏不干活**：每轮先看 `mWakefulness=Awake`，屏幕灭就跳过；
- **扫描间隔 ≥120 秒**：看前台 30 秒一次，但真正读 dex 至少隔 2 分钟；
- **超大包直接跳过**：APK > 150MB 记进 `seen` 不再重扫（微信这类包本来也不该反复啃）；
- **应用限流**：`learn_auto` 下每 1800 秒最多重建一次 hosts，而且只有确认条数变化才重建；
- 扫描进程降优先级（`nice -n 19`），不跟前台抢 CPU。

新增靶场 4 条：超大包跳过、大包记 seen、首次自动应用、限流期内不重复 apply。学习器 16 → 20。

版本：模块 `v1.8.1`（10083，APP_VER 仍 13，App 无改动）。
