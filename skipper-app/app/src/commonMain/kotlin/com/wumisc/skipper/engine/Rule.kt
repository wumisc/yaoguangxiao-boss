package com.wumisc.skipper.engine

enum class RuleSource { BUILTIN, USER }

/** 规则分组：UI 上按这个分组展示，引擎里按分组开关。 */
enum class RulePack(val title: String, val blurb: String) {
    GENERAL("通用跳过", "绝大多数开屏广告都用同一个词"),
    VIDEO("视频开屏", "视频类 App 的开屏广告位更靠上"),
    USER("我的规则", "自己加的，优先级最高"),
    IMPORTED("专项规则", "从公开规则库提炼的开屏专项，按包名生效"),
}

/**
 * 一条规则 = 一台「识别机器」。
 * 只认文字 + 位置 + 尺寸 + 可点性，绝不按坐标硬点。
 */
data class SkipRule(
    val id: String,
    val pack: RulePack,
    val title: String,
    val packagePatterns: List<String> = listOf("*"),
    val textPatterns: List<String>,
    /** 控件 id 名（`viewIdResourceName` 冒号后那段）的正则，一些开屏按钮没有文字只有 id。 */
    val viewIdPatterns: List<String> = emptyList(),
    val excludeTextPatterns: List<String> = emptyList(),
    /** 候选必须落在窗口顶部这一比例以内。 */
    val maxTopRatio: Float = 0.5f,
    val maxWidthRatio: Float = 0.46f,
    val maxHeightRatio: Float = 0.14f,
    val weight: Float = 1f,
    val source: RuleSource = RuleSource.BUILTIN,
    val enabled: Boolean = true,
) {
    fun matchesPackage(pkg: String): Boolean =
        packagePatterns.any { it == "*" || pkg == it || pkg.startsWith(it.removeSuffix("*")) }
}

data class EngineConfig(
    val windowMillis: Long = 3_000,
    val cooldownMillis: Long = 3_000,
    val maxPerWindow: Int = 2,
    val minScore: Float = 0.80f,
)

data class WindowState(
    val openedAt: Long,
    val hits: Int = 0,
    val lastHitAt: Long = 0,
)

sealed interface Verdict {
    data class Skip(val node: UiNode, val rule: SkipRule, val score: Float) : Verdict
    data class Idle(val reason: IdleReason, val best: Float = 0f, val ruleId: String? = null) : Verdict
}

enum class IdleReason {
    BLOCKED_PACKAGE,
    WINDOW_EXPIRED,
    WINDOW_LIMIT,
    COOLDOWN,
    NO_NODE,
    LOW_SCORE,
}
