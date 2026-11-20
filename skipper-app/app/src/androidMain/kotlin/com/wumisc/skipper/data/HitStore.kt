package com.wumisc.skipper.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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
        runCatching { file.appendText(HitCodec.encode(hit) + "\n") }
    }

    fun clear() {
        _hits.value = emptyList()
        runCatching { file.writeText("") }
    }

    private fun load(): List<SkipHit> {
        if (!file.exists()) return emptyList()
        return runCatching { HitCodec.decodeAll(file.readLines(), MAX) }.getOrDefault(emptyList())
    }

    private companion object { const val MAX = 300 }
}
