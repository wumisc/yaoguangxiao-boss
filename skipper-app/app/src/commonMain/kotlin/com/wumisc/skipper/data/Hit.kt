package com.wumisc.skipper.data

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class SkipHit(
    val at: Long,
    val pkg: String,
    val ruleId: String,
    val text: String,
    val score: Float,
)

/** 记录文件的行格式：at|pkg|ruleId|text|score，字段做 URL 编码，避免分隔符撞车。 */
object HitCodec {

    fun encode(hit: SkipHit): String = listOf(
        hit.at.toString(),
        enc(hit.pkg),
        hit.ruleId,
        enc(hit.text),
        hit.score.toString(),
    ).joinToString("|")

    fun decode(line: String): SkipHit? {
        val parts = line.split("|")
        if (parts.size < 5) return null
        return runCatching {
            SkipHit(
                at = parts[0].toLong(),
                pkg = dec(parts[1]),
                ruleId = parts[2],
                text = dec(parts[3]),
                score = parts[4].toFloat(),
            )
        }.getOrNull()
    }

    fun decodeAll(lines: List<String>, max: Int): List<SkipHit> =
        lines.asReversed().mapNotNull(::decode).take(max)

    @OptIn(ExperimentalEncodingApi::class)
    private fun enc(value: String) = Base64.UrlSafe.encode(value.encodeToByteArray())

    @OptIn(ExperimentalEncodingApi::class)
    private fun dec(value: String) = runCatching { Base64.UrlSafe.decode(value).decodeToString() }.getOrElse { "" }
}
