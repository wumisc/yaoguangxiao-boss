#!/system/bin/sh
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=/data/adb/modules/yaoguangxiao_boss

MODDIR="$MODDIR" /system/bin/sh "$MODDIR/bin/ygb" status
echo "--------------------------------"
echo "提示: 打开 KSU 管理器的模块 WebUI 可开关拦截、加规则、看日志。"
echo "姚广孝 BOSS · 杀掉一切广告，省心省力。"
