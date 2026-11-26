# 姚广孝 BOSS

> 去广告 · 跳广告 · 杀掉一切广告
> 基于 KernelSU 4.2.0 的 hosts 规则拦截模块，仅供学习参考。

---

> **发布政策**：本仓库只更新源码，不发布任何 APK 或内置 APK 的安装包。
> 需要 App 请自行从源码构建；仓库与 Release 页面不会提供可下载的安装包。

## APK v0.3.1-m11.1（2026-11-27）

诊断版 APK 源码已更新：补 `isAccessibilityTool=true`；状态事件不再被 70ms 内容节流吞掉；修复 `SnapshotMapper` 空子节点丢兄弟；工作窗口默认 5 秒/上限 8 秒；首页新增运行诊断卡（事件数、最后包名、节点数、未命中原因、根节点为空/节流次数）。JVM 靶场 45/45、APK 冒烟 11/11。详见 `docs/SKIPPER-M11.1-SMOKE.md`。

## 模块 v1.8.2.1（2026-11-27）

只换内置 APK：`0.3.1-m11.1` 诊断版，模块逻辑保持 v1.8.2 P0。`APP_VER=14`，升级时覆盖旧 m11。

## v1.8.2（2026-11-26）

自动学习从模块侧 `adwatch` 常驻扫描迁到 App 侧三级探针：界面 SDK 类名探针（L0）+ 清单组件探针（L1）+ 充电/熄屏/空闲时的延迟 dex 扫描（L2）。模块不再解包 APK，只导入 App 产出的 `learned.tsv`。

- WebUI 拆成「智能探针 / 深度补扫 / 自动写入 hosts」三个独立开关；旧 `learn_off` 仍兼容。
- 旧 `adwatch` 默认停用，升级时自动 kill 并清 pid；`service.sh` 不再启动它。
- 新增 `ygb import-learn`：保护域、白名单、无点域名、通配、重复全部过滤，导入幂等。
- 开机守护只查 `ygb mounted`，不再每 3 秒跑完整 `status`。
- 首次开机 `rules.ver` 种子修复，不再误拉全量规则。

> 本版只出模块源码；App 学习引擎在 m12 接入。

## 一、它做了什么

用 hosts 规则在内核层把广告域名直接"指到黑洞"：App 发起请求 → 系统解析域名 → 命中规则 → 请求根本发不出去。

因为拦截发生在 **请求层**，所以不挑 App、不需要 Xposed、不注入进程，安装即生效：

- 开屏广告、插屏、Banner 的广告图文请求（广点通 / 穿山甲 / 优量汇 / Google AdMob 等）
- 广告 SDK 的曝光、点击、归因上报
- MIUI / 华为 / OPPO / vivo 系统级推广接口
- 新闻、浏览器、工具类 App 的第三方广告联盟请求

## 二、规则清单一览（精选 251 + 增强 15364 = 15615 条生效域名）

| 分类 | 条数 | 说明 |
| --- | ---: | --- |
| 1. 国际广告 / 归因 / 统计 | 24 | AdMob、DoubleClick、GA、Facebook/TikTok 统计 |
| 2. 国际广告交易平台 SSP/DSP | 47 | AppNexus、Criteo、Taboola、Rubicon 等 |
| 3. 国际移动广告 SDK | 29 | AppLovin、Unity Ads、ironSource、Vungle 等 |
| 4. 字节跳动 · 穿山甲 / 巨量 | 12 | pangolin / pangle / gromore 全链路 |
| 5. 腾讯 · 广点通 / 优量汇 | 8 | 含素材 CDN `ugdtimg.com` |
| 6. 阿里 · 阿里妈妈 / 友盟+ | 12 | adash、mmstat、umeng、cnzz |
| 7. 百度 · 移动联盟 | 8 | mobads、pos、cpro、hm |
| 8. 快手 · 磁力金牛 | 4 | ad / union / adkwai |
| 9. 手机厂商广告 | 15 | 小米 MIUI、华为、OPPO、vivo、三星 |
| 10. 国内第三方广告 / 统计 SDK | 20 | 新浪、网易、知乎、微博、360、WPS 等 |
| 11. 开屏 / 插屏聚合复用域名 | 9 | 被大量 App 复用的聚合入口 |
| 12. 并入的广告 SDK 主干端点 | 64 | 来自 SM 2.9 精选：穿山甲 `pglstatp`、快手 `adkwai`、秒针、多盟、voiceads、tradplus 等 |
| **合计** | **251** | 原始 188 行 + 并入 64 条（重复项自动去重） |

