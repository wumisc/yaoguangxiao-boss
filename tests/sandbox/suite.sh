#!/system/bin/sh
# 姚广孝 BOSS · 靶场功能测试套件（在假安卓根内执行）
# v2：SM 规则并入精选/增强；总开关关闭 = 停全部规则 + 还原 hosts
MOD=/data/adb/modules/yaoguangxiao_boss
YGB=$MOD/bin/ygb
HOSTS=/system/etc/hosts
ORIG=/etc.hosts.orig
STATE=$MOD/state
TOTAL=9432      # 精选 251 + 增强 9181
BASE=251
BOOST=9181
LINES=18872     # 8 行头 + 2*规则
WLC=97

pass=0; fail=0
ok()   { pass=$((pass+1)); printf 'PASS  %s\n' "$1"; }
bad()  { fail=$((fail+1)); printf 'FAIL  %s\n      期望=%s\n      实际=%s\n' "$1" "$3" "$2"; }
eq()   { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "$2" "$3"; fi }
cnt()  { grep -c "$1" "$2" 2>/dev/null | head -1; }
y()    { /system/bin/sh "$YGB" "$@"; }
j()    { printf '%s' "$1" | jq -r "$2" 2>/dev/null; }

echo "========== 0. 环境准备 =========="
rm -rf "$STATE"; mkdir -p "$STATE"
: > "$MOD/lists/user.txt"
# 白名单保持模块原样（97 条功能兜底 + 3 家推送/云厂商），用例只临时增删 *.test.com

echo "========== 1. 开机脚本 post-fs-data =========="
MODDIR=$MOD /system/bin/sh "$MOD/post-fs-data.sh"
eq "post-fs-data 后 hosts 已挂载" "$(cnt 'yaoguangxiao-boss' "$HOSTS")" "1"
eq "首启生效域名数" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$TOTAL"
eq "hosts 总行数" "$(wc -l < "$HOSTS" | tr -d ' ')" "$LINES"

echo "========== 2. 状态与 JSON =========="
S=$(y ui-status)
eq "ui-status 是合法 JSON" "$(printf '%s' "$S" | jq -e . >/dev/null 2>&1 && echo valid || echo invalid)" "valid"
eq "JSON rules" "$(j "$S" '.rules')" "$TOTAL"
eq "JSON mounted" "$(j "$S" '.mounted')" "yes"
eq "JSON base" "$(j "$S" '.base')" "$BASE"
eq "JSON boost" "$(j "$S" '.boost')" "$BOOST"
eq "JSON boostOn" "$(j "$S" '.boostOn')" "1"
eq "JSON custom" "$(j "$S" '.custom')" "0"
eq "JSON white" "$(j "$S" '.white')" "$WLC"
eq "已无 SM 字段" "$(j "$S" '.sm')" "null"
eq "状态文本无 SM 包行" "$(y status | grep -c 'SM')" "0"

echo "========== 3. 强力模式开关（关键回归） =========="
INO1=$(ls -i "$MOD/build/hosts" | awk '{print $1}')
y boost-off >/dev/null
INO2=$(ls -i "$MOD/build/hosts" | awk '{print $1}')
eq "规则文件 inode 未更换" "$INO2" "$INO1"
eq "关闭后 hosts 立即降到精选" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$BASE"
S=$(y ui-status)
eq "关闭后 boost 计数归零" "$(j "$S" '.boost')" "0"
eq "关闭后 boostOn=0" "$(j "$S" '.boostOn')" "0"
eq "关闭后 rules" "$(j "$S" '.rules')" "$BASE"
y boost-on >/dev/null
eq "重新开启恢复全量" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$TOTAL"

echo "========== 4. 总开关：关闭必须停掉全部规则与 hosts =========="
y off >/dev/null
eq "关闭后 hosts 与系统原样逐字节一致" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
eq "关闭后 hosts 无任何拦截行" "$(grep -c '^0.0.0.0 ' "$HOSTS")" "0"
eq "关闭后挂载层为 0" "$(mount | grep -c ' /system/etc/hosts ')" "0"
S=$(y ui-status)
eq "关闭后 mounted=no" "$(j "$S" '.mounted')" "no"
eq "关闭后 state=off" "$(j "$S" '.state')" "off"
eq "关闭后 rules=0" "$(j "$S" '.rules')" "0"
eq "关闭后 custom=0" "$(j "$S" '.custom')" "0"
eq "关闭后 status 拦截域名 0" "$(y status | grep '拦截域名' | grep -c '拦截域名: 0')" "1"
eq "关闭状态落盘 state/off" "$([ -f "$STATE/off" ] && echo yes || echo no)" "yes"
y on >/dev/null
eq "恢复后重新挂载" "$(cnt 'yaoguangxiao-boss' "$HOSTS")" "1"
eq "恢复后规则齐全" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$TOTAL"

