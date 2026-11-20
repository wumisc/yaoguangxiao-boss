#!/bin/bash
# 装机靶场：用假 pm 验证「装模块 = 装 App」这条链路（首次装、已装不重复装、无 pm 环境静默跳过、卸载带走）
set -u
cd "$(dirname "$0")/.."
MOD=${MOD:-姚广孝BOSS}
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
no(){ FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }

WORK=$(mktemp -d)
mkdir -p "$WORK/bin" "$WORK/mod"
cp -r "$MOD"/apk "$MOD"/service.sh "$MOD"/customize.sh "$MOD"/bin "$MOD"/lists "$MOD"/uninstall.sh "$WORK/mod/" 2>/dev/null
CALLS="$WORK/calls.log"

# 假 pm：list 只在「已装」文件存在时报告已装
cat > "$WORK/bin/pm" <<'PM'
#!/bin/sh
echo "$@" >> "$CALLS"
case "$1" in
  list) [ -f "$STATE_INSTALLED" ] && echo "package:com.wumisc.skipper"; exit 0;;
  install) [ -f "$STATE_FAIL" ] && exit 1; : > "$STATE_INSTALLED"; exit 0;;
  uninstall) rm -f "$STATE_INSTALLED"; exit 0;;
  *) exit 0;;
esac
PM
chmod +x "$WORK/bin/pm"
export CALLS STATE_INSTALLED="$WORK/installed" STATE_FAIL="$WORK/failflag"

echo "== 1. 安装模块时（customize.sh）"
rm -f "$CALLS" "$STATE_INSTALLED"
env -i PATH="$WORK/bin:/usr/bin:/bin" CALLS="$CALLS" STATE_INSTALLED="$STATE_INSTALLED" STATE_FAIL="$STATE_FAIL" MODPATH="$WORK/mod" sh -c "cd '$WORK/mod' && sh customize.sh >/dev/null 2>&1"
grep -q "^install -r -d $WORK/mod/apk/yaoguangxiao-skip.apk" "$CALLS" && ok "调用了 pm install 且指向内置 APK" || no "调用了 pm install 且指向内置 APK"
[ "$(cat "$WORK/mod/state/app.ver" 2>/dev/null)" = "4" ] && ok "写入版本标记 app.ver=4" || no "写入版本标记 app.ver=4"

echo "== 2. 开机守护（service.sh）已装且版本一致"
rm -f "$CALLS"
env -i PATH="$WORK/bin:/usr/bin:/bin" CALLS="$CALLS" STATE_INSTALLED="$STATE_INSTALLED" STATE_FAIL="$STATE_FAIL" MODDIR="$WORK/mod" timeout 3 sh "$WORK/mod/service.sh" >/dev/null 2>&1
grep -q "^install" "$CALLS" && no "不应重复安装" || ok "不重复安装，只查一次 pm list"

echo "== 3. 换成新版本（标记不一致）"
echo "3" > "$WORK/mod/state/app.ver"; mkdir -p "$WORK/mod/state"; echo "3" > "$WORK/mod/state/app.ver"
rm -f "$CALLS"
env -i PATH="$WORK/bin:/usr/bin:/bin" CALLS="$CALLS" STATE_INSTALLED="$STATE_INSTALLED" STATE_FAIL="$STATE_FAIL" MODDIR="$WORK/mod" timeout 3 sh "$WORK/mod/service.sh" >/dev/null 2>&1
grep -q "^install" "$CALLS" && ok "版本不一致时自动重装" || no "版本不一致时自动重装"

echo "== 4. 没有 pm 的环境（靶场 chroot 形态）"
rm -f "$CALLS"
env -i PATH=/usr/bin:/bin MODPATH="$WORK/mod" sh -c "cd '$WORK/mod' && sh customize.sh >/dev/null 2>&1"; RC=$?
[ $RC -eq 0 ] && ok "无 pm 时静默返回 0，不阻断安装" || no "无 pm 时静默返回 0，不阻断安装"

echo "== 5. 卸载模块时带走 App"
rm -f "$CALLS"
env -i PATH="$WORK/bin:/usr/bin:/bin" CALLS="$CALLS" STATE_INSTALLED="$STATE_INSTALLED" STATE_FAIL="$STATE_FAIL" MODDIR="$WORK/mod" sh "$WORK/mod/uninstall.sh" >/dev/null 2>&1
grep -q "^uninstall com.wumisc.skipper" "$CALLS" && ok "调用了 pm uninstall" || no "调用了 pm uninstall"
[ -f "$STATE_INSTALLED" ] && no "App 未被卸载" || ok "App 已从系统移除"

rm -rf "$WORK"
echo
echo "========== 装机靶场: PASS=$PASS FAIL=$FAIL =========="
[ $FAIL -eq 0 ]
