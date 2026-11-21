package com.wumisc.skipper.engine

/**
 * 「看着像跳过、但规则没命中」的侦察兵。
 *
 * 引擎判定 Idle 时才会被调用：它不参与点击，只回答「这一帧里有没有值得补一条规则的按钮」。
 * 命中条件比正式判定更保守（顶部 55%、宽 60%、高 20%），所以它只会建议，不会动手。
 */
object Advisor {

    /** 只认明确的跳过语义；裸「关闭 / 取消」一律不碰。 */
    private val skipish = Regex(
        "^(?:\\s*(?:跳过|跳過|关闭广告|關閉廣告|skip)\\s*(?:[0-9]{1,2}\\s*(?:s|S|秒)?)?\\s*)$",
        RegexOption.IGNORE_CASE,
    )

    private val danger = listOf(
        "登录", "注册", "绑定", "授权", "支付", "开通", "充值", "下载", "安装",
        "领取", "预约", "实名", "同意", "确认", "更新", "升级", "进入", "继续",
        "打开", "查看", "播放", "购买", "下单", "退出", "注销",
    )

    data class Suspect(
        val pkg: String,
        val label: String,
        val viewId: String?,
        val activity: String?,
        val score: Float,
    )

    fun suspect(window: WindowSnapshot, state: WindowState, config: EngineConfig, now: Long): Suspect? {
        if (now - state.openedAt > config.windowMillis) return null
        if (Safety.isBlocked(window.pkg)) return null
        if (Safety.isDenied(window.pkg, config.deniedPackages)) return null

        var best: Suspect? = null
        for (node in window.nodes) {
            val label = Labels.normalize(node.label)
            if (!skipish.matches(label)) continue
            if (danger.any { label.contains(it) }) continue
            if (!node.clickable && !node.clickableAncestor) continue
            if (node.bounds.width <= 0 || node.bounds.height <= 0) continue
            if (node.bounds.centerY > window.height * 0.55f) continue
            if (node.bounds.width > window.width * 0.60f) continue
            if (node.bounds.height > window.height * 0.20f) continue
            if (node.bounds.width < window.width * 0.04f) continue

            val score = 0.62f + 0.10f + 0.10f + (if (node.clickable) 0.12f else 0.06f)
            if (best == null || score > best.score) {
                best = Suspect(
                    pkg = window.pkg,
                    label = label,
                    viewId = Labels.viewId(node.viewId).takeIf { it.isNotEmpty() },
                    activity = window.activityClass?.substringAfterLast('.'),
                    score = score.coerceAtMost(1f),
                )
            }
        }
        return best
    }
}
