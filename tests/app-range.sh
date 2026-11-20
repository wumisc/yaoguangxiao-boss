#!/bin/bash
# APK 静态靶场：装不了真机也能先把「能不能装、会不会越权、服务对不对」查一遍
set -u
cd "$(dirname "$0")/.."
APK=${1:-skipper/app/build/outputs/apk/release/app-release.apk}
AAPT=${AAPT:-/home/user/android-sdk/build-tools/36.0.0/aapt2}
DEXDUMP=${DEXDUMP:-/home/user/android-sdk/build-tools/36.0.0/dexdump}
APKSIGNER=${APKSIGNER:-/home/user/android-sdk/build-tools/36.0.0/apksigner}
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
no(){ FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
chk(){ # chk 描述 条件
  if eval "$2"; then ok "$1"; else no "$1"; fi
}

echo "== 1. 包基本信息"
BADGING=$("$AAPT" dump badging "$APK")
chk "包名 = com.wumisc.skipper" "echo \"\$BADGING\" | grep -q \"package: name='com.wumisc.skipper'\""
chk "versionCode 有值" "echo \"\$BADGING\" | grep -q \"versionCode='[0-9]\""
chk "minSdk >= 29" "echo \"\$BADGING\" | awk -F\"'\" '/minSdkVersion/ {print \$2}' | grep -qE '^(29|3[0-9])$'"
chk "启动 Activity 存在" "echo \"\$BADGING\" | grep -q \"launchable-activity: name='com.wumisc.skipper.ui.MainActivity'\""

echo "== 2. 权限面"
chk "没有 INTERNET 权限" "! echo \"\$BADGING\" | grep -q \"uses-permission: name='android.permission.INTERNET'\""
chk "没有 QUERY_ALL_PACKAGES" "! echo \"\$BADGING\" | grep -q 'QUERY_ALL_PACKAGES'"
chk "没有 READ/WRITE 外部存储" "! echo \"\$BADGING\" | grep -qE 'READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE'"

echo "== 3. 无障碍服务声明"
MANIFEST=$("$AAPT" dump xmltree --file AndroidManifest.xml "$APK")
chk "声明了 SkipAccessibilityService" "echo \"\$MANIFEST\" | grep -q 'com.wumisc.skipper.service.SkipAccessibilityService'"
chk "绑定 BIND_ACCESSIBILITY_SERVICE" "echo \"\$MANIFEST\" | grep -q 'android.permission.BIND_ACCESSIBILITY_SERVICE'"
chk "带 accessibilityservice 的 meta-data" "echo \"\$MANIFEST\" | grep -q 'android.accessibilityservice'"

echo "== 4. 无障碍配置"
CFG_PATH=$("$AAPT" dump resources "$APK" | grep -A2 'xml/accessibility_service_config' | grep -o 'res/[^ ]*\.xml' | head -1)
chk "无障碍配置文件在资源表里" "[ -n \"\$CFG_PATH\" ]"
CFG=$("$AAPT" dump xmltree --file "$CFG_PATH" "$APK" 2>/dev/null)
chk "canRetrieveWindowContent=true" "echo \"\$CFG\" | grep -q 'canRetrieveWindowContent.*=true'"
chk "监听窗口变化事件(0x820)" "echo \"\$CFG\" | grep -q 'accessibilityEventTypes.*=0x00000820'"
chk "拿到 viewId(flags 0x50)" "echo \"\$CFG\" | grep -q 'accessibilityFlags.*=0x00000050'"

echo "== 5. 关键类与资源"
DEX=$("$DEXDUMP" -f "$APK" 2>/dev/null | grep 'Class descriptor')
chk "SkipperApp 在 dex 里" "echo \"\$DEX\" | grep -q 'Lcom/wumisc/skipper/SkipperApp;'"
chk "MainActivity 在 dex 里" "echo \"\$DEX\" | grep -q 'Lcom/wumisc/skipper/ui/MainActivity;'"
chk "无障碍服务在 dex 里" "echo \"\$DEX\" | grep -q 'Lcom/wumisc/skipper/service/SkipAccessibilityService;'"
TMPDEX=$(mktemp -d)
if unzip -o -j "$APK" classes.dex -d "$TMPDEX" >/dev/null 2>&1; then
  SKIP_HITS=$(grep -c '跳过' "$TMPDEX/classes.dex" 2>/dev/null || echo 0)
  PKG_HITS=$(grep -c 'com.wumisc.skipper' "$TMPDEX/classes.dex" 2>/dev/null || echo 0)
else
  SKIP_HITS=0; PKG_HITS=0
fi
if [ "${SKIP_HITS:-0}" -ge 1 ]; then ok "引擎规则文字没被 R8 抹掉（跳过 * $SKIP_HITS）"; else no "引擎规则文字没被 R8 抹掉"; fi
if [ "${PKG_HITS:-0}" -ge 1 ]; then ok "包名引用还在"; else no "包名引用还在"; fi
rm -rf "$TMPDEX"
chk "图标资源存在" "unzip -l \"$APK\" | grep -qE 'res/.*\.png'"

echo "== 6. 签名与体积"
chk "签名可校验" "'$APKSIGNER' verify --print-certs \"$APK\" >/dev/null 2>&1"
SIZE=$(stat -c%s "$APK")
chk "体积 < 6MB（实际 $((SIZE/1024))KB）" "[ $SIZE -lt 6291456 ]"

echo
echo "========== APK 靶场: PASS=$PASS FAIL=$FAIL =========="
[ $FAIL -eq 0 ]
