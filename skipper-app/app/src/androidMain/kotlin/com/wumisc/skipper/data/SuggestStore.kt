package com.wumisc.skipper.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 一条「待补充」建议：某个 App 的开屏按钮看着像跳过，但现有规则没覆盖。 */
data class Suggestion(
    val pkg: String,
    val label: String,
    val viewId: String? = null,
    val activity: String? = null,
    val score: Float = 0f,
    val hits: Int = 1,
    val lastAt: Long = 0,
)

/**
 * 建议池：按「包名 + 按钮文字」去重，最多留 60 条。
 * 只写文件，不自动加规则——加不加由用户在规则页点那一下决定。
 */
class SuggestStore(private val file: File) {

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Suggestion>> = _items.asStateFlow()

    fun record(s: Suggestion) {
        val existing = _items.value.firstOrNull { it.pkg == s.pkg && it.label == s.label }
        val next = if (existing == null) {
            (listOf(s) + _items.value).take(MAX)
        } else {
            _items.value.map {
                if (it.pkg == s.pkg && it.label == s.label) it.copy(hits = it.hits + 1, lastAt = s.lastAt) else it
            }
        }
        _items.value = next
        runCatching { file.writeText(next.joinToString("\n", transform = ::encode) + "\n") }
    }

    fun ignore(pkg: String, label: String) {
        val next = _items.value.filterNot { it.pkg == pkg && it.label == label }
        _items.value = next
        runCatching { file.writeText(next.joinToString("\n", transform = ::encode).let { if (it.isEmpty()) "" else it + "\n" }) }
    }

    fun clear() {
        _items.value = emptyList()
        runCatching { file.writeText("") }
    }

    private fun load(): List<Suggestion> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 4) return@mapNotNull null
                Suggestion(
                    pkg = p[0],
                    label = p[1],
                    viewId = p[2].ifBlank { null },
                    activity = p[3].ifBlank { null },
                    score = p.getOrNull(4)?.toFloatOrNull() ?: 0f,
                    hits = p.getOrNull(5)?.toIntOrNull() ?: 1,
                    lastAt = p.getOrNull(6)?.toLongOrNull() ?: 0L,
                )
            }.distinctBy { it.pkg to it.label }.take(MAX)
        }.getOrDefault(emptyList())
    }

    private fun encode(s: Suggestion): String =
        listOf(s.pkg, s.label, s.viewId.orEmpty(), s.activity.orEmpty(), "%.2f".format(s.score), s.hits.toString(), s.lastAt.toString())
            .joinToString("\t")

    private companion object { const val MAX = 60 }
}
