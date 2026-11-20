package com.wumisc.skipper.data

import com.wumisc.skipper.engine.RulePack
import com.wumisc.skipper.engine.RuleSource
import com.wumisc.skipper.engine.Safety
import com.wumisc.skipper.engine.SkipRule
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 用户规则的存储格式：id|pkg|字，一行一条。 */
object RuleCodec {

    fun userRule(packageName: String, text: String, id: String): SkipRule? {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return null
        val cleanPkg = packageName.trim()
        return SkipRule(
            id = id,
            pack = RulePack.USER,
            title = cleanText,
            packagePatterns = listOf(if (cleanPkg.isEmpty()) "*" else cleanPkg),
            textPatterns = listOf("^\\s*${Regex.escape(cleanText)}\\s*$"),
            excludeTextPatterns = Safety.dangerTextRegexes,
            maxTopRatio = 0.55f,
            weight = 1.1f,
            source = RuleSource.USER,
        )
    }

    fun encode(rules: List<SkipRule>): String = rules.joinToString("\n") { rule ->
        listOf(
            rule.id,
            enc(rule.packagePatterns.firstOrNull() ?: "*"),
            enc(rule.title),
        ).joinToString("|")
    }

    fun decode(payload: String?): List<SkipRule> {
        if (payload.isNullOrBlank()) return emptyList()
        return payload.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 3) return@mapNotNull null
            val pkg = runCatching { dec(parts[1]) }.getOrDefault("*")
            val text = runCatching { dec(parts[2]) }.getOrDefault("")
            userRule(pkg, text, parts[0])
        }.toList()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun enc(value: String) = Base64.UrlSafe.encode(value.encodeToByteArray())

    @OptIn(ExperimentalEncodingApi::class)
    private fun dec(value: String) = runCatching { Base64.UrlSafe.decode(value).decodeToString() }.getOrElse { "" }
}
