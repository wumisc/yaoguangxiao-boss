# 姚广孝 BOSS · 全量 BUG 审查与靶场测试报告

- 模块版本：v1.6.0（versionCode 10060）
- 测试日期：2026-11-17（v1.3.1） / 2026-11-20（v1.6.0：新增 6183 条开源规则，含覆盖率与误伤体检）
- 被测对象：`姚广孝BOSS/`（bin/ygb、post-fs-data.sh、service.sh、action.sh、uninstall.sh、webroot/*）
- 结论：**两套靶场共 92 项断言全部通过**（功能 72 / WebUI 20），期间发现并修复 10 个缺陷，其中 2 个是「面板永远读取中」「暂停后被偷偷挂回」的致命用户态问题。

## 一、靶场怎么搭的

云主机没有 root、不能改真实 `/system`，但支持 `unshare -rm`（user namespace + CAP_SYS_ADMIN）。于是：

1. 用 `unshare -rm` 起一个带 mount 权限的命名空间；
2. `chroot` 进自建假安卓根：`/system/bin`、`/system/etc/hosts`、`/data/adb/modules/yaoguangxiao_boss`；
3. 把宿主 `/usr`、`/bin`、`/lib`、`/etc/alternatives` 绑进去提供工具链；
4. `/dev` 用 tmpfs + mknod 造 null/zero/tty/random（没有 /proc 时 mount/umount 会去读 mtab，所以给 `/system/bin` 里放了走 mount(2)/umount2 系统调用的可观测垫片）；
5. 模块代码原样拷贝进去，**不改一行**，跑的是真实 bind mount 语义。

因此 inode 不变、原地覆盖、umount 后立即恢复系统 hosts 这些行为都是真实验证，不是断言打桩。

## 二、功能靶场结果（72/72 通过）

```
结果: PASS=72 FAIL=0
```

| 组 | 断言内容 |
| --- | --- |
| 1 首启 | post-fs-data 自动生成并挂载；生效域名 5330；hosts 全量 10667 行 |
| 2 状态 | ui-status 输出合法 JSON；rules/mounted/base/boost/boostOn 五项字段正确 |
| 3 强力模式 | 规则文件 inode 不变；关闭后 hosts 立即降到 187 条；boost 计数归零；重开恢复 5330 |
| 4 总开关 | 暂停后 hosts 与系统原样一致；mounted=no；恢复后重新挂载 |
| 4b 暂停态 | 暂停时 apply / 加规则 / 守护脚本都不会把 hosts 挂回来 |
| 5 自定义 | 新增 +1、写入 hosts、重复添加幂等、删除回落 |
| 7b 追加安全 | 列表文件缺结尾换行时仍能正确追加（不拼进注释行） |
| 6 校验 | 空格/中文域名被拒；`https://AD.Test123.COM/path?x=1` 归一化为 `ad.test123.com` |
| 7 白名单 | `*.test.com` 放行子域；移出后生效；`*.wild.com` 落成主域且不写通配字面 |
| 8 脏数据 | CRLF、注释、前后空格、大写全部正确处理 |
| 9 零规则 | 规则清空时 JSON 仍合法且 rules=0（原 BUG 回归点） |
| 10 自愈/日志 | 删掉 build/hosts 自动重建；日志超 200 行自动裁剪 |
| 11 并发 | 两个 apply 并行后仍只有一层挂载、规则完整 |
| 12 守护 | service.sh 常驻不报错、退出后仍挂载 |
| 13 权限 | ygb / post-fs-data.sh / service.sh 均可执行 |
| 14 卸载 | uninstall 后 hosts 还原、无残留挂载 |
| 3b SM 包 | 关 SM→5319；关增强→4442；双关→186；逐级恢复→9575 |
| 13b 误伤体检 | 12 个功能样本域零出现；白名单 ≥85 条 |

## 三、WebUI 靶场结果（20/20 通过）

Node + 假 DOM + 假 ksu：

| 场景 | 断言 |
| --- | --- |
| 回调式桥接 `ksu.exec(cmd, cb)` | 规则数上屏 5330、胶囊「拦截生效中」、诊断条隐藏 |
| Promise 式桥接 | 同上（验证超时后自动降级） |
| 无 `window.ksu` | 诊断条出现、胶囊「连接失败」、详情含 `window.ksu: 不存在` |
| 点击开关 | 总开关下发 `off`、强力模式下发 `boost-off`、状态同步 |
| 脏数据 | 返回非 JSON 时提示「模块状态读取失败」 |

## 四、本轮修复的 BUG

| # | 严重度 | 问题 | 影响 | 修法 |
| --- | --- | --- | --- | --- |
| 1 | 致命 | 桥接策略缓存把命令一起缓存，后续请求全部执行第一次的 `echo YGB_READY` | 面板永远「读取中」/数字全 `--` | 只缓存通道种类，每条命令重建执行器，缓存失败自动回落全链路 |
| 2 | 高 | 并发 apply 共用 `build/hosts.tmp` | 规则文件被冲成空，界面 0 条 | 临时文件带 `$$`，构建+挂载同锁 |
| 3 | 高 | 暂停后 apply / 加规则 / service.sh 仍会 mount | 用户关了又被偷偷挂回 | mount_hosts 统一拦截 `state/off`；守护循环每轮复查 |
| 4 | 中 | `grep -c ... || echo 0` 在 0 命中时输出两行 0 | 空规则时 JSON 非法 | 先取变量再兜底 |
| 5 | 中 | 挂载重试失败后不清层 | 极端情况叠多层 bind | 已挂载直接跳过；失败先 umount |
| 6 | 中 | 手动写入 hosts 格式行导致计数虚高 | 统计与生效不一致 | 计数前统一归一化 + 正则过滤 |
| 7 | 低 | `white-del` 空参数误删空行 | 脏数据 | 补空值校验 |
| 8 | 低 | post-fs-data/service 用 `&& exit 0` | 未命中返回 1，管理器可能记失败 | 改显式 if + exit 0 |
| 9 | 低 | uninstall 只解 `/system/etc/hosts` | system-as-root 机型残留 | 两种布局都解 |
| 10 | 低 | 锁不可重入、崩溃后可能留死锁 | 极端情况卡住 | 深度计数 + pid 存活检测回收 |

## 五、复现方式

```sh
# 功能靶场（需要 unshare 权限）
./tests/sandbox/run.sh

# WebUI 靶场
node tests/webui/test.js
```

## 六、仍需在真机确认的点

- 真机 `/system` 是否可写：KSU 下 bind mount 通常可行，但个别 ROM 只读，模块已有「开机重试 + 90 秒守护」兜底；
- 私有 DNS / DoH 会绕过 hosts，属原理限制，不是 BUG；
- KSU 各分支的 WebUI 桥接差异已由 5 种策略 + 诊断条兜底，真机若仍失败，诊断条会给出具体尝试链。

## 七、v1.4.0：SM 精选包移植与误伤控制

数据来源：用户提供的「SM广告丢那吗 hosts 2.9」（作者 酷安@下个ID見，2026-02-15 版）。

| 环节 | 数量 | 处理 |
| --- | --- | --- |
| 原始 0.0.0.0 行 | 4937 | 仅取拦截行；GitHub/FCM 的 185.199.x 等 IP 重定向全部丢弃 |
| 与既有规则重复 | 150 | 去重 |
| 作者白名单 | 81 | 全部并入放行名单 |
| 作者自标风险（误伤/副作用/无网/打不开…） | 79 | 剔除 |
| 功能关键词（login/pay/push/dns/map…） | 384 | 剔除 |
| 内容 CDN（gtimg/qpic/aliyuncs…） | 67 | 剔除，拦了图片视频全挂 |
| **入库 SM 包** | **4256** | `lists/sm.txt`，独立开关 |

另做一轮功能兜底，把 13 条真会影响功能的域从各包移出并放行：`httpdns.music.163.com`（HTTPDNS，拦了网易云直接没网）、`android.push.126.net` 等 6 条推送、`mhdpay.1391.com`（支付）、`12306media.com`/`m.12306media.com`、`appcfg.v.qq.com`/`api.poll.video.qq.com`（腾讯视频）、`ttplayer.qianqian.com`。

验收：94 条白名单在最终 hosts 中**零命中**；QQ/微信登录、支付宝、京东、地图、GitHub 等 40 个功能样本域**零误伤**。

另修一个隐蔽 BUG：列表文件末尾没有换行时，`echo >>` 会把新规则拼到上一行注释里，导致白名单/自定义规则静默失效。已改为 `append_line()` 自动补齐换行，并加入回归用例。

## 八、v1.5.0：规则并入两包 + 总开关彻底停机

**规则**：SM 规则不再单列成第三层，64 条广告 SDK 主干端点并入精选（251 条），4048 条长尾并入增强（9181 条），全量 9432 条。143 条推送/云厂商域（`*.igexin.com`、`*.jiguang.cn`、`*.amazonaws.com`）整体放行，放行名单 97 条。

**总开关**：`off` 从"暂停挂载"升级为彻底停机 —— umount 后 hosts 与系统原版逐字节一致；`state/off` 落盘后，重启、post-fs-data、service 守护、手动 mount、apply、加规则、改白名单、切强力模式全部拒绝挂载。靶场 9 项断言覆盖每个入口，避免"关了又被偷偷挂回"。

**新增断言**：关闭后 rules=0 / 拦截行 0 / 挂载层 0；关闭态下 5 个入口逐一尝试后 hosts 仍与原版一致；白名单 97 条在最终 hosts 中零命中（含通配后缀匹配）；推送厂商域确认未被拦截。

## 九、v1.6.0：规则扩充与指标验收

**新增规则 6183 条**：Cats-Team AdRules 1014、AdAway 官方 5164、手工直补 5。全量 15615 条（精选 251 + 增强 15364），hosts 31238 行。

**指标**（`python3 tests/coverage.py`）：

| 指标 | v1.5.0 | v1.6.0 |
| --- | ---: | ---: |
| 广告样本覆盖率 | 86.7% | **94.9%**（93/98） |
| 功能样本误伤 | 0/98 | **0/98** |
| 白名单命中 | 0 | **0** |
| 沙箱断言 | 72 | **72 全绿** |

**候选源的取舍**（详细见 `docs/RULES-PLAN-v1.6.md`）：217heidai 清单命中 Google 账号与 Firebase 安装域，弃用（仅借其 6563 条放行名单）；hagezi light 92k 条对覆盖率零贡献，弃用；yhosts 停更于 2022 且命中 5 个现行功能域，弃用。

**未覆盖的 5 个样本域**均为上游主动放行（`tanx.com`、`miaozhen.com`、`alimama.com` 拦根域会坏淘宝商品图；`app-measurement.com` 是 Google 归因；`sing.snssdk.com` 与字节 CDN 同域），按功能底线保持放行。
