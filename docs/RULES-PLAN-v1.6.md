# 规则扩充计划 v1.6.0（执行记录）

> 目标：把开源社区里可用的 hosts 广告规则吸收进来，**功能误伤为 0 是硬底线，广告覆盖率是唯一考核指标**。

## 一、指标定义

| 指标 | 定义 | 工具 |
| --- | --- | --- |
| 广告覆盖率 | `tests/sets/ads.txt`（98 个真实广告 SDK 请求域）中被最终 hosts 拦到的比例 | `python3 tests/coverage.py` |
| 功能误伤 | `tests/sets/func.txt`（98 个登录/支付/推送/地图/云服务域）中被拦到的数量，必须为 0 | 同上 |
| 白名单命中 | 放行名单里任意一条出现在最终 hosts 中的数量，必须为 0 | 同上 + 沙箱套件 |
| 体量 | 生效域名数 / hosts 行数 / 模块 zip 体积 | `ygb ui-status` |

## 二、候选源与取舍

| 源 | 规模 | 结论 | 理由 |
| --- | --- | --- | --- |
| **Cats-Team/AdRules**（`mod/rules/dns-rules.txt`） | 1626 条 | **采用 1014 条** | 国内广告联盟为主，命名直白，过完闸后功能样本零命中 |
| **AdAway 官方 hosts**（`adaway.org/hosts.txt`） | 6541 条 | **采用 5164 条** | 国际经典清单，粒度到具体广告子域 |
| **217heidai/adblockfilters** | 11106 条 | 不采用，只借用其 **6563 条放行名单** | 逐个试过：命中 `accounts.google.com`、`firebaseinstallations.googleapis.com` 等功能域，且对广告样本覆盖率贡献为 0 |
| **hagezi/dns-blocklists（light）** | 92176 条 | 不采用 | 78% 是 `xxx.com` 整域 + 随机子域，加进来只能把 hosts 撑到 10 万行，覆盖率提升 0 |
| **VeleSila/yhosts** | 6422 条 | 不采用 | 最后更新停在 2022，实测拦到 5 个现行功能域，典型的"老规则杀新功能" |
| **AdGuard ChineseFilter** | 已用于增强包 | 沿用 | — |

> 关键判断：**规则条数不是指标，覆盖率才是**。216 条样本里，加 6 千条规则覆盖率 86.7%→94.9%；再加 10 万条，覆盖率一动不动。

## 三、五道闸（每天的规则都要过一遍）

1. **重复闸**：已在 `base.txt` / `boost.txt` 里的直接跳过；
2. **放行闸**：命中 `lists/white.txt`（含 `*.` 后缀通配）或上游清单自带 allowlist 的丢弃；
3. **功能词闸**：`login|auth|passport|captcha|pay|wallet|push|mipush|getui|jpush|fcm|dns|location|map|update|ota|cloud|sync|sdk-init` 等 60 余个关键词命中即丢弃；
4. **CDN 闸**：`gtimg|qpic|aliyuncs|pstatp|amazonaws|alicdn|hdslb` 等内容分发域丢弃，拦了图片视频全挂；
5. **平台主域闸**：`qq.com`、`alipay.com`、`baidu.com`、`xiaomi.com`、`cmbchina.com`、`12306.cn`、`gov.cn`、`apple.com`、`googleapis.com` 等 60 个平台根域整体保护，只允许拦其广告子域。

## 四、执行结果（v1.6.0）

| 项 | v1.5.0 | v1.6.0 |
| --- | ---: | ---: |
| 精选 | 251 | 251 |
| 增强 | 9181 | 15364 |
| 生效域名 | 9432 | **15615** |
| hosts 行数 | 18872 | 31238 |
| 广告覆盖率 | 86.7% | **94.9%** |
| 功能误伤 | 0 | **0** |
| 白名单命中 | 0 | **0** |
| 沙箱断言 | 72 | 72（全绿） |

未覆盖的 5 个样本域均为上游清单**主动放行**的域（`tanx.com`、`miaozhen.com`、`alimama.com` 属阿里广告平台，拦根域会影响淘宝商品图；`app-measurement.com` 属 Google 归因；`sing.snssdk.com` 与字节 CDN 同域）。按"功能底线"原则保持放行。

## 五、复现方式

```bash
# 覆盖率 / 误伤体检（在仓库根目录）
python3 tests/coverage.py

# 功能回归（需要 unshare 权限，模拟真安卓根）
unshare -rm -- tests/sandbox/run.sh
```
