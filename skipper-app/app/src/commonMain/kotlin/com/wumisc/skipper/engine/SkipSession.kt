package com.wumisc.skipper.engine

/**
 * 一次会话的状态机：窗口是谁、开了多久、点了几次、上次点到什么时候。
 * 从 Service 里抽出来，是因为它和「判定」一样重要，也必须能在靶场里被反复驱动。
 */
class SkipSession(
    private val rules: () -> List<SkipRule>,
    private val config: () -> EngineConfig,
    private val minEventGapMillis: Long = 70,
) {
    private var windowKey: String? = null
    private var currentPkg: String? = null
    private var state = WindowState(openedAt = 0)
    private var lastEventAt = 0L

    var lastIdle: IdleReason? = null
        private set

    /** 返回 null = 这条事件被节流丢掉，没有进入判定。 */
    fun onEvent(pkg: String, activityClass: String?, snapshot: WindowSnapshot, now: Long): Verdict? {
        if (now - lastEventAt < minEventGapMillis) return null
        lastEventAt = now

        // 部分 ROM 上 event.className 会是 null 或每次都不一样；这时沿用旧窗口，
        // 否则同一屏会被当成「新窗口」反复放行，点一下又点一下。
        if (pkg != currentPkg) {
            currentPkg = pkg
            windowKey = null
        }
        if (activityClass.isNullOrBlank()) {
            // class 为空：这次事件不换窗口，沿用旧的计数与冷却
            if (windowKey == null) {
                windowKey = "$pkg|-"
                state = WindowState(openedAt = now)
            }
        } else {
            val key = "$pkg|$activityClass"
            if (key != windowKey) {
                windowKey = key
                state = WindowState(openedAt = now)
                lastIdle = null
            }
        }

        val verdict = SkipEngine.decide(snapshot, rules(), config(), state, now)
        if (verdict is Verdict.Idle) lastIdle = verdict.reason
        return verdict
    }

    /** 点击成功后必须调用，否则冷却和单窗上限就失效了。 */
    fun markClicked(now: Long) {
        state = state.copy(hits = state.hits + 1, lastHitAt = now)
    }

    fun state(): WindowState = state
}
