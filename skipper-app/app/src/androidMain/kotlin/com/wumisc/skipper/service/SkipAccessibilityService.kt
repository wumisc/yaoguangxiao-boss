package com.wumisc.skipper.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wumisc.skipper.data.SkipHit
import com.wumisc.skipper.data.Settings
import com.wumisc.skipper.engine.EngineConfig
import com.wumisc.skipper.engine.SkipSession
import com.wumisc.skipper.engine.Verdict
import com.wumisc.skipper.graph
import com.wumisc.skipper.platform.AndroidSnapshot
import com.wumisc.skipper.platform.SnapshotMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 平台层：只负责三件事——取快照、执行点击、上报。
 * 「该不该点、同一窗口点几次」全在 SkipSession / SkipEngine 里，那边有靶场盯着。
 */
class SkipAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val heartbeat = Handler(Looper.getMainLooper())
    private var settings = Settings()

    private val session by lazy {
        SkipSession(
            rules = { graph.rules.activeRules(settings) },
            config = { config() },
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        graph.settings.setConnected(true)
        graph.stats.setConnected(true)
        dbg("服务已连接")
        scope.launch {
            graph.settings.state.collect {
                settings = it
                dbg("设置更新 enabled=${it.enabled} 窗口=${it.windowMillis}ms 冷却=${it.cooldownMillis}ms 上限=${it.maxPerWindow}")
            }
        }
        heartbeat.post(heartbeatTask)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        dbg("服务断开")
        graph.settings.setConnected(false)
        graph.stats.setConnected(false)
        heartbeat.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !settings.enabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return
        val eventType = event.eventType
        val activityClass = event.className?.toString()

        graph.stats.onEvent(eventType, pkg, activityClass, 0)

        val root = rootInActiveWindow ?: activeRoot()
        if (root == null) {
            graph.stats.onRootNull(pkg, activityClass)
            dbg("根节点为空 pkg=$pkg")
            return
        }
        val (w, h) = screenSize()
        val snapshot = SnapshotMapper.map(pkg, activityClass, root, w, h)
        graph.stats.onNodes(snapshot.window.nodes.size)
        val now = SystemClock.uptimeMillis()
        dbg("事件 pkg=$pkg cls=${event.className} 节点=${snapshot.window.nodes.size} 屏=${w}x$h")

        val isStateEvent = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        when (val verdict = session.onEvent(pkg, activityClass, snapshot.window, now, isStateEvent)) {
            null -> {
                graph.stats.onThrottle(pkg)
                dbg("  节流：两条事件间隔 <70ms，丢弃")
            }
            is Verdict.Skip -> {
                graph.stats.onVerdict("命中：${verdict.rule.title}", verdict.score, verdict.rule.id)
                dbg("  命中 label=\"${verdict.node.label}\" 分数=${fmt(verdict.score)} 规则=${verdict.rule.id} 区域=${verdict.node.bounds}")
                performSkip(verdict, snapshot, now)
            }
            is Verdict.Idle -> {
                graph.stats.onVerdict(reasonText(verdict.reason), verdict.best, verdict.ruleId)
                dbg("  未命中 原因=${reasonText(verdict.reason)} 最高分=${fmt(verdict.best)} 规则=${verdict.ruleId ?: "-"}")
                if (verdict.reason == com.wumisc.skipper.engine.IdleReason.NO_NODE ||
                    verdict.reason == com.wumisc.skipper.engine.IdleReason.LOW_SCORE
                ) {
                    val suspect = com.wumisc.skipper.engine.Advisor.suspect(
                        snapshot.window, session.state(), config(), now,
                    )
                    if (suspect != null) {
                        graph.suggest.record(
                            com.wumisc.skipper.data.Suggestion(
                                pkg = suspect.pkg,
                                label = suspect.label,
                                viewId = suspect.viewId,
                                activity = suspect.activity,
                                score = suspect.score,
                                lastAt = System.currentTimeMillis(),
                            )
                        )
                        dbg("  侦察：像跳过但没规则 label=\"${suspect.label}\" pkg=${suspect.pkg}")
                    }
                }
            }
        }
    }

    /** 有些 ROM 上 rootInActiveWindow 为 null，退一步从 windows 里找活动的那个。 */
    private fun activeRoot(): AccessibilityNodeInfo? =
        runCatching { windows.firstOrNull { it.isActive }?.root ?: windows.firstOrNull()?.root }.getOrNull()

    private fun reasonText(reason: com.wumisc.skipper.engine.IdleReason): String = when (reason) {
        com.wumisc.skipper.engine.IdleReason.BLOCKED_PACKAGE -> "包名在黑名单里（金融/支付/政务/系统）"
        com.wumisc.skipper.engine.IdleReason.WINDOW_EXPIRED -> "超过工作窗口，本界面不再动作"
        com.wumisc.skipper.engine.IdleReason.WINDOW_LIMIT -> "本界面已点满次数"
        com.wumisc.skipper.engine.IdleReason.COOLDOWN -> "冷却中"
        com.wumisc.skipper.engine.IdleReason.NO_NODE -> "没有候选（文字/位置/尺寸/可点性任一不满足）"
        com.wumisc.skipper.engine.IdleReason.LOW_SCORE -> "有候选但分数不够"
    }

    private fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun dbg(message: String) {
        if (settings.debugLog) graph.debug.append(message)
    }

    private fun performSkip(verdict: Verdict.Skip, snapshot: AndroidSnapshot, now: Long) {
        val node = snapshot.node(verdict.node.id) ?: return
        if (!click(node)) return

        session.markClicked(now)
        graph.hits.record(
            SkipHit(
                at = System.currentTimeMillis(),
                pkg = snapshot.window.pkg,
                ruleId = verdict.rule.id,
                text = verdict.node.label,
                score = verdict.score,
            ),
            keepHistory = settings.keepHistory,
        )
        if (settings.vibrateOnSkip) vibrate()
    }

    /** 点得动就点；点不动就沿父链找第一个可点的，最多四层。 */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun vibrate() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(14, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun config() = EngineConfig(
        windowMillis = settings.windowMillis,
        cooldownMillis = settings.cooldownMillis,
        maxPerWindow = settings.maxPerWindow,
        deniedPackages = settings.denyPackages.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    )

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.currentWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) return bounds.width() to bounds.height()
        }
        val dm = resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    private val heartbeatTask = object : Runnable {
        override fun run() {
            graph.settings.markAlive(System.currentTimeMillis())
            heartbeat.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private companion object { const val HEARTBEAT_MS = 5_000L }
}
