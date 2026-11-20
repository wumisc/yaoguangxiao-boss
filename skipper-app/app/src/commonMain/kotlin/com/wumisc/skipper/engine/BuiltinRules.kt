package com.wumisc.skipper.engine

/**
 * 内置规则库。参考李跳跳「一条通用规则覆盖绝大多数开屏」的思路，
 * 再按场景拆成两个包：通用、视频。所有规则共用同一套安全阀（见 Safety）。
 *
 * 关键：真机上的跳过按钮几乎都带倒计时——「跳过 5」「跳过广告 3s」「关闭广告5秒」。
 * 只认「跳过」两个字的写法在实际 App 里几乎打不中，这里统一允许尾部倒计时。
 */
object BuiltinRules {

    /** 结尾允许 1–2 位数字，单位 s/S/秒 可省——「跳过 5」这种只写秒数的也很常见。 */
    private const val COUNTDOWN = "(?:\\s*[0-9]{1,2}\\s*(?:s|S|秒)?)?"

    private val generalCores = listOf(
        "跳过开屏广告", "跳过此广告", "跳过广告", "跳过",
        "关闭此广告", "关闭广告",
        "Skip Ads", "Skip Ad", "Skip",
    )

    private fun exact(core: String) = "^\\s*${Regex.escape(core)}$COUNTDOWN\\s*$"

    /** 容易误判的写法，先排除再匹配。 */
    private val dangerRegexes = listOf(
        "^\\s*跳过.*?(?:登录|注册|绑定|授权|支付|开通|充值|下载|安装|领取|预约|实名).*$",
    )

    val general = SkipRule(
        id = "general.skip",
        pack = RulePack.GENERAL,
        title = "通用跳过",
        packagePatterns = listOf("*"),
        textPatterns = generalCores.map(::exact),
        excludeTextPatterns = dangerRegexes,
        maxTopRatio = 0.5f,
        maxWidthRatio = 0.46f,
        maxHeightRatio = 0.14f,
    )

    private fun video(pkg: String, title: String) = SkipRule(
        id = "video.$pkg",
        pack = RulePack.VIDEO,
        title = title,
        packagePatterns = listOf(pkg),
        textPatterns = listOf("跳过", "跳过广告", "关闭广告", "Skip").map(::exact),
        excludeTextPatterns = dangerRegexes,
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
