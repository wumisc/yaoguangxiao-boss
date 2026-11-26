#!/system/bin/sh
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=/data/adb/modules/yaoguangxiao_boss

if [ -f "$MODDIR/disable" ]; then exit 0; fi

# ---------- 内置「跳过」App：开机确保已安装 / 已更新 ----------
APP_ID=com.wumisc.skipper
APP_APK="$MODDIR/apk/yaoguangxiao-skip.apk"
APP_VER=13
install_skip_app() {
  [ -f "$APP_APK" ] || return 0
  command -v pm >/dev/null 2>&1 || return 0
  cur=$(cat "$MODDIR/state/app.ver" 2>/dev/null)
  if [ "$cur" = "$APP_VER" ] && pm list packages 2>/dev/null | grep -q "package:$APP_ID"; then
    return 0
  fi
  if pm install -r -d "$APP_APK" >/dev/null 2>&1; then
    mkdir -p "$MODDIR/state" 2>/dev/null
    echo "$APP_VER" > "$MODDIR/state/app.ver" 2>/dev/null
    return 0
  fi
  cp "$APP_APK" /data/local/tmp/ygb-skip.apk 2>/dev/null
  if pm install -r -d /data/local/tmp/ygb-skip.apk >/dev/null 2>&1; then
    mkdir -p "$MODDIR/state" 2>/dev/null
    echo "$APP_VER" > "$MODDIR/state/app.ver" 2>/dev/null
    rm -f /data/local/tmp/ygb-skip.apk
  fi
  return 0
}
install_skip_app

# ---------- v1.8.2：停掉旧的前台观察者，学习迁到 App m12 ----------
# 旧版 adwatch 会每 30 秒 dumpsys + 换 App 解包扫 dex；升级后必须确保它不再运行。
stop_legacy_adwatch() {
  pid=$(cat "$MODDIR/state/adwatch.pid" 2>/dev/null | tr -dc '0-9')
  [ -n "$pid" ] && kill "$pid" 2>/dev/null
  command -v pkill >/dev/null 2>&1 && pkill -f "$MODDIR/bin/adwatch" 2>/dev/null
  rm -f "$MODDIR/state/adwatch.pid" 2>/dev/null
  return 0
}
stop_legacy_adwatch

# ---------- 每天最多一次：开机后去 GitHub 看一眼规则 ----------
maybe_update() {
  [ -f "$MODDIR/state/auto_update" ] || return 0
  last=$(cat "$MODDIR/state/update.at" 2>/dev/null | tr -dc '0-9')
  [ -z "$last" ] && last=0
  now=$(date +%s 2>/dev/null || echo 0)
  [ "$now" -gt 0 ] && [ $((now - last)) -lt 86400 ] && return 0
  ( sleep 45; MODDIR="$MODDIR" sh "$MODDIR/bin/ygb" update >/dev/null 2>&1 ) &
}

maybe_update

# App 学习结果：开机只导入一次，不扫描；App 还没产出时静默跳过。
if [ ! -f "$MODDIR/state/learn_import_off" ]; then
  MODDIR="$MODDIR" /system/bin/sh "$MODDIR/bin/ygb" import-learn >/dev/null 2>&1
fi

# 用户手动暂停 -> 只停拦截，App 仍然可用
if [ -f "$MODDIR/state/off" ]; then exit 0; fi

# 部分 ROM 会在开机后期重新挂载 /system，这里做 90 秒守护。
# 只查挂载状态，不再跑完整 status（避免每 3 秒对 15k 行做 4 次 count_valid）。
i=0
while [ $i -lt 30 ]; do
  # 用户中途在面板暂停，必须立刻停止守护，避免把已暂停的拦截重新挂上
  if [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/state/off" ]; then exit 0; fi
  if ! MODDIR="$MODDIR" /system/bin/sh "$MODDIR/bin/ygb" mounted >/dev/null 2>&1; then
    MODDIR="$MODDIR" /system/bin/sh "$MODDIR/bin/ygb" mount >/dev/null 2>&1
  fi
  i=$((i + 1))
  sleep 3
done
exit 0
