package com.wumisc.skipper.engine

/**
 * 内置规则库。参考李跳跳「一条通用规则覆盖绝大多数开屏」的思路，
 * 再按场景拆成两个包：通用、视频。所有规则共用同一套安全阀（见 Safety）。
 */
object BuiltinRules {

    /** 只认「就是跳过按钮」的文字，带尾巴的一律不算，防止误点「跳过登录」。 */
    private val skipRegexes = listOf(
        "^\\s*跳过\\s*$",
        "^\\s*跳过广告\\s*$",
        "^\\s*跳过此广告\\s*$",
        "^\\s*跳过开屏广告\\s*$",
        "^\\s*跳过开屏\\s*$",
        "^\\s*关闭广告\\s*$",
        "^\\s*关闭此广告\\s*$",
        "^\\s*Skip\\s*$",
        "^\\s*Skip Ad\\s*$",
        "^\\s*Skip Ads\\s*$",
    )

    val general = SkipRule(
        id = "general.skip",
        pack = RulePack.GENERAL,
        title = "通用跳过",
        packagePatterns = listOf("*"),
        textPatterns = skipRegexes,
        excludeTextPatterns = Safety.dangerTextRegexes,
        maxTopRatio = 0.5f,
        maxWidthRatio = 0.46f,
        maxHeightRatio = 0.14f,
    )

    private fun video(pkg: String, title: String) = SkipRule(
        id = "video.$pkg",
        pack = RulePack.VIDEO,
        title = title,
        packagePatterns = listOf(pkg),
        textPatterns = listOf("^\\s*(?:跳过|关闭广告|跳过广告|Skip)\\s*$"),
        excludeTextPatterns = Safety.dangerTextRegexes,
        maxTopRatio = 0.42f,
        maxWidthRatio = 0.4f,
        maxHeightRatio = 0.12f,
        weight = 1.05f,
    )

    val videoRules = listOf(
        video("com.qiyi.video", "爱奇艺"),
        video("com.youku.phone", "优酷"),
        video("com.tencent.qqlive", "腾讯视频"),
        video("com.hunantv.imgo.activity", "芒果 TV"),
        video("tv.danmaku.bili", "哔哩哔哩"),
        video("com.sohu.sohuvideo", "搜狐视频"),
        video("com.cctv.yangshipin.app.androidp", "央视频"),
    )

    val all: List<SkipRule> = listOf(general) + videoRules

    fun byPack(pack: RulePack): List<SkipRule> = all.filter { it.pack == pack }
}
