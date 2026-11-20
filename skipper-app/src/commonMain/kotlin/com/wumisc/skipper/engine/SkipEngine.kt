package com.wumisc.skipper.engine

/**
 * 引擎是纯函数：给一帧窗口快照 + 规则 + 窗口状态，回答「点不点，点哪个」。
 * 它不持有 Android 对象，也不自己做点击——点不点由它决定，怎么点交给平台层。
 */
object SkipEngine {

    fun decide(
        window: WindowSnapshot,
        rules: List<SkipRule>,
        config: EngineConfig,
        state: WindowState,
        now: Long,
    ): Verdict {
        if (Safety.isBlocked(window.pkg)) return Verdict.Idle(IdleReason.BLOCKED_PACKAGE)
        if (window.nodes.isEmpty()) return Verdict.Idle(IdleReason.NO_NODE)

        if (now - state.openedAt > config.windowMillis) {
            return Verdict.Idle(IdleReason.WINDOW_EXPIRED)
        }
        if (state.hits >= config.maxPerWindow) {
            return Verdict.Idle(IdleReason.WINDOW_LIMIT)
        }
        if (state.hits > 0 && now - state.lastHitAt < config.cooldownMillis) {
            return Verdict.Idle(IdleReason.COOLDOWN)
        }

        val applicable = rules.filter { it.enabled && it.matchesPackage(window.pkg) }
        if (applicable.isEmpty()) return Verdict.Idle(IdleReason.NO_NODE)

        var best: Verdict.Skip? = null
        var bestScore = 0f
        var bestRule: String? = null

        for (rule in applicable) {
            for (node in window.nodes) {
                val score = Scoring.score(node, rule, window)
                if (score <= 0f) continue
                if (score > bestScore || (score == bestScore && best != null && node.bounds.area < best.node.bounds.area)) {
                    bestScore = score
                    bestRule = rule.id
                    best = Verdict.Skip(node, rule, score)
                }
            }
        }

        return when {
            best == null -> Verdict.Idle(IdleReason.NO_NODE)
            bestScore < config.minScore -> Verdict.Idle(IdleReason.LOW_SCORE, bestScore, bestRule)
            else -> best
        }
    }
}