另有 **9 条高风险条目**（`graph.facebook.com`、`app.adjust.com`、`branch.io`、`api.getui.com`、`api.jpush.cn` 等）默认注释，可能影响登录/推送/归因，按需取消注释启用。

> 每条域名同时写 `0.0.0.0` 与 `::1`，实际 hosts 为 15615 条域名 / 31238 行（含注释与 localhost 两行）。

### 增强规则包 `lists/boost.txt`（15364 条）

v1.1.0 起接入公开广告清单，补齐 200 个头部 App 背后长尾广告联盟：

| 来源 | 条数 | 说明 |
| --- | ---: | --- |
| anti-AD 广告/追踪域名清单 | 5015 | `github.com/privacy-protection-tools/anti-AD`，2026-11-17 快照 |
| AdGuard Chinese Filter · adservers | 128 | `github.com/AdguardTeam/AdguardFilters`，中文第三方广告联盟 |
| SM 2.9 长尾规则（已过滤误伤项） | 4048 | 并入增强包，可随强力模式整体关闭 |
| Cats-Team AdRules（国内广告联盟） | 1014 | `github.com/Cats-Team/AdRules`，2026-11-20 快照 |
| AdAway 官方 hosts | 5164 | `adaway.org/hosts.txt` |
| 广告 SDK 直补（手工核验） | 5 | 广点通素材、阿里妈妈、秒针、TradPlus 等主干端点 |
| **合计（已剔除与精选重复）** | **15364** | hosts 无通配，全部为独立域名 |

> 已校验：两份清单均未包含 `qq.com` / `weixin.qq.com` / `alipay.com` / `taobao.com` / `xiaomi.com` / `umeng.com` / `getui.com` 等登录、支付、推送类关键域名，不存在整域误杀。

## 三、目录结构（KSU 4.x 标准模块）

```
姚广孝BOSS/
├── module.prop              # 模块信息（id / name / version / description）
├── post-fs-data.sh          # 开机最早的阶段：生成规则 + bind mount
├── service.sh               # 开机后期守护 90s，防止 ROM 重挂 /system 导致失效
├── action.sh                # 管理器里「执行」按钮的输出
├── uninstall.sh             # 卸载时解除挂载，恢复系统 hosts
├── bin/ygb                  # 核心控制脚本（CLI + WebUI 后端）
├── lists/
│   ├── base.txt             # 精选规则 251 条（按广告联盟分类）
│   ├── boost.txt            # 增强规则 15364 条（anti-AD + AdGuard 中文 + SM 长尾 + AdAway）
│   ├── user.txt             # 自定义规则（WebUI 写入）
│   └── white.txt            # 白名单（放行）
├── build/hosts              # 运行时生成的最终 hosts
├── webroot/                 # KSU WebUI 控制面板
│   ├── index.html
│   ├── style.css
│   └── app.js
└── META-INF/                # 通用刷机包结构（兼容 Magisk/APatch 安装器）
```

## 四、安装

1. 手机已 root 且为 **KernelSU 4.2.0**（KernelSU / KernelSU Next 均可）。
2. 打开 KSU 管理器 → 模块 → 从本地安装 → 选择 `姚广孝BOSS_v1.0.0.zip`。
3. 重启（首次需要，之后改规则无需重启）。
4. 重启后在模块卡片点开 **WebUI** 即可管理。

