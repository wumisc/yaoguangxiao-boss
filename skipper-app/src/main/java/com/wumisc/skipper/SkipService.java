package com.wumisc.skipper;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * P0：一条规则的最小实现。
 * 只在「窗口切换后的 3 秒内」找一次「跳过」按钮，找到就点，点不到就记一条日志。
 * 不屏蔽广告、不注入手势、不改别人的界面。
 */
public class SkipService extends AccessibilityService {

    private static final long WINDOW_WINDOW_MS = 3000;   // 窗口切换后的观察期
    private static final long COOLDOWN_MS = 3000;        // 同一包名的冷却
    private static final int MAX_NODES = 400;            // 单次遍历上限
    private static final int MAX_CLICKS_PER_WINDOW = 2;

    /** 严格只认「跳过」。绝不含「详情 / 下载 / 领取 / 查看」这类会点到广告主体的词。 */
    private static final String[] SKIP_WORDS = {
            "跳过广告", "跳过", "关闭广告", "跳過", "skip ad", "skip"
    };

    /** 永不动作的包名：金融、支付、政务、系统界面。 */
    private static final String[] NEVER = {
            "com.eg.android.alipaygphone", "com.tencent.mm", "com.unionpay",
            "com.chinamworld.main", "com.icbc", "com.ccb.longji", "com.bankcomm",
            "com.cmbchina", "com.ecitic.bank.mobile", "com.pingan.paces",
            "com.antfortune.wealth"
    };

    private long lastWindowAt = 0;
    private String lastWindowPkg = "";
    private long lastClickAt = 0;
    private String lastClickPkg = "";
    private int clicksThisWindow = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        HitLog.init(this);
        HitLog.add("self", "服务已连接", "P0 规则已加载");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;

        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        long now = SystemClock.elapsedRealtime();

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!pkg.equals(lastWindowPkg)) {
                lastWindowPkg = pkg;
                lastWindowAt = now;
                clicksThisWindow = 0;
            }
        }

        if (now - lastWindowAt > WINDOW_WINDOW_MS) return;
        if (clicksThisWindow >= MAX_CLICKS_PER_WINDOW) return;
        if (now - lastClickAt < COOLDOWN_MS && pkg.equals(lastClickPkg)) return;
        if (!allowed(pkg)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            Candidate best = findSkip(root, pkg);
            if (best == null) return;
            if (click(best.node)) {
                lastClickAt = now;
                lastClickPkg = pkg;
                clicksThisWindow++;
                HitLog.add(pkg, "命中「" + best.text + "」", "已点击");
            } else {
                HitLog.add(pkg, "命中「" + best.text + "」", "点击未生效（节点不可点）");
            }
        } catch (Exception e) {
            HitLog.add(pkg, "扫描异常", e.getClass().getSimpleName());
        } finally {
            root.recycle();
        }
    }

    /** 白名单 / 黑名单闸门 */
    private boolean allowed(String pkg) {
        String self = getPackageName();
        if (pkg.equals(self) || pkg.equals("com.android.systemui")) return false;
        String lower = pkg.toLowerCase();
        for (String n : NEVER) {
            if (n.isEmpty()) continue;
            if (lower.equals(n) || lower.startsWith(n)) return false;
        }
        SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
        boolean whiteOnly = sp.getBoolean("whiteOnly", false);
        if (!whiteOnly) return true;
        String list = sp.getString("whitelist", "");
        for (String s : list.split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && lower.equals(t.toLowerCase())) return true;
        }
        return false;
    }

    private static final class Candidate {
        AccessibilityNodeInfo node;
        String text;
        int score;
    }

    private Candidate findSkip(AccessibilityNodeInfo root, String pkg) {
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        int sw = screen.width(), sh = screen.height();
        Candidate best = null;

        while (!queue.isEmpty() && visited < MAX_NODES) {
            AccessibilityNodeInfo n = queue.poll();
            visited++;
            String text = textOf(n);
            if (text != null && isSkip(text)) {
                Rect b = new Rect();
                n.getBoundsInScreen(b);
                if (b.width() > 0 && b.height() > 0) {
                    int score = 0;
                    if (sh > 0 && b.centerY() < sh * 0.35) score += 100;   // 上方
                    if (sw > 0 && b.centerX() > sw * 0.45) score += 60;    // 偏右
                    if (n.isClickable()) score += 40;
                    if (b.width() < sw * 0.5) score += 10;                 // 小按钮更像跳过
                    if (best == null || score > best.score) {
                        if (best != null) best.node.recycle();
                        Candidate c = new Candidate();
                        c.node = AccessibilityNodeInfo.obtain(n);
                        c.text = text;
                        c.score = score;
                        best = c;
                    }
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return best;
    }

    private static String textOf(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        if (t == null) t = n.getContentDescription();
        if (t == null) t = n.getTooltipText();
        return t == null ? null : t.toString().trim();
    }

    private static boolean isSkip(String text) {
        String t = text.toLowerCase().replace(" ", "");
        for (String w : SKIP_WORDS) {
            if (t.contains(w.replace(" ", ""))) return true;
        }
        // 「跳过 3s」「跳过5秒」这类倒计时也算，但必须含「跳过」
        return t.contains("跳过") && t.length() <= 12;
    }

    private boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int i = 0; i < 4 && cur != null; i++) {
            if (cur.isClickable()) {
                boolean ok = cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (ok) return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }
}
