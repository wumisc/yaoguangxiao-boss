#!/bin/bash
# 广告域名学习器靶场：不 root 也能把「捞域名 / 白名单 / 阈值 / 不自动生效」全查一遍
set -u
cd "$(dirname "$0")/.."
PASS=0; FAIL=0
ok(){ PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
no(){ FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
chk(){ if eval "$2"; then ok "$1"; else no "$1"; fi; }

WORK=$(mktemp -d)
MOD="$WORK/mod"
mkdir -p "$MOD/bin" "$MOD/lists" "$MOD/state" "$MOD/logs" "$WORK/bin"
cp 姚广孝BOSS/module.prop "$MOD/"
cp 姚广孝BOSS/bin/adlearn "$MOD/bin/"
cp 姚广孝BOSS/bin/adwatch "$MOD/bin/"
chmod +x "$MOD/bin/adlearn" "$MOD/bin/adwatch"
printf 'example.com\n' > "$MOD/lists/white.txt"

# 假 ygb：只记录被调用次数，不真挂载
cat > "$MOD/bin/ygb" <<'Y'
#!/system/bin/sh
echo "$@" >> "$MODDIR/state/ygb.calls"
Y
chmod +x "$MOD/bin/ygb"

# 假 APK：dex 文本里混着广告域名、主域、普通域名
cat > "$WORK/bin/unzip" <<'U'
#!/bin/sh
cat <<'T'
https://pangle.io/api/ad
gdt.qq.com
umeng.com
ii.umeng.com
api.umeng.com
qq.com
baidu.com
example.com
cdn.example.org
T
U
chmod +x "$WORK/bin/unzip"

touch "$WORK/base.apk"
cat > "$WORK/bin/pm" <<P
#!/bin/sh
[ "\$1" = "path" ] && echo "package:$WORK/base.apk"
P
chmod +x "$WORK/bin/pm"
cp "$WORK/bin/pm" "$WORK/bin2_pm" 2>/dev/null || true

export PATH="$WORK/bin:$PATH"
export MODDIR="$MOD"

# 一套只有基础工具的 PATH：用来模拟「机器上没有 unzip」
mkdir -p "$WORK/bin2"
for t in sh grep sed awk sort tr date cut head tail wc mv rm mkdir cat printf uniq; do
  p=$(command -v "$t" 2>/dev/null) && ln -sf "$p" "$WORK/bin2/$t"
done
cp "$WORK/bin/pm" "$WORK/bin2/pm"

run(){ MODDIR="$MOD" PATH="$WORK/bin:$PATH" /bin/sh "$MOD/bin/adlearn" "$@" 2>/dev/null; }
run_no_unzip(){ MODDIR="$MOD" PATH="$WORK/bin2" /bin/sh "$MOD/bin/adlearn" "$@" 2>/dev/null; }

echo "== 1. 第一次扫描：只收集，不确认"
OUT=$(run com.demo.news)
chk "候选落盘" "grep -q 'pangle.io' '$MOD/state/learn.cand'"
chk "品牌子域也收（ii.umeng.com）" "grep -q 'ii.umeng.com' '$MOD/state/learn.cand'"
chk "主域 qq.com 不收" "! cut -f1 '$MOD/state/learn.cand' | grep -qx 'qq.com'"
chk "主域 baidu.com 不收" "! cut -f1 '$MOD/state/learn.cand' | grep -qx 'baidu.com'"
chk "白名单 example.com 不收" "! cut -f1 '$MOD/state/learn.cand' | grep -qx 'example.com'"
chk "非广告域 cdn.example.org 不收" "! cut -f1 '$MOD/state/learn.cand' | grep -qx 'cdn.example.org'"
chk "一次不算确认（learned 为空）" "[ ! -s '$MOD/lists/learned.txt' ]"
chk "默认不调用 ygb apply" "[ ! -f '$MOD/state/ygb.calls' ]"

echo "== 2. 第二次扫描同一 App：达到阈值，确认"
run com.demo.news >/dev/null
chk "过阈值后进 learned" "grep -qx 'pangle.io' '$MOD/lists/learned.txt'"
chk "learned 无重复" "[ \$(sort '$MOD/lists/learned.txt' | uniq -d | wc -l) -eq 0 ]"
chk "仍不自动生效" "[ ! -f '$MOD/state/ygb.calls' ]"

echo "== 3. 换一个 App 也用同一个域名：两个 App 就确认"
run com.demo.shop >/dev/null
chk "第二个 App 的候选也落了" "grep -q 'com.demo.shop' '$MOD/state/learn.cand'"

echo "== 4. 显式自动生效开关"
touch "$MOD/state/learn_auto"
run com.demo.news >/dev/null
chk "开关打开后调用 apply" "grep -q 'apply' '$MOD/state/ygb.calls'"
rm -f "$MOD/state/learn_auto"

echo "== 5. 学习器不碰白名单"
chk "白名单仍在" "grep -q 'example.com' '$MOD/lists/white.txt'"
chk "learned 里没有白名单域名" "! grep -q 'example.com' '$MOD/lists/learned.txt'"

echo "== 6. 没有 unzip 的机器：优雅退出，不写垃圾"
rm -f "$WORK/bin/unzip"
OUT=$(run_no_unzip com.demo.news)
chk "输出 NO-UNZIP" "echo \"\$OUT\" | grep -q 'NO-UNZIP'"

# 第 6 节把 unzip 删了，这里补回来，后面还要用
cat > "$WORK/bin/unzip" <<'U'
#!/bin/sh
cat <<'T'
https://pangle.io/api/ad
gdt.qq.com
umeng.com
ii.umeng.com
api.umeng.com
qq.com
baidu.com
example.com
cdn.example.org
T
U
chmod +x "$WORK/bin/unzip"

echo "== 7. 超大安装包：直接跳过，不把手机烘热"
mkdir -p "$WORK/bin4"
truncate -s 200M "$WORK/big.apk" 2>/dev/null || dd if=/dev/zero of="$WORK/big.apk" bs=1M count=200 2>/dev/null
cat > "$WORK/bin4/pm" <<P2
#!/bin/sh
[ "\$1" = "path" ] && echo "package:$WORK/big.apk"
P2
chmod +x "$WORK/bin4/pm"
: > "$MOD/logs/learn.log"
MODDIR="$MOD" PATH="$WORK/bin4:$WORK/bin:$PATH" /bin/sh "$MOD/bin/adlearn" com.big.app >/dev/null 2>&1
chk "日志写明超过 150MB" "grep -q '超过 150MB' '$MOD/logs/learn.log'"
chk "大包记入 seen 不再重扫" "grep -qx 'com.big.app' '$MOD/state/learn.seen'"

echo "== 8. 自动应用限流：1800 秒内不重复重建 hosts"
rm -f "$MOD/state/learn.applied" "$MOD/state/learn.apply_at" "$MOD/state/ygb.calls"
touch "$MOD/state/learn_auto"
run com.demo.throttle1 >/dev/null
chk "第一次会 apply" "[ -f '$MOD/state/ygb.calls' ] && grep -q 'apply' '$MOD/state/ygb.calls'"
run com.demo.throttle2 >/dev/null
chk "限流期内不重复 apply" "[ \$(grep -c 'apply' '$MOD/state/ygb.calls') -eq 1 ]"
rm -f "$MOD/state/learn_auto"

echo
echo "========== 学习器靶场: PASS=$PASS FAIL=$FAIL =========="
rm -rf "$WORK"
[ $FAIL -eq 0 ]
