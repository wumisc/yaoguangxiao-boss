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
    private var currentPkg: String? = null
    private var state = WindowState(openedAt = 0)
    private var lastContentEventAt = 0L

    var lastIdle: IdleReason? = null
        private set

    /**
     * 返回 null = 这条事件被节流丢掉，没有进入判定。
     * 状态变化事件不参与内容节流：开屏先来 STATE，紧接着的 CONTENT 很可能才带着「跳过」节点。
     */
    fun onEvent(
        pkg: String,
        activityClass: String?,
        snapshot: WindowSnapshot,
        now: Long,
        isStateEvent: Boolean = false,
    ): Verdict? {
        if (!isStateEvent && now - lastContentEventAt < minEventGapMillis) return null
        if (!isStateEvent) lastContentEventAt = now

        // 一个「窗口」= 一次进入前台，而不是一个 Activity。
        // 界面内部再怎么跳 Activity（开编辑器、进设置页），都不重置工作窗口，
        // 这样「开屏那几秒工作窗口」之外的东西永远不会被点。
        if (pkg != currentPkg) {
            currentPkg = pkg
            state = WindowState(openedAt = now)
            lastIdle = null
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
