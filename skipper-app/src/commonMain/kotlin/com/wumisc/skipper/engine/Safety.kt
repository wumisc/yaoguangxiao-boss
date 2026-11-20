package com.wumisc.skipper.engine

/**
 * 红线：这些界面上的「跳过」一律不碰。
 * 宁可漏跳一百次，也不在一个支付页面上点错一下。
 */
object Safety {
    private val blockedPrefixes = listOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.android.settings",
        "com.android.settings.",
        "com.android.keychain",
        "com.google.android.packageinstaller",
        "com.android.providers.settings",
        "com.android.inputmethod",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.cellbroadcast",
        "com.android.emergency",
        "com.android.documentsui",
        "com.android.mms",
        "com.android.contacts",
    )

    /** 命中这些词的包名，整体拉黑（金融 / 支付 / 政务 / 医疗关键页面）。 */
    private val blockedKeywords = listOf(
        "bank", "pay", "alipay", "wallet", "unionpay", "ccb", "icbc", "boc.", "cmb",
        "gov", "government", "police", "tax", "social.security", "ehr", "hospital",
        "insur", "securit", "fund", "exchange", "credit", "loan", "mortgage",
        "wallet", "cashier", "checkout", "payment",
    )

    /** 容易被误判成跳过按钮的词，任何规则都要排除。 */
    val dangerTextRegexes = listOf(
        "^\\s*跳过登录.*$",
        "^\\s*跳过.*(?:登录|注册|绑定|授权|支付|开通|充值|下载|安装).*$",
    )

    fun isBlocked(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        if (blockedPrefixes.any { pkg == it || pkg.startsWith("$it.") }) return true
        val lower = pkg.lowercase()
        return blockedKeywords.any { lower.contains(it) }
    }
}
