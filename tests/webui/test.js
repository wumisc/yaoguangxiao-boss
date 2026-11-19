/* 姚广孝 BOSS · WebUI 桥接层靶场（Node + 假 DOM + 假 ksu） */
const fs = require('fs');
const vm = require('vm');
const path = require('path');

const APP = path.join(__dirname, '../../姚广孝BOSS/webroot/app.js');
const SRC = fs.readFileSync(APP, 'utf8');

function makeEl(id) {
  let _html = '';
  const el = {
    id, textContent: '', innerHTML: '', className: '', value: '', children: [], _cls: new Set(),
    attrs: {}, listeners: {},
    classList: {
      add: c => el._cls.add(c), remove: c => el._cls.delete(c),
      toggle: (c, on) => (on === undefined ? (el._cls.has(c) ? el._cls.delete(c) : el._cls.add(c)) : (on ? el._cls.add(c) : el._cls.delete(c))),
      contains: c => el._cls.has(c)
    },
    appendChild(k) { el.children.push(k); return k; },
    addEventListener(t, f) { (el.listeners[t] = el.listeners[t] || []).push(f); },
    getAttribute(n) { return el.attrs[n]; },
    remove() { el.removed = true; },
    click() { (el.listeners.click || []).forEach(f => f({})); }
  };
  Object.defineProperty(el, 'innerHTML', {
    get() { return _html; },
    set(v) { _html = v; if (v === '') el.children.length = 0; }
  });
  return el;
}

function makeEnv(ksuFactory, shell) {
  const els = {};
  const body = makeEl('body');
  const actions = ['reload', 'status', 'logs', 'restore'].map(a => { const e = makeEl('act-' + a); e.attrs['data-act'] = a; return e; });
  const doc = {
    readyState: 'complete',
    body,
    getElementById(id) { if (!els[id]) els[id] = makeEl(id); return els[id]; },
    createElement() { return makeEl('new'); },
    querySelectorAll(sel) { return sel === '[data-act]' ? actions : []; },
    addEventListener() {}
  };
  const cmds = [];
  const hist = [];
  // 还原 index.html 的初始态：诊断条默认隐藏
  els.errorBar = makeEl('errorBar');
  els.errorBar._cls.add('hidden');
  const ksuObj = ksuFactory(cmds, hist);
  const ctx = {
    document: doc, navigator: {}, location: { href: 'ksu://webui' },
    setTimeout, clearTimeout, Promise, console, JSON, String, Object, Array, Date,
    requestAnimationFrame: cb => setTimeout(cb, 0), els, cmds, shell
  };
  ctx.window = ctx;
  ctx.window.document = doc;
  ctx.window.ksu = ksuObj;
  ctx.ksu = ksuObj;
  vm.createContext(ctx);
  vm.runInContext(SRC, ctx, { filename: 'app.js' });
  return ctx;
}

const sleep = ms => new Promise(r => setTimeout(r, ms));
let pass = 0, fail = 0;
const ok = n => { pass++; console.log('PASS  ' + n); };
const bad = (n, got) => { fail++; console.log('FAIL  ' + n + ' | 实际=' + got); };
const eq = (n, got, want) => (String(got) === String(want) ? ok(n) : bad(n, got));

function fakeYgb(state) {
  return (cmd) => {
    if (/echo YGB_READY/.test(cmd)) return 'YGB_READY\n';
    if (/for d in/.test(cmd)) return 'YGB_DIR=/data/adb/modules/yaoguangxiao_boss\n';
    if (/module\.prop/.test(cmd)) return 'version=v1.3.1\n';
    if (/ui-status/.test(cmd)) {
      return JSON.stringify({
        state: state.on, mounted: state.on === 'on' ? 'yes' : 'no',
        rules: state.on === 'on' ? state.rules : 0, base: state.base,
        boost: state.on === 'on' ? state.boost : 0, boostOn: state.boostOn,
        custom: 0, white: 97
      }) + '\n';
    }
    if (/\slist$/.test(cmd)) return 'ad.test.com\n';
    if (/\swhite$/.test(cmd)) return '# 白名单\n';
    if (/\soff$/.test(cmd)) { state.on = 'off'; return 'OK 拦截已暂停\n'; }
    if (/\son$/.test(cmd)) { state.on = 'on'; return 'OK 拦截已开启\n'; }
    if (/boost-off/.test(cmd)) { state.boostOn = 0; state.boost = 0; return 'OK\n'; }
    if (/boost-on/.test(cmd)) { state.boostOn = 1; state.boost = state.b0; return 'OK\n'; }
    return 'OK\n';
  };
}

