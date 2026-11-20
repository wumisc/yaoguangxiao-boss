#!/usr/bin/env python3
"""生成模块的在线更新清单（manifest.txt）。

只做一件事：把 lists/ 里要对外同步的三个文件，算 sha256 和有效行数，
再配上当前 versionCode。模块端只认这份清单，不认别的格式。
"""
from __future__ import annotations

import hashlib
import re
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD = ROOT / "姚广孝BOSS"
LISTS = ["base.txt", "boost.txt", "white.txt"]


def effective_lines(path: Path) -> int:
    n = 0
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        n += 1
    return n


def version_code() -> int:
    prop = (MOD / "module.prop").read_text(encoding="utf-8")
    m = re.search(r"^versionCode=(\d+)", prop, re.M)
    return int(m.group(1)) if m else 0


def main() -> int:
    out = MOD / "updates" / "manifest.txt"
    out.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# 姚广孝 BOSS 规则更新清单（模块端按行解析，不做 JSON）",
        f"# generated={datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"version={version_code()}",
    ]
    for name in LISTS:
        p = MOD / "lists" / name
        if not p.exists():
            print(f"缺文件 {p}", file=sys.stderr)
            return 1
        digest = hashlib.sha256(p.read_bytes()).hexdigest()
        lines.append(f"{name} {effective_lines(p)} sha256={digest}")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(out.read_text(encoding="utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
