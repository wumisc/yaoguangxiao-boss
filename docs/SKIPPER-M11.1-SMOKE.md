# 姚广孝 · 跳过 v0.3.1-m11.1 冒烟测试与修复记录

- 日期：2026-11-27
- APK：`姚广孝跳过_v0.3.1-m11.1.apk`
- versionCode：14
- versionName：`0.3.1-m11.1`
- SHA-256：`c2e686cbfd505bda368e79b203cd01237aba4ce3b9438361408b1541b27fe8f1`
- 签名：APK Signature Scheme v2，Debug 证书（与现有 m11 同一签名）

## 一、这版修了什么

1. **无障碍声明**
   - `accessibility_service_config.xml` 增加 `android:isAccessibilityTool="true"`。
   - 侧载/Android 13+ 受限设置下更容易被系统当成正常无障碍工具。

2. **状态事件被内容节流吞掉**
   - 原来所有事件共用一个 70ms 节流；`TYPE_WINDOW_STATE_CHANGED` 后紧接着的 `TYPE_WINDOW_CONTENT_CHANGED` 可能带着“跳过”节点，却被丢掉。
   - 现在状态事件不参与内容节流，内容事件仍保持 70ms。

3. **空子节点丢兄弟**
   - `SnapshotMapper` 的 `getChild(i) ?: break` 会在一个空子节点时丢掉后面所有兄弟节点。
   - 改成跳过空节点继续遍历。

4. **工作窗口默认值**
   - 默认从 3 秒调到 5 秒；设置上限从 5 秒放宽到 8 秒。
   - 首页副标题改为显示实际窗口，不再写死“3 秒”。
   - 已保存 3 秒的用户不会被强行改，可手动调大。

5. **正则缓存**
   - `Scoring` 不再为每个节点 × 每条规则 `new Regex`；规则正则只编译一次，降低无障碍主线程开销。

6. **建议池写盘**
   - `SuggestStore.record` 不再在无障碍主线程整文件 `writeText`；改单线程 IO，避免广告页动画时频繁卡顿。

7. **心跳写盘**
   - `SettingsStore.markAlive` 仍每 5 秒更新内存心跳，但 SharedPreferences 最多 30 秒落盘一次。

8. **运行诊断**
   - 首页新增“运行诊断”卡：事件总数、状态/内容事件数、最后包名、节点数、最后未命中原因、分数/规则、根节点为空次数、节流次数。
   - 只存在内存，不写盘、不联网；UI 最多 1 秒刷新一次。

## 二、测试结果

### JVM 功能靶场

- `./gradlew jvmTest --offline`
- 结果：**45 / 45 通过，0 失败**
- 新增回归：`状态事件不被内容节流吞掉`

### APK 冒烟

脚本：`tests/app-smoke.sh`

结果：**PASS=11 FAIL=0**

- jvmTest 构建通过
- 测试数 ≥ 45，失败 0
- assembleRelease 构建通过
- APK 存在
- versionCode=14
- versionName=0.3.1-m11.1
- 包名 com.wumisc.skipper
- 未申请 INTERNET
- 未申请 QUERY_ALL_PACKAGES
- 无障碍配置 `isAccessibilityTool=true`
- APK 签名 v2 校验通过

## 三、没有做的验证

- 当前环境没有连接安卓真机/模拟器，**没有做装机后的实际点击验证**。
- 首页诊断卡需要用户装到手机上看一次：打开无障碍、触发一个开屏，确认“事件数”是否增长、最后未命中原因是什么。
- 如果事件数为 0：无障碍没真正连上或被系统冻结；如果事件数增长但 `NO_NODE`：按钮不在无障碍树或规则文字不匹配。

## 四、产物

- APK：`姚广孝跳过_v0.3.1-m11.1.apk`
- 冒烟脚本：`skipper/tests/app-smoke.sh`
- 源码：`skipper/`
