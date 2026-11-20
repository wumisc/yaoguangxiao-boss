# 从 GitHub 更新规则

## 用

```sh
su -c '/data/adb/modules/yaoguangxiao_boss/bin/ygb update'
```

KSU 面板里点「检查更新」也是同一条命令。

- 有新版：下载 → 校验 → 备份 → 替换 → 重建 hosts → 立刻生效
- 已是最新：直接返回，不下载
- 网络不通 / 校验不过：明确报错，**旧规则保持不动**

## 更新的是什么

只更新三个规则文件：`lists/base.txt`、`lists/boost.txt`、`lists/white.txt`。

**永远不动**：`lists/user.txt`（你自己加的）、`lists/learned.txt`（学习器学的）、`state/*`。

## 安全闸

| 闸 | 作用 |
|---|---|
| 固定源 | 只认 `raw.githubusercontent.com/wumisc/yaoguangxiao-boss/main`，不接受任意 URL |
| sha256 | 每个文件和清单里的哈希逐一比对，半截下载 / 被篡改一律拒绝 |
| 行数 | 再比一次有效行数（非空非注释），防止「哈希对但内容被截断」这类怪事 |
| 版本比较 | 远端 version ≤ 本地就跳过，不做无意义下载 |
| 备份 | 旧版留在 `state/backup/<版本>/`，要回滚直接把备份拷回 `lists/` |
| 原子替换 | 先写 `.new` 再 `mv`，中途失败不会留下半个规则文件 |

## 换更新源

```sh
su -c '.../ygb update-url https://raw.githubusercontent.com/<user>/<repo>/<branch>'
su -c '.../ygb update-url'   # 不带参数 = 看用法
```

- 只接受 `https`，`http://` / `file://` 一律拒绝
- 地址存在 `state/update_url`，不填就用默认的 `wumisc/yaoguangxiao-boss/main`
- 适合：多台设备走自己的镜像、或先在内网/私有仓库试规则

## 自动检查

```sh
su -c '.../ygb update-auto-on'    # 开机后最多每天查一次
su -c '.../ygb update-auto-off'
su -c '.../ygb update-info'       # 本地版本 / 上次检查 / 开关状态
```

开机时网络往往还没起来，自动检查会等 45 秒再试一次；失败就记日志，下次开机再来。
想立刻要新规则，手动点一次「检查更新」最稳。

## 自己发版

1. 改 `lists/*.txt`
2. 跑 `python3 tools/make_manifest.py`（会按 `module.prop` 的 versionCode 生成 `updates/manifest.txt`）
3. 提交并推到 `main`，`lists/` 和 `updates/manifest.txt` 一起进仓库
4. 模块端 `ygb update` 就能拉到
