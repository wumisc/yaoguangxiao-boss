#!/usr/bin/env bash
# APK 功能冒烟：单元/靶场 + Release 编译 + 包体关键断言
# 用法：JAVA_HOME=/path/to/jdk21 bash tests/app-smoke.sh
set -u
cd "$(dirname "$0")/.."
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
no(){ FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
chk(){ if eval "$2" >/dev/null 2>&1; then ok "$1"; else no "$1"; fi; }

JAVA_HOME=${JAVA_HOME:-/home/user/jdk21}
export JAVA_HOME
GRADLE="./gradlew --offline --console=plain"
AAPT2=${AAPT2:-/home/user/android-sdk/build-tools/36.0.0/aapt2}
APK=app/build/outputs/apk/release/app-release.apk

echo "== 1. JVM 单元/靶场 =="
if $GRADLE jvmTest >/tmp/skipper-smoke-test.log 2>&1; then
  ok "jvmTest 构建通过"
else
  no "jvmTest 构建失败（见 /tmp/skipper-smoke-test.log）"
  tail -20 /tmp/skipper-smoke-test.log
fi
TEST_SUM=$(python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
tests=fails=0
for f in glob.glob('app/build/test-results/jvmTest/*.xml'):
    r=ET.parse(f).getroot()
    tests += int(r.get('tests', 0))
    fails += int(r.get('failures', 0)) + int(r.get('errors', 0))
print(f"{tests} {fails}")
PY
)
set -- $TEST_SUM
TESTS=$1; FAILS=$2
chk "测试数 ≥ 45（实际 $TESTS）" "[ $TESTS -ge 45 ]"
chk "测试失败 = 0（实际 $FAILS）" "[ $FAILS -eq 0 ]"

echo "== 2. Release 编译 =="
if $GRADLE assembleRelease >/tmp/skipper-smoke-build.log 2>&1; then
  ok "assembleRelease 构建通过"
else
  no "assembleRelease 构建失败（见 /tmp/skipper-smoke-build.log）"
  tail -40 /tmp/skipper-smoke-build.log
fi
chk "APK 存在" "[ -s '$APK' ]"

echo "== 3. 包体关键断言 =="
if [ -x "$AAPT2" ] && [ -s "$APK" ]; then
  BADGING=$("$AAPT2" dump badging "$APK" 2>/dev/null | head -1)
  echo "  $BADGING"
  if printf '%s' "$BADGING" | grep -q "versionCode='14'"; then ok "versionCode=14"; else no "versionCode=14"; fi
  if printf '%s' "$BADGING" | grep -q "versionName='0.3.1-m11.1'"; then ok "versionName=0.3.1-m11.1"; else no "versionName=0.3.1-m11.1"; fi
  if printf '%s' "$BADGING" | grep -q "name='com.wumisc.skipper'"; then ok "包名 com.wumisc.skipper"; else no "包名 com.wumisc.skipper"; fi
  chk "未申请 INTERNET" "! \"$AAPT2\" dump badging '$APK' 2>/dev/null | grep -q 'android.permission.INTERNET'"
  chk "未申请 QUERY_ALL_PACKAGES" "! \"$AAPT2\" dump badging '$APK' 2>/dev/null | grep -q 'QUERY_ALL_PACKAGES'"
fi

XML_HIT=0
for f in $(unzip -Z1 "$APK" 2>/dev/null | grep -E '^res/[^/]+\.xml$'); do
  if "$AAPT2" dump xmltree "$APK" --file "$f" 2>/dev/null | grep -q 'isAccessibilityTool(0x01010641)=true'; then
    XML_HIT=1; break
  fi
done
chk "无障碍配置 isAccessibilityTool=true" "[ $XML_HIT -eq 1 ]"

echo
echo "========== APK 冒烟: PASS=$PASS FAIL=$FAIL =========="
[ $FAIL -eq 0 ]
