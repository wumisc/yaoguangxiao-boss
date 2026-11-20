package com.wumisc.skipper.data

import com.wumisc.skipper.engine.Labels
import com.wumisc.skipper.engine.RulePack
import com.wumisc.skipper.engine.RuleSource
import com.wumisc.skipper.engine.Safety
import com.wumisc.skipper.engine.SkipRule

/**
 * 专项规则包的读取器。格式是一行一个包，字段用 tab 分隔：
 *
 *   pkg | 应用名 | 文字正则（多个用 \u001F 分隔）| 控件 id 正则 | 顶部比例
 *
 * 为什么不用 JSON：这层只要「读得进、读得快、坏了能跳过」，TSV 最省事，
 * 而且和用户规则的解析思路一致，测试里可以直接喂字符串。
 */
object ImportedCodec {

    private const val SEP = '\u001F'

    fun decode(payload: String?): List<SkipRule> {
        if (payload.isNullOrBlank()) return emptyList()
        return payload.lineSequence()
            .filterNot { it.isBlank() || it.startsWith("#") }
            .mapNotNull(::parse)
            .toList()
    }

    private fun parse(line: String): SkipRule? {
        val parts = line.split('\t')
        if (parts.size < 5) return null
        val pkg = parts[0].trim()
        if (pkg.isEmpty()) return null
        val texts = splitPatterns(parts[2])
        val ids = splitPatterns(parts[3])
        if (texts.isEmpty() && ids.isEmpty()) return null
        val top = parts[4].trim().toFloatOrNull()?.coerceIn(0.4f, 0.96f) ?: 0.5f
        return SkipRule(
            id = "gkd.$pkg",
            pack = RulePack.IMPORTED,
            title = parts[1].trim().ifEmpty { pkg },
            packagePatterns = listOf(pkg),
            textPatterns = texts,
            viewIdPatterns = ids,
            excludeTextPatterns = Safety.dangerTextRegexes,
            maxTopRatio = top,
            maxWidthRatio = if (ids.isEmpty()) 0.5f else 0.62f,
            maxHeightRatio = if (ids.isEmpty()) 0.15f else 0.2f,
            weight = 1.02f,
            source = RuleSource.BUILTIN,
        )
    }

    private fun splitPatterns(field: String): List<String> =
        field.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }.take(8)

    /** 供设置页显示数量用，避免为了一个数字把整个包解析两遍。 */
    fun count(payload: String?): Int = decode(payload).size

    @Suppress("unused")
    private fun normalize(label: String) = Labels.normalize(label)
}
