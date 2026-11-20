package com.wumisc.skipper.engine

/**
 * 打分器。分数由四件事累加，每一件都可解释：
 *  - 文字像不像跳过（0.62，权重最高，因为它是唯一语义依据）
 *  - 位置在不在顶部（0.10，开屏广告的跳过都在上半屏）
 *  - 尺寸小不小（0.10，跳过按钮不会是一整块）
 *  - 点不点得动（0.12 自身可点 / 0.06 靠祖先）
 */
internal object Scoring {

    private const val TEXT = 0.62f
    private const val TOP = 0.10f
    private const val SIZE = 0.10f
    private const val CLICK_SELF = 0.12f
    private const val CLICK_ANCESTOR = 0.06f

    fun score(node: UiNode, rule: SkipRule, window: WindowSnapshot): Float {
        if (rule.textPatterns.none { p -> node.label.matches(Regex(p)) }) return 0f
        if (rule.excludeTextPatterns.any { p -> node.label.matches(Regex(p)) }) return 0f
        if (!node.clickable && !node.clickableAncestor) return 0f
        if (node.bounds.width <= 0 || node.bounds.height <= 0) return 0f

        if (node.bounds.centerY > window.height * rule.maxTopRatio) return 0f
        if (node.bounds.width > window.width * rule.maxWidthRatio) return 0f
        if (node.bounds.height > window.height * rule.maxHeightRatio) return 0f
        if (node.bounds.width < window.width * 0.04f) return 0f

        var total = TEXT + TOP + SIZE
        total += if (node.clickable) CLICK_SELF else CLICK_ANCESTOR
        return (total * rule.weight).coerceAtMost(1f)
    }
}
