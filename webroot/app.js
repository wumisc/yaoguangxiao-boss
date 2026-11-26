/* 姚广孝 BOSS · KSU WebUI 控制面板 */
(function () {
  'use strict';

  var MODDIR = '/data/adb/modules/yaoguangxiao_boss';
  var CMD = '/system/bin/sh ' + MODDIR + '/bin/ygb';
  var busy = false;
  var bridgeLabel = '';
  var diagLog = [];

  var $ = function (id) { return document.getElementById(id); };

  function logDiag(s) {
    diagLog.push(s);
    if (diagLog.length > 14) diagLog.shift();
  }

  function pick(v) {
    if (v == null) return '';
    if (typeof v === 'string') return v;
    if (typeof v === 'object') return String(v.stdout || v.output || '');
    return String(v);
  }

  /* ---------------- 单次尝试：兼容回调式 / Promise 式 / 同步返回 ---------------- */
  function attempt(runner, label, timeoutMs) {
    return new Promise(function (resolve, reject) {
      var settled = false;
      var timer = setTimeout(function () {
        if (!settled) { settled = true; logDiag(label + ' -> 超时'); reject(new Error(label + ' 超时')); }
      }, timeoutMs);

      function ok(v) {
        if (settled) return;
        settled = true; clearTimeout(timer); resolve(pick(v));
      }
      function bad(e) {
        if (settled) return;
        settled = true; clearTimeout(timer); reject(e || new Error(label + ' 失败'));
      }

      try {
        var ret = runner(ok, bad);
        if (ret && typeof ret.then === 'function') {
          ret.then(function (v) { ok(v); }, bad);
        } else if (typeof ret === 'string') {
          ok(ret);
        } else if (ret && typeof ret === 'object' && ('stdout' in ret || 'output' in ret)) {
          ok(ret);
        }
      } catch (e) {
        bad(e);
      }
    });
  }

  /* ---------------- 策略表：不同 KSU 版本桥接 API 不同 ---------------- */
  function strategies(cmd) {
    return [
      {
        label: 'ksu.exec(cmd, cb)',
        run: function (ok) {
          if (!window.ksu || typeof ksu.exec !== 'function') throw new Error('ksu.exec 不可用');
          return ksu.exec(cmd, function (errno, stdout, stderr) {
            ok(String(stdout || '') + String(stderr || ''));
          });
        }
      },
      {
        label: 'ksu.exec(cmd)',
        run: function () {
          if (!window.ksu || typeof ksu.exec !== 'function') throw new Error('ksu.exec 不可用');
          return ksu.exec(cmd);
        }
      },
      {
        label: 'ksu.execCommand(cmd, cb)',
        run: function (ok) {
          if (!window.ksu || typeof ksu.execCommand !== 'function') throw new Error('无 execCommand');
          return ksu.execCommand(cmd, function (errno, stdout, stderr) {
            ok(String(stdout || '') + String(stderr || ''));
          });
        }
      },
      {
        label: 'ksu.shell(cmd)',
        run: function () {
          if (!window.ksu || typeof ksu.shell !== 'function') throw new Error('无 shell');
          return ksu.shell(cmd);
        }
      },
      {
        label: 'ksu.run(cmd)',
        run: function () {
          if (!window.ksu || typeof ksu.run !== 'function') throw new Error('无 run');
          return ksu.run(cmd);
        }
      }
    ];
  }

  var cachedKind = -1;

  function exec(command) {
    var list = strategies(command);

    function runChain(from) {
      if (from >= list.length) {
        return Promise.reject(new Error('所有桥接方式均不可用'));
      }
      var s = list[from];
      return attempt(s.run, s.label, 1600).then(function (out) {
        if (out && out.indexOf('YGB_') >= 0) {
          cachedKind = from;
          bridgeLabel = s.label;
        }
        return out;
      }, function () {
        return runChain(from + 1);
      });
    }

    // 只缓存“通道种类”，命令必须每次重建，否则所有请求都会跑成第一次的命令
    if (cachedKind >= 0 && list[cachedKind]) {
      var cached = list[cachedKind];
      return attempt(cached.run, cached.label, 2500)['catch'](function () {
        cachedKind = -1;
        bridgeLabel = '';
        return runChain(0);
      });
    }
    return runChain(0);
  }

  /* ---------------- 提示 / 错误条 ---------------- */
  function toast(msg, isErr) {
    var el = document.createElement('div');
    el.className = 'toast' + (isErr ? ' err' : '');
    el.textContent = msg;
    document.body.appendChild(el);
    requestAnimationFrame(function () { el.classList.add('show'); });
    setTimeout(function () {
      el.classList.remove('show');
      setTimeout(function () { el.remove(); }, 250);
    }, 1900);
    if (window.ksu && typeof ksu.toast === 'function') {
      try { ksu.toast(String(msg)); } catch (e) { /* ignore */ }
    }
  }

  function showError(title, detail) {
    var bar = $('errorBar');
    if (!bar) return;
    var env = [];
    env.push('window.ksu: ' + (window.ksu ? '存在' : '不存在'));
    if (window.ksu) {
      try {
        env.push('可用方法: ' + Object.keys(window.ksu).join(', '));
      } catch (e) { env.push('方法枚举失败'); }
    }
    env.push('页面: ' + (location.href || ''));
    env.push('尝试链: ' + (diagLog.join(' | ') || '无'));
    if (bridgeLabel) env.push('当前通道: ' + bridgeLabel);

    bar.innerHTML = '';
    var h = document.createElement('div');
    h.className = 'eb-title';
    h.textContent = title;
    bar.appendChild(h);

    var d = document.createElement('pre');
    d.className = 'eb-detail';
    d.textContent = (detail ? detail + '\n\n' : '') + env.join('\n');
    bar.appendChild(d);

    var row = document.createElement('div');
    row.className = 'eb-actions';

    var retry = document.createElement('button');
    retry.className = 'btn primary';
    retry.textContent = '重新连接';
    retry.addEventListener('click', function () {
      cachedKind = -1;
      diagLog = [];
      boot();
    });

    var copy = document.createElement('button');
    copy.className = 'btn';
    copy.textContent = '复制诊断信息';
    copy.addEventListener('click', function () {
      var txt = title + '\n' + d.textContent;
      try {
        if (navigator.clipboard) navigator.clipboard.writeText(txt);
        toast('诊断信息已复制');
      } catch (e) { toast('请手动长按复制', true); }
    });

    row.appendChild(retry);
    row.appendChild(copy);
    bar.appendChild(row);
    bar.classList.remove('hidden');
  }

  function hideError() {
    var bar = $('errorBar');
    if (bar) bar.classList.add('hidden');
  }

  /* ---------------- 渲染 ---------------- */
  var state = {
    on: false, mounted: false, boostOn: true, loaded: false,
    base: 0, boost: 0, learned: 0, cand: 0, learnAuto: false,
    probe: true, deep: true, importAuto: true, appReady: false,
    queue: 0, lastImport: '-'
  };

  function render() {
    var sw = $('toggle');
    if (sw) sw.classList.toggle('on', state.on);

    var pill = $('statePill');
    if (pill) {
      pill.textContent = state.on ? (state.mounted ? '拦截生效中' : '已开启·待挂载') : '已暂停';
      pill.className = 'pill ' + (state.on ? 'pill-on' : 'pill-off');
    }

    var mh = $('mountHint');
    if (mh) {
      mh.textContent = state.on
        ? (state.mounted ? 'hosts 已挂载，广告请求正在被拦截' : '规则已开启，但 hosts 尚未挂载，可点「立即重载规则」')
        : '总开关已关闭：全部规则停用，系统 hosts 已还原（重启后仍保持关闭）';
    }

    var bt = $('boostToggle');
    if (bt) bt.classList.toggle('on', state.boostOn);

    var bh = $('boostHint');
    if (bh) {
      bh.textContent = state.boostOn
        ? '已开启：精选 ' + state.base + ' + 增强 ' + state.boost + ' 条'
        : '已关闭：仅保留精选 ' + state.base + ' 条，兼容性优先';
    }

    // 总开关关闭时，增强开关置灰（规则不参与任何拦截）
    if (bt) bt.disabled = !state.on;

    var pt = $('probeToggle');
    if (pt) pt.classList.toggle('on', state.probe);
    var dt = $('deepToggle');
    if (dt) dt.classList.toggle('on', state.deep);
    var lt = $('learnToggle');
    if (lt) lt.classList.toggle('on', state.learnAuto);

    var lh = $('learnHint');
    if (lh) {
      lh.textContent =
        '探针 ' + (state.probe ? '开' : '关') +
        ' · 深度补扫 ' + (state.deep ? '开（仅充电空闲）' : '关') +
        ' · 已确认 ' + state.learned + ' 条 · 候选 ' + state.cand +
        ' · 队列 ' + state.queue +
        ' · App 学习引擎 ' + (state.appReady ? '就绪' : '未接入') +
        (state.lastImport && state.lastImport !== '-' ? ' · 上次导入 ' + state.lastImport : '');
    }

    var now = $('learnNowBtn');
    if (now) {
      now.disabled = !state.appReady;
      now.title = state.appReady ? '让 App 学习当前界面' : '等待 App 学习引擎（m12）';
    }
    var imp = $('importLearnBtn');
    if (imp) {
      imp.disabled = false;
      imp.title = state.appReady ? '读取 App 学习结果并应用' : 'App 学习引擎尚未产出结果，导入会提示未找到';
    }
  }

  function applyStatus(raw) {
    var line = String(raw || '').trim().split('\n').pop();
    var data = null;
    try { data = JSON.parse(line); } catch (e) { data = null; }
    if (!data) {
      showError('模块状态读取失败', '原始输出：' + (String(raw || '').slice(0, 300) || '（空）'));
      return false;
    }
    state.on = data.state === 'on';
    state.mounted = data.mounted === 'yes';
    state.boostOn = data.boostOn !== 0;
    state.base = data.base || 0;
    state.boost = data.boost || 0;
    state.learned = data.learned || 0;
    state.cand = data.cand || 0;
    state.learnAuto = data.learnAuto === 1;
    state.probe = data.probe !== 0;
    state.deep = data.deep !== 0;
    state.importAuto = data.importAuto !== 0;
    state.appReady = data.appReady === 1;
    state.queue = data.queue || 0;
    state.lastImport = data.lastImport || '-';
    state.loaded = true;
    if ($('stRules')) $('stRules').textContent = data.rules || 0;
    if ($('stBase')) $('stBase').textContent = data.base || 0;
    if ($('stBoost')) $('stBoost').textContent = data.boost || 0;
    if ($('stCustom')) $('stCustom').textContent = data.custom || 0;
    if ($('whiteCount')) $('whiteCount').textContent = ' · 已放行 ' + (data.white || 0) + ' 条';
    render();
    return true;
  }

  function renderTags(boxId, text, delKind) {
    var box = $(boxId);
    if (!box) return;
    var lines = String(text || '').split('\n').filter(function (l) {
      var s = l.trim();
      return s && s.charAt(0) !== '#';
    });
    box.innerHTML = '';
    if (!lines.length) {
      box.innerHTML = '<p class="empty">' + (delKind === 'del' ? '暂无自定义规则' : '暂无白名单') + '</p>';
      return;
    }
    lines.forEach(function (l) {
      var row = document.createElement('div');
      row.className = 'tag';
      var t = document.createElement('span');
      t.textContent = l.trim();
      var b = document.createElement('button');
      b.className = 'del';
      b.type = 'button';
      b.textContent = '×';
      b.addEventListener('click', guard(function () {
        return exec(CMD + ' ' + delKind + ' ' + l.trim()).then(function () {
          toast('已移除 ' + l.trim());
          return refreshAll();
        });
      }));
      row.appendChild(t);
      row.appendChild(b);
      box.appendChild(row);
    });
  }

  /** 学习候选：域名 / 被几个 App 用到 / 加入 or 忽略 */
  function renderLearn(text) {
    var box = $('learnList');
    if (!box) return;
    var lines = String(text || '').split('\n').filter(function (l) { return l.trim() && l.trim().charAt(0) !== '#'; });
    box.innerHTML = '';
    if (!lines.length) {
      box.innerHTML = '<p class="empty">暂无候选。多开几个 App 就会攒出来。</p>';
      return;
    }
    lines.forEach(function (l) {
      var f = l.split('\t');
      var d = (f[0] || '').trim();
      if (!d) return;
      var row = document.createElement('div');
      row.className = 'tag';
      var t = document.createElement('span');
      t.textContent = d + (f[1] ? ' · ' + f[1] + ' 个 App' : '');
      row.appendChild(t);

      var add = document.createElement('button');
      add.className = 'del';
      add.type = 'button';
      add.textContent = '＋';
      add.title = '加入自定义规则';
      add.addEventListener('click', guard(function () {
        return exec(CMD + ' add ' + d).then(function () { toast('已加入 ' + d); return refreshAll(); });
      }));
      row.appendChild(add);

      var ign = document.createElement('button');
      ign.className = 'del';
      ign.type = 'button';
      ign.textContent = '×';
      ign.title = '忽略';
      ign.addEventListener('click', guard(function () {
        return exec(CMD + ' learn-ignore ' + d).then(function () { toast('已忽略 ' + d); return refreshAll(); });
      }));
      row.appendChild(ign);

      box.appendChild(row);
    });
  }

  function refreshAll() {
    return exec(CMD + ' ui-status').then(function (out) {
      if (!applyStatus(out)) throw new Error('状态解析失败');
      hideError();
      return Promise.all([exec(CMD + ' list'), exec(CMD + ' white'), exec(CMD + ' learn-cand')]).then(function (r) {
        renderTags('customList', r[0], 'del');
        renderTags('whiteList', r[1], 'white-del');
        renderLearn(r[2]);
      });
    });
  }

  function guard(fn) {
    return function () {
      if (busy) return;
      busy = true;
      Promise.resolve()
        .then(fn)
        ['catch'](function (e) {
          toast(e && e.message ? e.message : '操作失败', true);
        })
        .then(function () { busy = false; });
    };
  }

  /* ---------------- 启动：先探桥接，再定路径 ---------------- */
  function bindLearnControls() {
    var pt = $('probeToggle');
    if (pt) pt.addEventListener('click', guard(function () {
      var target = state.probe ? ' learn-probe-off' : ' learn-probe-on';
      return exec(CMD + target).then(function () {
        toast(state.probe ? '智能探针已关' : '智能探针已开');
        return refreshAll();
      });
    }));

    var dt = $('deepToggle');
    if (dt) dt.addEventListener('click', guard(function () {
      var target = state.deep ? ' learn-deep-off' : ' learn-deep-on';
      return exec(CMD + target).then(function () {
        toast(state.deep ? '深度补扫已关' : '深度补扫已开（仅充电空闲）');
        return refreshAll();
      });
    }));

    var lt = $('learnToggle');
    if (lt) lt.addEventListener('click', guard(function () {
      var target = state.learnAuto ? ' learn-auto-off' : ' learn-auto-on';
      return exec(CMD + target).then(function () {
        return state.learnAuto ? null : exec(CMD + ' apply');
      }).then(function () {
        toast(state.learnAuto ? '已关闭自动写入' : '已开启自动写入');
        return refreshAll();
      });
    }));

    var now = $('learnNowBtn');
    if (now) now.addEventListener('click', guard(function () {
      if (!state.appReady) { toast('App 学习引擎尚未接入', true); return; }
      return exec(CMD + ' learn-now').then(function (out) {
        var okk = /OK/.test(out);
        toast(okk ? '已请求 App 学习当前界面' : (String(out || '').trim() || '请求失败'), !okk);
        return refreshAll();
      });
    }));

    var imp = $('importLearnBtn');
    if (imp) imp.addEventListener('click', guard(function () {
      return exec(CMD + ' import-learn').then(function (out) {
        var okk = /OK/.test(out);
        var text = String(out || '').trim().split('\n').pop() || '导入完成';
        toast(text, !okk);
        return refreshAll();
      });
    }));
  }

  function boot() {
    var pill = $('statePill');
    if (pill) { pill.textContent = '连接中'; pill.className = 'pill'; }

    bindLearnControls();
    exec('echo YGB_READY')
      .then(function (out) {
        if (String(out).indexOf('YGB_READY') < 0) throw new Error('桥接无回显');
        logDiag('桥接正常: ' + bridgeLabel);
        return exec('for d in /data/adb/modules/yaoguangxiao_boss /data/adb/modules_update/yaoguangxiao_boss; do [ -d "$d" ] && echo "YGB_DIR=$d"; done');
      })
      .then(function (out) {
        var m = String(out || '').match(/YGB_DIR=([^\s]+)/);
        if (m) { MODDIR = m[1]; CMD = '/system/bin/sh ' + MODDIR + '/bin/ygb'; }
        return refreshAll();
      })
      ['catch'](function (e) {
        if (pill) { pill.textContent = '连接失败'; pill.className = 'pill pill-off'; }
        var bar = $('errorBar');
        if (bar && !bar.classList.contains('hidden')) return;
        showError('未能连接模块', '原因：' + (e && e.message ? e.message : '未知'));
      });
  }

  /* ---------------- 事件绑定 ---------------- */
  function bindUI() {
    var t = $('toggle');
    if (t) t.addEventListener('click', guard(function () {
      var next = !state.on;
      return exec(CMD + (next ? ' on' : ' off')).then(function () {
        toast(next ? '拦截已开启' : '拦截已暂停');
        return refreshAll();
      });
    }));

    var bt = $('boostToggle');
    if (bt) bt.addEventListener('click', guard(function () {
      if (!state.on) { toast('总开关已关闭，请先打开总开关', true); return; }
      var next = !state.boostOn;
      return exec(CMD + (next ? ' boost-on' : ' boost-off')).then(function () {
        toast(next ? '强力模式已开启' : '强力模式已关闭');
        return refreshAll();
      });
    }));

    var add = $('addBtn');
    if (add) add.addEventListener('click', guard(function () {
      var d = cleanDomain($('domainInput').value);
      if (!d) { toast('请输入合法域名，如 ad.example.com', true); return; }
      $('domainInput').value = '';
      return exec(CMD + ' add ' + d).then(function (out) {
        var okk = /OK/.test(out);
        toast(okk ? ('已添加 ' + d) : '添加失败', !okk);
        return refreshAll();
      });
    }));

    var wb = $('whiteBtn');
    if (wb) wb.addEventListener('click', guard(function () {
      var d = cleanDomain($('whiteInput').value);
      if (!d) { toast('请输入合法域名', true); return; }
      $('whiteInput').value = '';
      return exec(CMD + ' white-add ' + d).then(function (out) {
        var okk = /OK/.test(out);
        toast(okk ? ('已放行 ' + d) : '操作失败', !okk);
        return refreshAll();
      });
    }));

    var di = $('domainInput');
    if (di) di.addEventListener('keydown', function (e) { if (e.key === 'Enter') add.click(); });

    var actions = document.querySelectorAll('[data-act]');
    for (var i = 0; i < actions.length; i++) {
      (function (btn) {
        btn.addEventListener('click', guard(function () {
          var act = btn.getAttribute('data-act');
          if (act === 'reload') {
            return exec(CMD + ' apply').then(function () { toast('规则已重新加载'); return refreshAll(); });
          }
          if (act === 'update') {
            toast('正在检查更新…');
            if ($('logCard')) $('logCard').classList.remove('hidden');
            return exec(CMD + ' update').then(function (out) {
              var text = String(out || '').trim();
              if ($('logBox')) $('logBox').textContent = text || '无输出';
              toast(text.indexOf('OK') === 0 ? '已完成' : '更新未完成，看日志');
              return refreshAll();
            });
          }
          if (act === 'status') {
            return refreshAll().then(function () { toast('状态已刷新'); });
          }
          if (act === 'logs') {
            if ($('logCard')) $('logCard').classList.remove('hidden');
            return exec(CMD + ' logs').then(function (out) {
              if ($('logBox')) $('logBox').textContent = out.trim() || '暂无日志';
            });
          }
          if (act === 'restore') {
            return exec(CMD + ' unmount').then(function () {
              toast('已恢复系统 hosts（重启后仍会挂载）');
              return refreshAll();
            });
          }
        }));
      })(actions[i]);
    }

    exec('grep "^version=" ' + MODDIR + '/module.prop').then(function (v) {
      var ver = (v.split('=')[1] || '').trim();
      if (ver && $('verText')) $('verText').textContent = '姚广孝 BOSS ' + ver;
    })['catch'](function () { /* ignore */ });

    boot();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bindUI);
  } else {
    bindUI();
  }

  function cleanDomain(v) {
    var d = String(v || '').trim().toLowerCase();
    d = d.replace(/^https?:\/\//, '').replace(/\/.*$/, '');
    if (!/^[a-z0-9*._-]+$/.test(d)) return '';
    return d;
  }
})();