echo "========== 4b. 关闭态不得被任何动作偷偷挂回 =========="
y off >/dev/null
y apply >/dev/null
eq "关闭时 apply 不挂载" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
y add pause.test.com >/dev/null
eq "关闭时加规则不挂载" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
y white-add block.test.com >/dev/null
eq "关闭时改白名单不挂载" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
y boost-on >/dev/null
eq "关闭时开强力模式不挂载" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
y mount >/dev/null
eq "关闭时手动 mount 被拦截" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
timeout 4 /system/bin/sh "$MOD/service.sh" 2>/dev/null
eq "关闭时守护脚本不挂载" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
MODDIR=$MOD /system/bin/sh "$MOD/post-fs-data.sh"
eq "关闭时开机脚本不挂载" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
y on >/dev/null
eq "重新开启后挂载恢复" "$(cnt '^0.0.0.0 pause.test.com$' "$HOSTS")" "1"
y del pause.test.com >/dev/null
y white-del block.test.com >/dev/null

echo "========== 5. 自定义规则 =========="
y add ad.test123.com >/dev/null
eq "新增规则后计数+1" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$((TOTAL+1))"
eq "新增规则写进 hosts" "$(cnt '^0.0.0.0 ad.test123.com$' "$HOSTS")" "1"
y add ad.test123.com >/dev/null
eq "重复添加幂等" "$(cnt '^0.0.0.0 ad.test123.com$' "$HOSTS")" "1"
eq "重复添加不重复计数" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$((TOTAL+1))"
y del ad.test123.com >/dev/null
eq "删除后计数回落" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$TOTAL"

echo "========== 6. 输入校验 =========="
eq "非法(空格)被拒" "$(y add 'bad domain.com')" "ERR 非法域名: bad domain.com"
eq "非法(中文)被拒" "$(y add '中文广告.com')" "ERR 非法域名: 中文广告.com"
y add 'https://AD.Test123.COM/path?x=1' >/dev/null
eq "URL 归一化为域名" "$(cnt '^0.0.0.0 ad.test123.com$' "$HOSTS")" "1"
y del ad.test123.com >/dev/null

echo "========== 7. 白名单与通配 =========="
y white-add '*.test.com' >/dev/null
y add ad.test.com >/dev/null
eq "白名单通配放行子域" "$(cnt '^0.0.0.0 ad.test.com$' "$HOSTS")" "0"
y white-del '*.test.com' >/dev/null
eq "移出白名单后生效" "$(cnt '^0.0.0.0 ad.test.com$' "$HOSTS")" "1"
y del ad.test.com >/dev/null
y add '*.wild.com' >/dev/null
eq "通配规则落成主域" "$(cnt '^0.0.0.0 wild.com$' "$HOSTS")" "1"
eq "hosts 中不存在字面通配" "$(cnt '\*\.wild\.com' "$HOSTS")" "0"
y del '*.wild.com' >/dev/null

cp "$MOD/lists/white.txt" "$STATE/white.bak"
printf 'nokey.example.com' > "$MOD/lists/white.txt"
y white-add '*.nonl.com' >/dev/null
y add ad.nonl.com >/dev/null
eq "列表无结尾换行仍能正确追加" "$(cnt '^0.0.0.0 ad.nonl.com$' "$HOSTS")" "0"
y del ad.nonl.com >/dev/null
cp "$STATE/white.bak" "$MOD/lists/white.txt"
y apply >/dev/null

echo "========== 8. 脏数据兼容 =========="
printf 'AD-UPPER.COM\r\n# 注释\r\n   spaced.com  \r\n' >> "$MOD/lists/user.txt"
y build >/dev/null; y mount >/dev/null
eq "大写转小写" "$(cnt '^0.0.0.0 ad-upper.com$' "$HOSTS")" "1"
eq "前后空格被裁掉" "$(cnt '^0.0.0.0 spaced.com$' "$HOSTS")" "1"
: > "$MOD/lists/user.txt"; y apply >/dev/null
eq "清理后回到全量" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$TOTAL"

