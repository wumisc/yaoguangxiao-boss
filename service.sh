#!/system/bin/sh
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=/data/adb/modules/yaoguangxiao_boss

if [ -f "$MODDIR/disable" ]; then exit 0; fi
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
