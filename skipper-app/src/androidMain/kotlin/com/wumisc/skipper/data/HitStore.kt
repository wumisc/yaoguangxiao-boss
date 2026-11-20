package com.wumisc.skipper.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

data class SkipHit(
    val at: Long,
    val pkg: String,
    val ruleId: String,
    val text: String,
    val score: Float,
)

/**
 * 命中记录：内存里留最近 300 条给 UI 实时用，同时按行落盘，杀进程不丢。
 * 记录只写「成功点掉的那一下」，失败和拒绝不进日志，避免噪音。
 */
class HitStore(private val file: File) {

    private val _hits = MutableStateFlow(load())
    val hits: StateFlow<List<SkipHit>> = _hits.asStateFlow()

    fun record(hit: SkipHit, keepHistory: Boolean) {
        val next = (listOf(hit) + _hits.value).take(MAX)
        _hits.value = next
        if (!keepHistory) return
        runCatching { file.appendText(encode(hit) + "\n") }
    }

    fun clear() {
        _hits.value = emptyList()
        runCatching { file.writeText("") }
    }

    private fun load(): List<SkipHit> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().asReversed().mapNotNull(::decode).take(MAX)
        }.getOrDefault(emptyList())
    }

    private fun encode(hit: SkipHit): String = listOf(
        hit.at.toString(),
        URLEncoder.encode(hit.pkg, "UTF-8"),
        hit.ruleId,
        URLEncoder.encode(hit.text, "UTF-8"),
        hit.score.toString(),
    ).joinToString("|")

    private fun decode(line: String): SkipHit? {
        val parts = line.split("|")
        if (parts.size < 5) return null
        return runCatching {
            SkipHit(
                at = parts[0].toLong(),
                pkg = URLDecoder.decode(parts[1], "UTF-8"),
                ruleId = parts[2],
                text = URLDecoder.decode(parts[3], "UTF-8"),
                score = parts[4].toFloat(),
            )
        }.getOrNull()
    }

    private companion object { const val MAX = 300 }
}