> 手动安装也可：把 `姚广孝BOSS/` 目录整体推到 `/data/adb/modules/yaoguangxiao_boss/`，重启生效。

## 五、WebUI 能干什么

| 功能 | 说明 |
| --- | --- |
| 总开关 | 一键开启 / 关闭。**关闭 = 停止全部规则 + 还原系统 hosts**，任何动作都不会偷偷挂回 |
| 强力模式 | 增强规则包单独开关：开启=15615 条全量；关闭后 15364 条增强规则立即从生效清单中移除，仅留 251 条精选，hosts 当场重建并重新挂载，无需重启 |
| 数据面板 | 生效域名数、内置规则数、增强规则数、自定义数、白名单数、挂载状态 |
| 立即重载规则 | 改完规则立即 rebuild + remount，不用重启 |
| 添加自定义规则 | `ad.example.com`；`*.example.com` 实际只拦主域（hosts 无通配能力） |
| 白名单放行 | 被误伤时救场，支持 `*.example.com` 后缀通配 |
| 查看日志 | 看最近挂载、生成结果 |
| 恢复系统 hosts | 立即 umount，系统立刻回到原始 hosts |

## 六、命令行（高级玩法）

```sh
su -c 'sh /data/adb/modules/yaoguangxiao_boss/bin/ygb status'
su -c 'sh .../bin/ygb add ad.example.com'
su -c 'sh .../bin/ygb white-add pay.example.com'
su -c 'sh .../bin/ygb off'    # 暂停
su -c 'sh .../bin/ygb on'     # 恢复
su -c 'sh .../bin/ygb apply'  # 重载
```

## 七、原理与关键代码

核心就两步：**生成 hosts** + **bind mount 覆盖系统 hosts**。

```sh
# post-fs-data.sh
sh $MODDIR/bin/ygb build             # 合并 base+user，剔除 white，生成 build/hosts
sh $MODDIR/bin/ygb mount             # mount -o bind build/hosts /system/etc/hosts
```

`ygb` 里的挂载逻辑（带重试，规避部分 ROM 的时序问题）：

```sh
mount -o bind "$BUILD_FILE" /system/etc/hosts
```

> v1.2.1 修复：规则文件改为**原地覆盖**（不替换 inode），并在每次切换后强制 `umount` 再 `mount`。否则 bind mount 仍指向旧文件，关闭强力模式会看起来"没生效"。

生成规则时同时写入 IPv4 与 IPv6 两条：

```
0.0.0.0 ad.example.com
::1     ad.example.com
```

只写 IPv4 的话，IPv6 网络下 App 仍可能通过 AAAA 查询拿到真实地址，所以两条都要。

## 八、边界与已知限制（重要，别被"杀掉一切广告"骗了）

1. **DoH / DoT 会绕过 hosts**。Chrome、部分 App 走加密 DNS，不走系统 resolver，hosts 拦不住。可在设置里关掉浏览器的安全 DNS。
2. **写死 IP 的广告请求拦不住**。少数 SDK 直接用 IP 请求，不走域名解析。
3. **已缓存的广告**会照常显示一次，下次才生效。
4. **"跳广告" ≠ 跳过等待**。hosts 是让广告加载失败，多数 App 因此直接进主界面；但部分 App 在广告请求失败后会显示"加载失败"占位或延长等待，这类必须靠 Hook（LSPosed）处理，hosts 做不到 100%。
5. 个别 App 把**内容接口**和**广告接口**放同一域名，会被误伤 → 加白名单解决。
6. **hosts 天生不支持通配符**。`0.0.0.0 *.foo.com` 不会被系统识别，所以本模块会把 `*.foo.com` 落成 `foo.com`；要拦子域必须逐个写明（白名单里反过来支持后缀通配，因为是模块自己算的）。

## 九、免责

本项目仅用于学习 Android 模块机制与 DNS/hosts 拦截原理，请勿用于任何商业用途或绕过平台正当商业模式。使用产生的任何后果由使用者自行承担。

