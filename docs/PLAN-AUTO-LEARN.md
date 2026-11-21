# 自动学习广告：计划与边界

## 目标

1. 界面浅色/深色跟随系统（手动也能钉住）。
2. 用 App 时，自动记录「点掉了哪个 App 的广告」。
3. 用 App 时，自动从 App 的 APK 里学广告域名，作为 hosts 规则的增量来源。

## 分成四步，每步都能单独验证

| 阶段 | 做什么 | 产物 | 能不能回退 |
|---|---|---|---|
| P1 主题 | `themeMode` 三态：跟随系统 / 浅色 / 深色 | 设置页一行循环切换 | 是，切回系统 |
| P2 观测 | 命中记录按包聚合，一键复制 TSV | 记录页顶部「观测」卡 | 是，清空即可 |
| P3 域名学习 | 前台换 App → 扫它 APK 里的广告 SDK 域名 → 候选 → 确认 | `state/learn.cand`、`lists/learned.txt` | 是，`ygb learn-clear` |
| P4 生效 | 确认域名并入 hosts 生成，默认**只收集不生效** | `ygb learn-auto-on` 才自动应用 | 是，随时 `learn-auto-off` |

## 域名学习器怎么工作

```
adwatch（shell 循环，12s）
   └─ 看前台是谁（dumpsys activity）
        └─ 没扫过 → adlearn <包名>
             ├─ pm path 拿 APK
             ├─ unzip -p classes*.dex | grep 域名
             ├─ 品牌表过滤（pangle / umeng / gdt / ksad …）
             ├─ 主域保护（qq.com、baidu.com 绝不写）
             ├─ 白名单过滤（*.foo.com 放行整棵子树）
             └─ 记入 learn.cand
                  └─ 出现 ≥2 次，或被 ≥2 个 App 用到 → learned.txt
                       └─ learn_auto 存在 → ygb apply
```

**没有任何抓包、代理、VPN、DNS 劫持。** 域名来自 APK 里本来就写着的 SDK 字符串，是静态读取。

## 为什么默认不自动生效

「帮用户点跳过」和「替用户屏蔽广告」在法律上不是一回事。拦精灵案里法院讲得很直白：
功能应仅限于替用户点击「跳过」，直接屏蔽广告不具有正当性。
所以：

- 学习器默认只**收集证据**（`learn.cand` → `learned.txt`），不动 hosts；
- 要真正写 hosts，得手动 `ygb learn-auto-on`，这是用户自己的选择；
- 白名单、主域保护、品牌白名单三道闸，减少误伤；
- 模块总开关关闭时，学习器也不写 hosts。

## 已知限制

- 设备上必须有 `unzip` 或 `busybox` 才能读 dex；没有就优雅退出，写一行日志，不产生垃圾。
- 只认品牌表里的 SDK；小众 SDK 会漏，漏了不影响跳过功能。
- 一个 App 只扫一次（按包名记 seen）；App 升级后想重扫，清掉 `state/learn.seen`。


## 落地状态（v1.8.0 / 0.3.0-m11）

- P1 主题：已完成，`themeMode` 三态。
- P2 观测：已完成，记录页「观测」卡按包聚合。
- P3 域名学习：已完成，`adwatch` 开机随 `service.sh` 起，`adlearn` 静态读 dex；候选进 `state/learn.cand`，确认进 `lists/learned.txt`。
- P4 生效：已完成但默认关闭；`learn-auto-on` 才自动写 hosts。
- 新增 App 侧「发现」：`Advisor` 在启动窗口内挑出「像跳过却没规则」的按钮，进规则页「发现」卡，一键补用户规则。
- 新增 WebUI「自动学习」卡：候选域名可加入/忽略，自动写 hosts 开关直接可见。