(async () => {
  // 场景 1：回调式桥接
  {
    const st = { on: 'on', boostOn: 1, base: 251, b0: 9181, rules: 9432, boost: 9181 };
    const ctx = makeEnv((cmds, hist) => ({
      exec(cmd, cb) { cmds.push(cmd); hist.push(cmd); const out = fakeYgb(st)(cmd); if (cb) setTimeout(() => cb(0, out, ''), 10); },
      toast() {}
    }));
    await sleep(1400);
    eq('S1 回调桥接 规则数上屏', ctx.els.stRules && ctx.els.stRules.textContent, '9432');
    eq('S1 已移除 SM 开关逻辑', /smToggle|stSm/.test(SRC), 'false');
    eq('S1 已移除 SM 命令分支', /sm-on|sm-off/.test(SRC), 'false');
    eq('S1 回调桥接 状态胶囊', ctx.els.statePill && ctx.els.statePill.textContent, '拦截生效中');
    eq('S1 回调桥接 诊断条隐藏', ctx.els.errorBar._cls.has('hidden'), 'true');
  }

  // 场景 2：Promise 式桥接（exec 带 cb 时不回调，只返回 Promise）
  {
    const st = { on: 'on', boostOn: 1, base: 251, b0: 9181, rules: 9432, boost: 9181 };
    const ctx = makeEnv((cmds) => ({
      exec(cmd, cb) { cmds.push(cmd); const out = fakeYgb(st)(cmd); if (cb) return; return Promise.resolve(out); }
    }));
    await sleep(3700);
    eq('S2 Promise 桥接 规则数上屏', ctx.els.stRules && ctx.els.stRules.textContent, '9432');
    eq('S2 Promise 桥接 状态胶囊', ctx.els.statePill && ctx.els.statePill.textContent, '拦截生效中');
  }

  // 场景 3：无 ksu
  {
    const ctx = makeEnv(() => undefined);
    await sleep(1400);
    eq('S3 无桥接 诊断条可见', ctx.els.errorBar._cls.has('hidden'), 'false');
    eq('S3 无桥接 胶囊失败', ctx.els.statePill && ctx.els.statePill.textContent, '连接失败');
    const all = ctx.els.errorBar.children.map(c => c.textContent || '').join('\n');
    eq('S3 诊断含 window.ksu', all.indexOf('window.ksu: 不存在') >= 0, 'true');
  }

  // 场景 4：点击开关下发命令
  {
    const st = { on: 'on', boostOn: 1, base: 251, b0: 9181, rules: 9432, boost: 9181 };
    const ctx = makeEnv((cmds) => ({
      exec(cmd, cb) { cmds.push(cmd); const out = fakeYgb(st)(cmd); if (cb) setTimeout(() => cb(0, out, ''), 10); }
    }));
    await sleep(1200);
    ctx.els.toggle.click();
    await sleep(900);
    eq('S4 总开关下发 off', ctx.cmds.some(c => /ygb off$/.test(c.trim())), 'true');
    eq('S4 关闭后胶囊', ctx.els.statePill.textContent, '已暂停');
    eq('S4 关闭后生效数归零', ctx.els.stRules.textContent, '0');
    eq('S4 关闭后提示停用', ctx.els.mountHint.textContent.indexOf('全部规则停用') >= 0, 'true');
    eq('S4 关闭后增强开关置灰', ctx.els.boostToggle.disabled, 'true');
    const before = ctx.cmds.length;
    ctx.els.boostToggle.click();
    await sleep(400);
    eq('S4 关闭态点增强不下发命令', ctx.cmds.length, before);
    ctx.els.boostToggle.click();
    await sleep(400);
    ctx.els.toggle.click();
    await sleep(900);
    eq('S4 重新开启下发 on', ctx.cmds.some(c => /ygb on$/.test(c.trim())), 'true');
    eq('S4 开启后生效数恢复', ctx.els.stRules.textContent, '9432');
  }

  // 场景 5：脏数据
  {
    const ctx = makeEnv((cmds) => ({
      exec(cmd, cb) { cmds.push(cmd); const out = /echo YGB_READY/.test(cmd) ? 'YGB_READY\n' : (/for d in/.test(cmd) ? 'YGB_DIR=/data/adb/modules/yaoguangxiao_boss\n' : 'NOT_JSON'); setTimeout(() => { if (cb) cb(0, out, ''); }, 10); }
    }));
    await sleep(1500);
    const all = ctx.els.errorBar.children.map(c => c.textContent || '').join('\n');
    eq('S5 脏数据 报状态读取失败', all.indexOf('模块状态读取失败') >= 0, 'true');
    eq('S5 脏数据 诊断条可见', ctx.els.errorBar._cls.has('hidden'), 'false');
  }

  console.log('\n==== WebUI 结果: PASS=' + pass + ' FAIL=' + fail + ' ====');
  process.exit(fail ? 1 : 0);
})();
