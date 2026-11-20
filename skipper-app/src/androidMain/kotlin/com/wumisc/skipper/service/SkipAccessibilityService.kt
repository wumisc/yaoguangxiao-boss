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
import com.wumisc.skipper.graph
import com.wumisc.skipper.engine.EngineConfig
import com.wumisc.skipper.engine.IdleReason
import com.wumisc.skipper.engine.SkipEngine
import com.wumisc.skipper.engine.Verdict
import com.wumisc.skipper.engine.WindowState
import com.wumisc.skipper.platform.AndroidSnapshot
import com.wumisc.skipper.platform.SnapshotMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 平台层：只负责三件事——取快照、执行点击、上报结果。
 * 「该不该点」不归它管，全部交给 engine.SkipEngine。
 */
class SkipAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val heartbeat = Handler(Looper.getMainLooper())
    private var settings = Settings()

    private var windowKey: String? = null
    private var windowState = WindowState(openedAt = 0)
    private var lastEvaluateAt = 0L
    private var lastIdle: IdleReason? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val graph = graph
        graph.settings.setConnected(true)
        scope.launch { graph.settings.state.collect { settings = it } }
        heartbeat.post(heartbeatTask)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        graph.settings.setConnected(false)
        heartbeat.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!settings.enabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return

        val now = SystemClock.uptimeMillis()
        if (now - lastEvaluateAt < MIN_EVENT_GAP_MS) return
        lastEvaluateAt = now

        val root = rootInActiveWindow ?: return
        val className = event.className?.toString()
        val key = "$pkg|$className"
        if (key != windowKey) {
            windowKey = key
            windowState = WindowState(openedAt = now)
            lastIdle = null
        }

        val (w, h) = screenSize()
        val snapshot = SnapshotMapper.map(pkg, className, root, w, h)
        val appRules = graph.rules.activeRules(settings)
        val verdict = SkipEngine.decide(snapshot.window, appRules, config(), windowState, now)

        when (verdict) {
            is Verdict.Skip -> performSkip(verdict, snapshot, now)
            is Verdict.Idle -> lastIdle = verdict.reason
        }
    }

    private fun performSkip(verdict: Verdict.Skip, snapshot: AndroidSnapshot, now: Long) {
        val node = snapshot.node(verdict.node.id) ?: return
        if (!click(node)) return

        windowState = windowState.copy(hits = windowState.hits + 1, lastHitAt = now)
        graph.hits.record(
            SkipHit(at = System.currentTimeMillis(), pkg = snapshot.window.pkg, ruleId = verdict.rule.id, text = verdict.node.label, score = verdict.score),
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

    private companion object {
        const val MIN_EVENT_GAP_MS = 70L
        const val HEARTBEAT_MS = 5_000L
    }
}
