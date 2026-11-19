#!/system/bin/sh
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=/data/adb/modules/yaoguangxiao_boss

# 卸载前恢复系统原始 hosts，两种分区布局都处理，避免残留挂载
for t in /system/etc/hosts /system_root/system/etc/hosts; do
  [ -f "$t" ] || continue
  umount "$t" 2>/dev/null
  umount -l "$t" 2>/dev/null
done

rm -rf "$MODDIR/state/.lock" 2>/dev/null
exit 0
