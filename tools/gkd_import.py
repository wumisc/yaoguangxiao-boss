#!/usr/bin/env python3
"""把公开 GKD 订阅里的「开屏广告」规则，提炼成本工程的专项规则包。

只做一件事：从选择器里抽出「文字」这一维，转成文字正则 + 顶部/尺寸约束。
GKD 的完整选择器（id、层级、颜色）不使用，所以不会照搬别人的判定逻辑，
只借用「哪些包的开屏按钮长什么样」这一事实。

数据不进仓库：输出写到 assets，脚本本身进仓库；换源只改 SOURCES。
"""
from __future__ import annotations

import argparse
import json
import json5
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "skipper/app/src/androidMain/assets/rules/imported.tsv"
REPORT = ROOT / "docs/IMPORTED-RULES.md"

SOURCES = [
    ("gkd-kit/subscription", "https://raw.githubusercontent.com/gkd-kit/subscription/main/dist/gkd.json5", "MIT"),
    ("Adpro-Team/GKD_subscription", "https://raw.githubusercontent.com/Adpro-Team/GKD_subscription/main/dist/Adpro_gkd.json5", "SATA-2.0（需 star + 致谢）"),
]

# 与引擎一致的倒计时尾巴：数字 + 可省的 s/S/秒
COUNTDOWN = r"(?:\s*[0-9]{1,2}\s*(?:s|S|秒)?)?"

SKIP_WORDS = ("跳过", "关闭广告", "关闭此广告", "Skip")
DANGER = ("登录", "注册", "绑定", "授权", "支付", "开通", "充值", "下载", "安装",
          "领取", "预约", "实名", "同意", "确认", "更新", "升级")
BLOCKED_PKG = ("com.android.systemui", "com.android.settings", "com.android.permissioncontroller",
               "com.android.packageinstaller", "com.google.android.packageinstaller",
               "com.android.inputmethod", "com.android.phone", "com.android.mms",
               "com.android.contacts")
BLOCKED_KEY = ("bank", "pay", "alipay", "gov", "tax", "insur", "fund", "credit", "loan",
               "checkout", "wallet", "securities")

TEXT_RE = re.compile(r'text(!?=|\*=|\^=|\$=)"([^"]*)"')
ID_RE = re.compile(r'id="([^"]*)"')

# 控件 id 里允许出现的「跳过」语义；太通用的名字（close/cancel）一律不收，避免误点。
ID_TOKENS = ("skip", "jump", "ad_close", "close_ad", "adskip", "ad_skip", "tiao")
ID_DANGER = ("login", "pay", "register", "order", "buy", "vip", "update", "download")


def esc(value: str) -> str:
    return re.escape(value)


def pattern_for(op: str, value: str) -> str | None:
    core = value.strip()
    if not core or len(core) > 12:
        return None
    if any(w in core for w in SKIP_WORDS):
        pass
    elif core in ("关闭", "关闭广告"):
        pass
    else:
        return None
    if any(w in core for w in DANGER):
        return None
    if op == "=":
        return f"^\\s*{esc(core)}{COUNTDOWN}\\s*$"
    if op in ("*=", "^="):
        return f"^\\s*{esc(core)}{COUNTDOWN}\\s*$"
    if op == "$=":
        return f"^\\s*{esc(core)}{COUNTDOWN}\\s*$"
    return None


def id_pattern(raw: str) -> str | None:
    name = raw.split(":id/")[-1].strip()
    if not name or len(name) > 48:
        return None
    low = name.lower()
    adish = any(t in low for t in ("ad", "splash", "interstitial"))
    if not any(t in low for t in ID_TOKENS) and not (adish and any(t in low for t in ("close", "cancle", "cancel"))):
        return None
    if any(d in low for d in ID_DANGER):
        return None
    return f"^\\s*{esc(name)}\\s*$"


def top_ratio(rule) -> float | None:
    pos = rule.get("position")
    if not isinstance(pos, dict):
        return None
    raw = str(pos.get("top") or "")
    m = re.search(r"\*\s*([0-9.]+)", raw)
    if not m:
        return None
    try:
        r = float(m.group(1))
    except ValueError:
        return None
    if r < 0 or r > 1.2:
        return None
    return r


def is_blocked(pkg: str) -> bool:
    if pkg in BLOCKED_PKG or pkg.startswith("com.android."):
        return True
    low = pkg.lower()
    return any(k in low for k in BLOCKED_KEY)


def load(path: Path):
    return json5.loads(path.read_text(encoding="utf-8"))


