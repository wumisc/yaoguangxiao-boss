# 姚广孝 · 跳过 —— 功能测试报告 / 靶场

日期：2026-11-20　被测版本：`0.2.1-m2`（versionCode 4）　模块：`v1.7.2`
结论：**四层靶场全部通过，0 失败**；本轮靶场还揪出并修掉 1 个真实缺陷。

## 一、靶场怎么设计的

没有真机、没有模拟器，所以把"功能"拆成四层，每层都能在机器上真跑：

| 层 | 测什么 | 怎么造 | 入口 |
|---|---|---|---|
| L1 场景靶场 | 单界面判定：点不点、为什么点 | `scenarios.tsv` 一行 = 一个真实界面 | `./gradlew :app:jvmTest` |
| L2 时序靶场 | 一串事件：冷却、上限、切窗、节流、超时 | 代码驱动 `SkipSession` | 同上 |
| L3 存储靶场 | 规则/记录存取往返、脏数据 | 纯 Kotlin 编解码 | 同上 |
| L4 交付靶场 | APK 能不能装、有无越权、服务声明对不对；模块装机链路 | aapt2 / dexdump / apksigner / 假 pm | `bash tests/app-range.sh`、`bash tests/app-install-range.sh` |

## 二、结果

### L1 场景靶场（22 条场景，1 个用例聚合，全部通过）

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