## 十、WebUI 连不上 / 一直"读取中"怎么办

v1.3.0 起面板自带诊断条：连接失败会直接显示 `window.ksu` 是否存在、可用方法列表、尝试链和页面地址，并支持"重新连接"。常见原因与排查：

1. **管理器不支持 WebUI**：部分 KernelSU 分支需要自己实现 `webroot`，若 `window.ksu` 为 undefined，面板只能当只读页面，请在终端用命令管理。
2. **执行环境 PATH 极简**：v1.3.0 已在 `ygb` 内显式补 `/system/bin`，并将调用改为绝对路径 `/system/bin/sh`，该问题已修。
3. **模块目录不同**：面板会自动在 `/data/adb/modules/yaoguangxiao_boss` 与 `/data/adb/modules_update/yaoguangxiao_boss` 之间探测。

终端验证命令：

```sh
ls -l /data/adb/modules/yaoguangxiao_boss/bin/ygb
/data/adb/modules/yaoguangxiao_boss/bin/ygb status
/data/adb/modules/yaoguangxiao_boss/bin/ygb ui-status
```

能正常输出 JSON 说明模块侧没问题，问题在 WebUI 桥接层；把面板里的「复制诊断信息」内容贴给开发者即可定位。

---

**姚广孝 BOSS** · 一身黑衣，专干脏活。广告，一个都别想跑。

## 十一、自测靶场（v1.5.0）

模块自带两套可复现的自测，源码外挂在项目的 `tests/` 目录，不进入刷机包。

**① 功能靶场**（`tests/sandbox/`）：用 Linux user namespace + chroot 造一个假安卓根（`/system/bin`、`/system/etc`、`/data/adb/modules`），mount 用真实的 `mount(2)` 系统调用，因此 bind mount、inode、umount 全部是真语义，不是打桩。

```sh
./tests/sandbox/run.sh          # 需要 unshare 权限
# 51 项断言：首启、状态 JSON、强力模式、总开关、暂停态、自定义规则、白名单、通配、
# 脏数据、零规则、自愈、日志裁剪、并发、守护脚本、权限、卸载还原
```

**② WebUI 靶场**（`tests/webui/test.js`）：Node + 假 DOM + 假 `ksu` 桥，验证回调式/Promise 式桥接、无桥接降级、开关命令下发、脏数据提示。

```sh
node tests/webui/test.js        # 13 项断言
```

**本轮抓到并修掉的 BUG（v1.3.1）**

| # | 问题 | 影响 | 修法 |
| --- | --- | --- | --- |
| 1 | 桥接策略缓存把「命令」一起缓存了，后续所有请求都在执行第一次的 `echo YGB_READY` | **面板永远"读取中"的根因** | 只缓存通道种类，每条命令重建执行器 |
| 2 | `count_rules` 用 `grep -c ... || echo 0`，0 命中时输出 `0\n0` | 规则为空时 JSON 非法 | 先取变量再兜底 |
| 3 | 并发 `apply` 共用同一个 `build/hosts.tmp` | 规则文件被互相冲成空，界面显示 0 条 | 临时文件带 `$$`，构建+挂载同锁 |
| 4 | 挂载重试 8 次，失败后不清层 | 极端情况下叠多层 bind | 已挂载直接跳过；失败先 umount 再重试 |
| 5 | 暂停后 `apply` / 加规则 / `service.sh` 仍会 mount | **暂停了又被偷偷挂回** | `mount_hosts` 统一拦截 `state/off`；守护循环每轮复查 |
| 6 | `service.sh` 90 秒守护期间不感知暂停 | 用户刚关掉又被重新挂上 | 循环内每轮检查 flag |
| 7 | 手动编辑列表写入 `0.0.0.0 ad.com` 等格式时计数与生效不一致 | 统计虚高 | 计数按合法域名归一化后再统计 |
| 8 | `white-del` 空参数会误删空行 | 脏数据 | 补空值校验 |
| 9 | `post-fs-data.sh` / `service.sh` 用 `[ -f ] && exit 0`，未命中时返回 1 | 管理器可能记为失败 | 改显式 `if` 并 `exit 0` |
| 10 | `uninstall.sh` 只解 `/system/etc/hosts` | system-as-root 机型残留挂载 | 两种布局都解 |