def collect(doc, stats):
    cats = {c.get("key"): c.get("name") for c in doc.get("categories", []) or [] if isinstance(c, dict)}
    for app in doc.get("apps", []) or []:
        if not isinstance(app, dict):
            continue
        pkg = str(app.get("id") or "")
        if not pkg or is_blocked(pkg):
            stats["skipped_pkg"] += 1
            continue
        title = str(app.get("name") or pkg)
        for group in app.get("groups", []) or []:
            if not isinstance(group, dict):
                continue
            if cats.get(group.get("key")) != "开屏广告":
                continue
            stats["splash_groups"] += 1
            top = 0.5
            for rule in group.get("rules", []) or []:
                if not isinstance(rule, dict):
                    continue
                r = top_ratio(rule)
                if r is not None:
                    top = max(top, min(0.96, r + 0.14))
                sels = []
                for key in ("matches", "anyMatches"):
                    v = rule.get(key)
                    if isinstance(v, str):
                        sels.append(v)
                    elif isinstance(v, list):
                        sels += [x for x in v if isinstance(x, str)]
                for sel in sels:
                    for op, value in TEXT_RE.findall(sel):
                        p = pattern_for(op, value)
                        if p:
                            entry = apps.setdefault(pkg, {"title": title, "top": top, "patterns": [], "ids": []})
                            entry["top"] = max(entry["top"], top)
                            if p not in entry["patterns"]:
                                entry["patterns"].append(p)
                            stats["patterns"] += 1
                    for raw in ID_RE.findall(sel):
                        ip = id_pattern(raw)
                        if ip:
                            entry = apps.setdefault(pkg, {"title": title, "top": top, "patterns": [], "ids": []})
                            entry["top"] = max(entry["top"], top)
                            if ip not in entry["ids"]:
                                entry["ids"].append(ip)
                            stats["ids"] += 1


apps: dict[str, dict] = {}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=str(ROOT / "tools/imported"), help="本地 json5 缓存目录")
    ap.add_argument("--offline", action="store_true")
    args = ap.parse_args()

    stats = {"sources": 0, "splash_groups": 0, "patterns": 0, "ids": 0, "skipped_pkg": 0}
    cache = Path(args.cache)
    cache.mkdir(parents=True, exist_ok=True)
    for name, url, license_ in SOURCES:
        path = cache / (name.replace("/", "_") + ".json5")
        if not path.exists():
            if args.offline:
                print(f"缺缓存 {path}，跳过", file=sys.stderr)
                continue
            import urllib.request
            print(f"下载 {url}")
            path.write_bytes(urllib.request.urlopen(url, timeout=60).read())
        doc = load(path)
        stats["sources"] += 1
        collect(doc, stats)
        print(f"{name}: apps={len(doc.get('apps', []))} license={license_}")

    rules = []
    for pkg, info in sorted(apps.items()):
        pats = info["patterns"][:8]
        ids = info.get("ids", [])[:6]
        if not pats and not ids:
            continue
        rules.append((pkg, info["title"].replace("\t", " "), pats, ids, info["top"]))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as f:
        f.write("# pkg\ttitle\ttextPatterns(\x1f)\tviewIdPatterns(\x1f)\ttopRatio\n")
        for pkg, title, pats, ids, top in rules:
            f.write(f"{pkg}\t{title}\t" + "\x1f".join(pats) + "\t" + "\x1f".join(ids) + f"\t{top:.2f}\n")

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# 专项规则包（从公开 GKD 订阅提炼）",
        "",
        f"生成时间：{__import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M')}",
        "",
        "| 来源 | 许可 | 用途 |",
        "|---|---|---|",
    ]
    for name, url, license_ in SOURCES:
        lines.append(f"| `{name}` | {license_} | 只取「开屏广告」类的文字与控件 id |")
    lines += [
        "",
        f"- 规则包：{len(rules)} 个应用，文字正则 {sum(len(r[2]) for r in rules)} 条，id 正则 {sum(len(r[3]) for r in rules)} 条",
        f"- 扫描到开屏规则组 {stats['splash_groups']} 个；因金融/政务/系统类而跳过的应用 {stats['skipped_pkg']} 个",
        "",
        "## 只借了什么",
        "",
        "GKD 的规则是「选择器」：按 id、层级、颜色、位置定位控件，点一下。",
        "本工程是纯文字 + 控件 id + 几何三种信号的轻量引擎，所以只提炼了两样东西：",
        "",
        "1. **哪些包有开屏跳过按钮**（包名清单）；",
        "2. **那个按钮上写什么字 / 叫什么 id**（转成正则）。",
        "",
        "层级、颜色、坐标一律不用，也不复制别人的执行流程。这既是能力边界，也是风险边界——",
        "我们只做「点跳过」，不做屏蔽、不做拦截、不改别人的界面。",
        "",
        "## 许可与致谢",
        "",
        "- `gkd-kit/subscription`：MIT，可直接使用，保留出处。",
        "- `Adpro-Team/GKD_subscription`：SATA-2.0，要求使用前给作者点 star 并致谢。已在此致谢。",
        "- 未收录 AIsouler 订阅：仓库未声明许可证，默认保留所有权利，不碰。",
        "",
        "## 数据不进仓库",
        "",
        "`tools/gkd_import.py` 进仓库，生成的 `imported.tsv` 只落在构建产物与 APK 里，",
        "`.gitignore` 已排除。这样公开的是「方法」，不是别人的「数据」。",
    ]
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"包 {len(rules)} 个，文字正则 {sum(len(r[2]) for r in rules)} 条，id 正则 {sum(len(r[3]) for r in rules)} 条 → {OUT.relative_to(ROOT)}")
    print(json.dumps(stats, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
