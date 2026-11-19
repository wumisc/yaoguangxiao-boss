#!/system/bin/sh
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=/data/adb/modules/yaoguangxiao_boss

# 模块被管理器禁用 or 用户手动暂停 -> 不挂载
if [ -f "$MODDIR/disable" ]; then exit 0; fi
if [ -f "$MODDIR/state/off" ]; then exit 0; fi

mkdir -p "$MODDIR/build" "$MODDIR/logs" 2>/dev/null

MODDIR="$MODDIR" /system/bin/sh "$MODDIR/bin/ygb" build >/dev/null 2>&1
MODDIR="$MODDIR" /system/bin/sh "$MODDIR/bin/ygb" mount >/dev/null 2>&1
exit 0
