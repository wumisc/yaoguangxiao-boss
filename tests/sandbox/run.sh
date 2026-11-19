#!/bin/bash
# 姚广孝 BOSS · 靶场启动器
# 用 user namespace + chroot 造一个可真实 bind mount 的假安卓根
set -u
PROJ="$(cd "$(dirname "$0")/../.." && pwd)"
MOD="$PROJ/姚广孝BOSS"
ROOT=/tmp/ygb-range/root

rm -rf /tmp/ygb-range
mkdir -p "$ROOT"/{system/bin,system/etc,system/xbin,data/adb/modules,data/adb/ksu/bin,usr,bin,lib,lib64,etc,proc,dev,sys,tests}

mount --bind /usr "$ROOT/usr"      || exit 90
mount --bind /bin "$ROOT/bin"      || exit 91
[ -d /lib ] && mount --bind /lib "$ROOT/lib"
[ -d /lib64 ] && mount --bind /lib64 "$ROOT/lib64"
mkdir -p "$ROOT/etc/alternatives" && mount --bind /etc/alternatives "$ROOT/etc/alternatives" 2>/dev/null || true
mount -t proc none "$ROOT/proc" 2>/dev/null || true
mount -t tmpfs none "$ROOT/dev" 2>/dev/null || true
for d in null:1:3 zero:1:5 tty:5:0 random:1:8 urandom:1:9; do
  mknod "$ROOT/dev/${d%%:*}" c "$(echo $d | cut -d: -f2)" "$(echo $d | cut -d: -f3)" 2>/dev/null || true
done

ln -sf /bin/sh "$ROOT/system/bin/sh"

# ---- 靶场专用 mount/umount 垫片（无 /proc 环境下走 mount(2)/umount2 系统调用，可观测） ----
cat > "$ROOT/system/bin/_mnt.py" <<'PY'
#!/usr/bin/env python3
import sys, os, ctypes
LOG = "/data/adb/test/mount.log"
os.makedirs(os.path.dirname(LOG), exist_ok=True)
libc = ctypes.CDLL("libc.so.6", use_errno=True)
libc.mount.argtypes = [ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_ulong, ctypes.c_void_p]
libc.umount2.argtypes = [ctypes.c_char_p, ctypes.c_int]

def active():
    order, dst2src = [], {}
    try:
        for line in open(LOG):
            p = line.rstrip("\n").split("\t")
            if p[0] == "M" and p[3] == "0":
                dst2src[p[2]] = p[1]; order.append(p[2])
            elif p[0] == "U" and p[2] == "0" and p[1] in dst2src:
                del dst2src[p[1]]
    except FileNotFoundError:
        pass
    seen = []
    for d in order:
        if d in dst2src and d not in seen:
            seen.append(d)
    return [(dst2src[d], d) for d in seen]

prog = os.path.basename(sys.argv[0])
args = sys.argv[1:]

if prog == "umount":
    detach = "-l" in args
    args = [a for a in args if not a.startswith("-")]
    tgt = args[-1] if args else ""
    rc = libc.umount2(tgt.encode(), 2 if detach else 0)
    open(LOG, "a").write("U\t%s\t%d\n" % (tgt, rc))
    if rc != 0:
        sys.stderr.write("umount: %s\n" % os.strerror(ctypes.get_errno()))
        sys.exit(1)
    sys.exit(0)

# mount
if not args or all(a.startswith("-") for a in args):
    for src, dst in active():
        print("%s on %s type none (rw,bind)" % (src, dst))
    sys.exit(0)

opt = []
while args and args[0].startswith("-"):
    f = args.pop(0)
    if f == "-o" and args:
        opt += args.pop(0).split(",")
src = args[0] if args else ""
dst = args[1] if len(args) > 1 else ""
rc = libc.mount(src.encode(), dst.encode(), None, 4096, None)   # MS_BIND
open(LOG, "a").write("M\t%s\t%s\t%d\n" % (src, dst, rc))
if rc != 0:
    sys.stderr.write("mount: %s\n" % os.strerror(ctypes.get_errno()))
    sys.exit(1)
sys.exit(0)
PY
chmod 755 "$ROOT/system/bin/_mnt.py"
ln -sf /system/bin/_mnt.py "$ROOT/system/bin/mount"
ln -sf /system/bin/_mnt.py "$ROOT/system/bin/umount"


printf '127.0.0.1 localhost\n::1 localhost\n' > "$ROOT/system/etc/hosts"
cp "$ROOT/system/etc/hosts" "$ROOT/etc.hosts.orig"

cp -a "$MOD" "$ROOT/data/adb/modules/yaoguangxiao_boss"
cp "${SUITE_FILE:-$PROJ/tests/sandbox/suite.sh}" "$ROOT/tests/suite.sh"

PATH=/system/bin:/usr/bin:/bin:/usr/sbin:/sbin chroot "$ROOT" /system/bin/sh /tests/suite.sh
RC=$?
echo "==== 靶场退出码: $RC ===="
exit $RC
