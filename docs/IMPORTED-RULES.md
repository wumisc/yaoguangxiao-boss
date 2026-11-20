# 专项规则包（从公开 GKD 订阅提炼）

生成时间：2026-11-20 17:54

| 来源 | 许可 | 用途 |
|---|---|---|
| `gkd-kit/subscription` | MIT | 只取「开屏广告」类的文字与控件 id |
| `Adpro-Team/GKD_subscription` | SATA-2.0（需 star + 致谢） | 只取「开屏广告」类的文字与控件 id |
| `ganlinte/gkd-subscription` | MIT | 只取「开屏广告」类的文字与控件 id |
| `MengNianxiaoyao/gkd-subscription` | MIT | 只取「开屏广告」类的文字与控件 id |

- 规则包：82 个应用，文字正则 76 条，id 正则 29 条
- 扫描到开屏规则组 470 个；因金融/政务/系统类而跳过的应用 115 个

## 只借了什么

GKD 的规则是「选择器」：按 id、层级、颜色、位置定位控件，点一下。
本工程是纯文字 + 控件 id + 几何三种信号的轻量引擎，所以只提炼了两样东西：

1. **哪些包有开屏跳过按钮**（包名清单）；
2. **那个按钮上写什么字 / 叫什么 id**（转成正则）。

层级、颜色、坐标一律不用，也不复制别人的执行流程。这既是能力边界，也是风险边界——
我们只做「点跳过」，不做屏蔽、不做拦截、不改别人的界面。

## 许可与致谢

- `gkd-kit/subscription`：MIT，可直接使用，保留出处。
- `Adpro-Team/GKD_subscription`：SATA-2.0，要求使用前给作者点 star 并致谢。已在此致谢。
- 未收录 AIsouler / JamisonLeo / kunjinkao（114514）：仓库未声明许可证，默认保留所有权利，不碰。
- 未收录 aoguai 订阅：GPL-3.0，与本仓库（未声明许可）不兼容，规则数据一旦并入就得整包 GPL，作罢。
- 未收录 YaChengMu 订阅：改版 MIT 附带「禁止公开推广」等额外条款，且其 dist 地址 404；宁可少收，不惹条款。

## 数据不进仓库

`tools/gkd_import.py` 进仓库，生成的 `imported.tsv` 只落在构建产物与 APK 里，
`.gitignore` 已排除。这样公开的是「方法」，不是别人的「数据」。
