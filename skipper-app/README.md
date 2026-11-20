# 姚广孝 · 跳过

开屏广告「跳过」自动点击器。只点跳过，不屏蔽、不注入、不联网。配套 `姚广孝 BOSS` hosts 模块使用，也可以单独安装。

## 它做什么

- 窗口切换后 3 秒内，在顶部区域找一个**文字就是「跳过/关闭广告/Skip」、尺寸很小、点得动**的节点，点一下。
- 一个界面最多点 2 次，两次之间至少隔 3 秒。
- 金融、支付、政务、系统设置、安装器、输入法界面**永不动作**。

## 工程结构

```
app/src/commonMain/kotlin/.../engine   纯 Kotlin 判定引擎（零 Android 依赖，JVM 可测）
app/src/androidMain/kotlin/.../data    设置 / 规则 / 命中记录（StateFlow + SharedPreferences）
app/src/androidMain/kotlin/.../platform 无障碍树 → 纯数据快照
app/src/androidMain/kotlin/.../service  无障碍服务：取快照、点、上报
app/src/androidMain/kotlin/.../ui       Compose + Miuix 界面（首页 / 规则 / 记录 / 设置）
app/src/jvmTest/kotlin                  引擎单测（JUnit4，12 条）
docs                                   设计文档见仓库 docs/SKIPPER-UI-DESIGN.md
```

依赖方向单向：`ui → data ← service → platform → engine`。引擎不认识 Android，界面不认识无障碍 API。

## 构建

```bash
# JDK 21
./gradlew :app:assembleRelease --offline     # 产物 app/build/outputs/apk/release/app-release.apk
./gradlew :app:jvmTest --offline             # 引擎单测
```

工具链：AGP 9.5.1 / Kotlin 2.4.21 / Compose 1.12.2 / Miuix 0.9.4，compileSdk 37，minSdk 29。

## 使用

1. 安装后打开，按首页提示进入系统「无障碍」把「姚广孝 · 跳过」打开（只需一次）。
2. 首页总开关 = 引擎开关，关掉后一切点击停止，记录保留。
3. 遇到内置规则没覆盖的广告，在「规则 → 我的」里加一条：包名（可空）+ 按钮上的字。

## 边界

不屏蔽广告、不修改他人界面、不联网、不申请应用列表权限。仅供学习研究。
