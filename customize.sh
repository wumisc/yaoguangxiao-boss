#!/system/bin/sh
# 模块安装时执行（KernelSU / Magisk 均支持）：把内置的开屏跳过 App 装上
SKIP_APK="$MODPATH/apk/yaoguangxiao-skip.apk"
say() { if command -v ui_print >/dev/null 2>&1; then ui_print "$1"; fi; }

[ -f "$SKIP_APK" ] || { say "! 内置 APK 缺失，跳过"; return 0; }

if ! command -v pm >/dev/null 2>&1; then
  say "- 当前环境没有 pm，开机后由 service.sh 自动安装"
  return 0
fi

say "- 正在安装「姚广孝 · 跳过」"
if pm install -r -d "$SKIP_APK" >/dev/null 2>&1; then
  mkdir -p "$MODPATH/state" 2>/dev/null
  echo "13" > "$MODPATH/state/app.ver" 2>/dev/null
  say "- 已安装，去「无障碍」里开启它即可生效"
else
  say "! 安装失败，开机后 service.sh 会再试一次"
fi
return 0

# 规则版本种子：让第一次开机不必立刻去 GitHub 拉一遍
mkdir -p "$MODPATH/state" 2>/dev/null
ver=$(sed -n 's/^versionCode=//p' "$MODPATH/module.prop" 2>/dev/null | head -1 | tr -dc '0-9')
[ -n "$ver" ] && echo "$ver" > "$MODPATH/state/rules.ver" 2>/dev/null
