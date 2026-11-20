package com.wumisc.skipper.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试日志：只存在内存里（最近 240 行），不写盘、不上传。
 * 开关关着的时候一行都不记，所以「复制出来的东西」是用户主动要的。
 */
class DebugLog {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)

    fun append(message: String) {
        val stamp = formatter.format(Date())
        _lines.value = (listOf("$stamp  $message") + _lines.value).take(MAX)
    }

    fun clear() {
        _lines.value = emptyList()
    }

    /** 给「复制」按钮用：带一段说明头，避免贴出去看不懂。 */
    fun exportText(): String = buildString {
        appendLine("姚广孝·跳过 调试日志（最近 ${_lines.value.size} 条）")
        _lines.value.asReversed().forEach { appendLine(it) }
    }

    private companion object { const val MAX = 240 }
}