echo "========== 9. 零规则边界（JSON 不得损坏） =========="
mkdir -p /tmp/hold
mv "$MOD/lists/base.txt" /tmp/hold/base.txt
mv "$MOD/lists/boost.txt" /tmp/hold/boost.txt
y apply >/dev/null
S=$(y ui-status)
eq "零规则时 JSON 合法" "$(printf '%s' "$S" | jq -e . >/dev/null 2>&1 && echo valid || echo invalid)" "valid"
eq "零规则时 rules=0" "$(j "$S" '.rules')" "0"
mv /tmp/hold/base.txt "$MOD/lists/base.txt"
mv /tmp/hold/boost.txt "$MOD/lists/boost.txt"
y apply >/dev/null
eq "恢复后全量" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$TOTAL"

echo "========== 10. 自愈与日志 =========="
rm -f "$MOD/build/hosts"
S=$(y ui-status)
eq "规则文件丢失时自愈" "$(j "$S" '.rules')" "$TOTAL"
i=0; while [ $i -lt 300 ]; do echo "filler $i" >> "$MOD/logs/apply.log"; i=$((i+1)); done
y apply >/dev/null
eq "日志自动裁剪到 <=200 行" "$([ "$(wc -l < "$MOD/logs/apply.log" | tr -d ' ')" -le 200 ] && echo yes || echo no)" "yes"

echo "========== 11. 并发与重复挂载 =========="
y apply >/dev/null & y apply >/dev/null & wait
eq "并发 apply 后仍只有一层挂载" "$(mount | grep -c ' /system/etc/hosts ')" "1"
eq "并发后规则完整" "$(cnt '^0.0.0.0 ' "$HOSTS")" "$TOTAL"

echo "========== 12. service.sh 守护 =========="
timeout 4 /system/bin/sh "$MOD/service.sh" 2>/dev/null
rc=$?
eq "守护脚本无异常退出(超时=正常常驻)" "$([ "$rc" = "0" ] || [ "$rc" = "124" ] && echo ok || echo "$rc")" "ok"
eq "守护后仍挂载" "$(cnt 'yaoguangxiao-boss' "$HOSTS")" "1"

echo "========== 13. 文件权限 =========="
eq "ygb 可执行" "$([ -x "$YGB" ] && echo yes || echo no)" "yes"
eq "post-fs-data 可执行" "$([ -x "$MOD/post-fs-data.sh" ] && echo yes || echo no)" "yes"
eq "service.sh 可执行" "$([ -x "$MOD/service.sh" ] && echo yes || echo no)" "yes"

echo "========== 13b. 误伤体检（功能域必须放行） =========="
BAD=0
for d in httpdns.music.163.com android.push.126.net news.push.126.net mhdpay.1391.com \
         12306media.com appcfg.v.qq.com api.poll.video.qq.com openmobile.qq.com \
         ti.qq.com pp.myapp.com a.video.qq.com iwx.mail.qq.com \
         api.weixin.qq.com openapi.alipay.com api.m.jd.com api.map.baidu.com \
         api.douyin.com api.bilibili.com api.github.com dl.google.com \
         sdk.open.phone.igexin.com api.jpush.cn ad-host-backup-asia.s3.ap-southeast-1.amazonaws.com; do
  grep -q "^0.0.0.0 $d$" "$HOSTS" && { BAD=$((BAD+1)); echo "      发现误伤: $d"; }
done
eq "功能域零误伤" "$BAD" "0"
WLBAD=$(awk '
  FNR==1 { n=0 }
  FILENAME ~ /white.txt/ { l=$0; gsub(/\r/,"",l); sub(/^[ \t]+/,"",l); sub(/[ \t]+$/,"",l);
    if (l=="" || l ~ /^#/) next; l=tolower(l);
    if (l ~ /^\*\./) s[substr(l,3)]=1; else e[l]=1; next }
  $1=="0.0.0.0" { d=$2; if (d in e) { print d; next }
    for (k in s) if (d==k || index(d, "." k) > 0) { print d; break } }' \
  "$MOD/lists/white.txt" "$HOSTS" | wc -l | tr -d ' ')
eq "白名单零命中（放行全部生效）" "$WLBAD" "0"
NW=$(grep -v -E '^#|^$' "$MOD/lists/white.txt" | sort -u | wc -l | tr -d ' ')
eq "白名单条数" "$NW" "$WLC"
eq "推送厂商主域整体放行" "$(cnt '^0.0.0.0 .*\.igexin\.com$' "$HOSTS")" "0"

echo "========== 14. 卸载还原 =========="
/system/bin/sh "$MOD/uninstall.sh"
eq "卸载后 hosts 还原" "$(cmp -s "$HOSTS" "$ORIG" && echo same || echo diff)" "same"
eq "卸载后无残留挂载" "$(mount | grep -c ' /system/etc/hosts ')" "0"

echo ""
echo "========== 结果: PASS=$pass FAIL=$fail =========="
[ "$fail" -eq 0 ] && exit 0 || exit 1
