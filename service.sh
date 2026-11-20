#!/system/bin/sh
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=/data/adb/modules/yaoguangxiao_boss

if [ -f "$MODDIR/disable" ]; then exit 0; fi

# ---------- 内置「跳过」App：开机确保已安装 / 已更新 ----------
APP_ID=com.wumisc.skipper
APP_APK="$MODDIR/apk/yaoguangxiao-skip.apk"
APP_VER=8
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

# ---------- 前台观察者：换一个 App 就学一次它的广告域名 ----------
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

start_adwatch() {
  [ -f "$MODDIR/state/learn_off" ] && return 0
  [ -f "$MODDIR/state/off" ] && return 0
  [ -x "$MODDIR/bin/adwatch" ] || return 0
  pid=$(cat "$MODDIR/state/adwatch.pid" 2>/dev/null)
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then return 0; fi
  ( setsid sh "$MODDIR/bin/adwatch" >/dev/null 2>&1 & ) 2>/dev/null \
    || ( nohup sh "$MODDIR/bin/adwatch" >/dev/null 2>&1 & ) 2>/dev/null
}

start_adwatch

# 用户手动暂停 -> 只停拦截，App 仍然可用
if [ -f "$MODDIR/state/off" ]; then exit 0; fi

# 部分 ROM 会在开机后期重新挂载 /system，这里做 90 秒守护
i=0
while [ $i -lt 30 ]; do
  # 用户中途在面板暂停，必须立刻停止守护，避免把已暂停的拦截重新挂上
  if [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/state/off" ]; then exit 0; fi
  if MODDIR="$MODDIR" /system/bin/sh "$MODDIR/bin/ygb" status 2>/dev/null | grep -q "挂载: no"; then
    MODDIR="$MODDIR" /system/bin/sh "$MODDIR/bin/ygb" mount >/dev/null 2>&1
  fi
  i=$((i + 1))
  sleep 3
done
exit 0
