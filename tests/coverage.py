#!/usr/bin/env python3
"""广告覆盖率 / 功能误伤 体检工具

用法: python3 tests/coverage.py [模块目录]
输出: 生效域名数、广告样本命中率、功能样本误伤数、白名单命中数
"""
import os, sys, subprocess, json

MOD = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '姚广孝BOSS')
HERE = os.path.dirname(os.path.abspath(__file__))

def load_set(p):
    return set(l.strip().lower() for l in open(p, encoding='utf-8') if l.strip() and not l.startswith('#'))

hosts = os.path.join(MOD, 'build', 'hosts')
subprocess.run(['sh', os.path.join(MOD, 'bin', 'ygb'), 'build'], capture_output=True)
blocked = set()
for line in open(hosts, encoding='utf-8', errors='ignore'):
    p = line.split()
    if len(p) == 2 and p[0] in ('0.0.0.0', '::1'):
        blocked.add(p[1].lower())

ads = load_set(os.path.join(HERE, 'sets', 'ads.txt'))
func = load_set(os.path.join(HERE, 'sets', 'func.txt'))
white = load_set(os.path.join(MOD, 'lists', 'white.txt'))
wl_exact = {w for w in white if not w.startswith('*.')}
wl_sfx = {w[2:] for w in white if w.startswith('*.')}

ad_hit = sorted(d for d in ads if d in blocked)
ad_miss = sorted(d for d in ads if d not in blocked)
func_hit = sorted(d for d in func if d in blocked)
wl_hit = [d for d in blocked if d in wl_exact or any(d == s or d.endswith('.' + s) for s in wl_sfx)]

print(f'生效域名      : {len(blocked)}')
print(f'广告样本覆盖率: {len(ad_hit)}/{len(ads)} = {len(ad_hit)/len(ads)*100:.1f}%')
print(f'功能样本误伤  : {len(func_hit)}/{len(func)}')
print(f'白名单命中    : {len(wl_hit)}')
if ad_miss:
    print('未覆盖广告域  : ' + ', '.join(ad_miss))
if func_hit:
    print('误伤明细      : ' + ', '.join(func_hit))
if wl_hit:
    print('白名单被拦    : ' + ', '.join(wl_hit[:20]))
sys.exit(0 if (not func_hit and not wl_hit) else 1)