## 十二、SM 规则是怎么并进来的（v1.5.0）

从「SM广告丢那吗 hosts 2.9」（酷安@下个ID見）里取用规则，**没有整包照搬，也没有做第三层开关**：能常开的伞柄进精选，长尾进增强。

| 环节 | 数量 | 处理 |
| --- | --- | --- |
| 原始 `0.0.0.0` 行 | 4937 | 仅取拦截行；作者的 GitHub/FCM 加速行（185.199.x 等）全部丢弃 |
| 作者白名单 | 81 | 全部并入放行名单 |
| 作者自标风险（误伤/副作用/无网/打不开） | 79 | 剔除 |
| 功能关键词（login/pay/push/dns/map…） | 384 | 剔除 |
| 内容 CDN（gtimg/qpic/aliyuncs…） | 67 | 剔除，拦了图片视频全挂 |
| 推送 / 云存储厂商主域 | 143 | 整体放行（见下） |
| **并入精选** | **64** | 广告 SDK 主干端点，永久常开 |
| **并入增强** | **4048** | 长尾，可随强力模式关闭 |

### 推送 / 云存储为什么整体放行

`*.igexin.com`（个推）、`*.jiguang.cn`（极光）、`*.amazonaws.com`（S3 通用对象存储）这三个域在规则里命中 143 条，但拦掉就是真正的事故：个推/极光是推送通道，S3 是大量 App 的图片与配置仓库。宁可漏掉几条广告，也不让消息和图片挂掉。

另外从各包统一清出 13 条功能域：HTTPDNS（`httpdns.music.163.com`，拦了网易云直接没网）、`android.push.126.net` 等 6 条推送、`mhdpay.1391.com` 支付、12306、腾讯视频配置与播放接口、千千音乐播放器。放行名单现为 97 条，靶场有「白名单零命中」断言守着。

### 总开关的语义（重要变化）

以前关闭只是"暂停挂载"，规则文件还在生成；现在 `off` 是一次彻底停机：

1. 立即 umount，`/system/etc/hosts` 逐字节回到系统原版；
2. `state/off` 落盘，重启、开机脚本、守护脚本、手动 mount、加规则、切强力模式，全部不会把 hosts 再挂回去（靶场 9 项断言专门盯这条）；
3. 面板与 CLI 的"生效域名"归零，"内置/增强"只作为规则库条数展示。

## 十三、内置「姚广孝 · 跳过」App

模块里带了一个 1.7 MB 的开屏跳过 App（`apk/yaoguangxiao-skip.apk`），**装模块就顺手装上**，不用再单独找 APK：

- 安装模块时由 `customize.sh` 直接 `pm install`；
- 开机时 `service.sh` 再检查一次（没装上、或被卸载、或版本旧了都会重装），版本号记在 `state/app.ver`；
- 卸载模块时 `uninstall.sh` 会把 App 一并卸掉，不留孤儿；
- App 界面是四页联动（首页 / 规则 / 记录 / 设置），Compose + Miuix 重写，设计说明见 `docs/SKIPPER-UI-DESIGN.md`
- App 只有无障碍权限，不联网、不申请应用列表权限。

App 只做一件事：窗口切换后 3 秒内找到「跳过」并点一下；金融、支付、政务、系统界面永不动作。**它不屏蔽广告、不改别人的界面**——屏蔽归 hosts 模块，点击归这个 App，两个产品分开交付、分开说清。

首次使用需要手动开无障碍（系统限制，任何 App 都绕不过）：设置 → 无障碍 → 已下载的应用 → 「姚广孝 · 跳过」。暂停模块拦截（总开关关闭）不影响 App 使用。
