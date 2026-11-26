#!/bin/bash
# P0 靶场：拆旧 adwatch、三开关、导入校验、开机守护轻量化
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ -f "$ROOT/姚广孝BOSS/module.prop" ]; then
  SRC="$ROOT/姚广孝BOSS"
else
  SRC="$ROOT"
fi
cd "$ROOT"
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
no(){ FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
chk(){ if eval "$2"; then ok "$1"; else no "$1"; fi; }
eq(){ if [ "$2" = "$3" ]; then ok "$1"; else no "$1（实际=$2 期望=$3）"; fi; }

WORK=$(mktemp -d)
MOD="$WORK/mod"
mkdir -p "$MOD"/{bin,lists,state,logs,build}
cp "$SRC/module.prop" "$MOD/"
cp "$SRC/bin/ygb" "$MOD/bin/"
cp "$SRC/bin/adwatch" "$MOD/bin/"
cp "$SRC/service.sh" "$MOD/"
cp "$SRC/customize.sh" "$MOD/"
cp "$SRC/lists/base.txt" "$MOD/lists/"
cp "$SRC/lists/boost.txt" "$MOD/lists/"
cp "$SRC/lists/white.txt" "$MOD/lists/"
: > "$MOD/lists/user.txt"
: > "$MOD/lists/learned.txt"
chmod +x "$MOD/bin/ygb" "$MOD/bin/adwatch"

export MODDIR="$MOD"
Y(){ /bin/sh "$MOD/bin/ygb" "$@"; }
J(){ python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get(sys.argv[1],""))' "$1"; }

echo "== 1. 版本与默认开关 =="
eq "module.prop v1.8.2" "$(sed -n 's/^version=//p' "$MOD/module.prop")" "v1.8.2"
eq "versionCode 10084" "$(sed -n 's/^versionCode=//p' "$MOD/module.prop")" "10084"
Y build >/dev/null 2>&1
S=$(Y ui-status)
eq "默认探针开" "$(printf '%s' "$S" | J probe)" "1"
eq "默认深度补扫开" "$(printf '%s' "$S" | J deep)" "1"
eq "默认自动写入关" "$(printf '%s' "$S" | J learnAuto)" "0"
eq "默认自动导入开" "$(printf '%s' "$S" | J importAuto)" "1"
eq "默认 App 学习引擎未接入" "$(printf '%s' "$S" | J appReady)" "0"
eq "规则数仍为 15615" "$(printf '%s' "$S" | J rules)" "15615"

echo "== 2. 三个开关互不冒充 =="
Y learn-probe-off >/dev/null
S=$(Y ui-status); eq "关探针后 probe=0" "$(printf '%s' "$S" | J probe)" "0"
eq "关探针不影响 deep" "$(printf '%s' "$S" | J deep)" "1"
Y learn-deep-off >/dev/null
S=$(Y ui-status); eq "关深度后 deep=0" "$(printf '%s' "$S" | J deep)" "0"
eq "关深度不影响 probe=0" "$(printf '%s' "$S" | J probe)" "0"
Y learn-probe-on >/dev/null
S=$(Y ui-status); eq "开探针后 probe=1" "$(printf '%s' "$S" | J probe)" "1"
eq "开探针不把 deep 带开" "$(printf '%s' "$S" | J deep)" "0"
Y learn-deep-on >/dev/null
S=$(Y ui-status); eq "开深度后 deep=1" "$(printf '%s' "$S" | J deep)" "1"
Y learn-auto-on >/dev/null
S=$(Y ui-status); eq "自动写入开" "$(printf '%s' "$S" | J learnAuto)" "1"
Y learn-auto-off >/dev/null
eq "自动写入关" "$(printf '%s' "$(Y ui-status)" | J learnAuto)" "0"

echo "== 3. learn-mode 兼容映射 =="
Y learn-mode off >/dev/null
S=$(Y ui-status); eq "mode off -> probe 0" "$(printf '%s' "$S" | J probe)" "0"
eq "mode off -> deep 0" "$(printf '%s' "$S" | J deep)" "0"
Y learn-mode manual >/dev/null
S=$(Y ui-status); eq "mode manual -> probe 1" "$(printf '%s' "$S" | J probe)" "1"
eq "mode manual -> deep 0" "$(printf '%s' "$S" | J deep)" "0"
Y learn-mode charging >/dev/null
S=$(Y ui-status); eq "mode charging -> deep 1" "$(printf '%s' "$S" | J deep)" "1"

echo "== 4. 导入校验 =="
APPDIR="$WORK/applearn"; mkdir -p "$APPDIR"
cat > "$APPDIR/learned.tsv" <<'EOF'
ok.example.com	pangle	com.demo	ui	0.9	1
ad.white.com	gdt	com.demo	ui	0.9	2
foo.qq.com	pangle	com.demo	ui	0.9	3
bad_domain	pangle	com.demo	ui	0.9	4
dup.example.com	gdt	com.demo	manifest	0.8	5
dup.example.com	gdt	com.demo	manifest	0.8	6
EOF
printf 'ad.white.com\n' > "$MOD/lists/white.txt"
APP_LEARN_DIR="$APPDIR" Y import-learn >/dev/null
chk "正常域名导入" "grep -qx 'ok.example.com' '$MOD/lists/learned.txt'"
chk "白名单不导入" "! grep -qx 'ad.white.com' '$MOD/lists/learned.txt'"
chk "保护域不导入" "! grep -qx 'foo.qq.com' '$MOD/lists/learned.txt'"
chk "无点域名不导入" "! grep -qx 'bad_domain' '$MOD/lists/learned.txt'"
chk "重复只留一条" "[ \$(grep -c '^dup.example.com$' '$MOD/lists/learned.txt') -eq 1 ]"
eq "导入后已确认 2 条" "$(printf '%s' "$(Y ui-status)" | J learned)" "2"
OUT=$(APP_LEARN_DIR="$APPDIR" Y import-learn)
chk "二次导入幂等" "printf '%s' '$OUT' | grep -q '新增 0 条'"
eq "二次导入后仍 2 条" "$(printf '%s' "$(Y ui-status)" | J learned)" "2"
chk "导入时间已落盘" "[ -s '$MOD/state/last_import' ]"

echo "== 5. 旧 adwatch 必须停用 =="
OUT=$(MODDIR="$MOD" /bin/sh "$MOD/bin/adwatch" 2>&1); RC=$?
eq "无 legacy 开关时退出码 0" "$RC" "0"
chk "提示已停用" "printf '%s' '$OUT' | grep -q '已停用'"
chk "不会写 adwatch.pid" "[ ! -f '$MOD/state/adwatch.pid' ]"
chk "service.sh 不再启动 adwatch" "! grep -q 'start_adwatch' '$MOD/service.sh'"
chk "service.sh 守护用 mounted" "grep -q 'ygb\" mounted' '$MOD/service.sh'"

echo "== 6. customize 版本种子 =="
rm -f "$MOD/state/rules.ver"
( MODPATH="$MOD"; . "$MOD/customize.sh" >/dev/null 2>&1 )
eq "rules.ver 已写入 10084" "$(cat "$MOD/state/rules.ver" 2>/dev/null)" "10084"

echo "== 7. mounted 轻量命令 =="
Y mounted >/dev/null 2>&1; RC=$?
eq "未挂载时退出码 1" "$RC" "1"
chk "输出 no" "Y mounted 2>/dev/null | grep -q no"

echo
echo "========== P0 靶场: PASS=$PASS FAIL=$FAIL =========="
rm -rf "$WORK"
[ $FAIL -eq 0 ]
