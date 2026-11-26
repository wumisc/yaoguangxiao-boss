package com.wumisc.skipper.data

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 运行诊断：只存在内存里，不写盘、不联网。
 * 目的不是"好看"，而是让用户/开发者一眼看出链路断在哪：
 * 事件有没有来、根节点有没有、最后一条为什么没点。
 */
data class RuntimeStats(
    val connected: Boolean = false,
    val events: Long = 0,
    val stateEvents: Long = 0,
    val contentEvents: Long = 0,
    val lastPkg: String = "",
    val lastClass: String = "",
    val lastNodes: Int = 0,
    val lastAt: Long = 0,
    val lastReason: String = "暂无事件",
    val lastScore: Float = 0f,
    val lastRule: String = "",
    val rootNull: Long = 0,
    val throttled: Long = 0,
)

class RuntimeStatsStore {

    private val _state = MutableStateFlow(RuntimeStats())
    val state: StateFlow<RuntimeStats> = _state.asStateFlow()

    private var events = 0L
    private var stateEvents = 0L
    private var contentEvents = 0L
    private var lastPkg = ""
    private var lastClass = ""
    private var lastNodes = 0
    private var lastAt = 0L
    private var lastReason = "暂无事件"
    private var lastScore = 0f
    private var lastRule = ""
    private var rootNull = 0L
    private var throttled = 0L
    private var publishedAt = 0L

    fun setConnected(value: Boolean) {
        _state.value = _state.value.copy(connected = value)
        lastReason = if (value) "服务已连接" else "服务已断开"
        publish(force = true)
    }

    /** 每个无障碍事件都只改内存计数；UI 最多 1 秒刷新一次，避免内容变化事件把主线程拖死。 */
    fun onEvent(type: Int, pkg: String, activityClass: String?, nodes: Int) {
        events++
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) stateEvents++ else contentEvents++
        lastPkg = pkg
        lastClass = activityClass.orEmpty()
        lastNodes = nodes
        lastAt = System.currentTimeMillis()
        publish(force = events == 1L)
    }

    fun onRootNull(pkg: String, activityClass: String?) {
        rootNull++
        lastPkg = pkg
        lastClass = activityClass.orEmpty()
        lastAt = System.currentTimeMillis()
        lastReason = "根节点为空（ROM 可能限制读取）"
        publish(force = true)
    }

    fun onThrottle(pkg: String) {
        throttled++
        lastPkg = pkg
        lastAt = System.currentTimeMillis()
        lastReason = "节流（距上一条 <70ms）"
        publish()
    }

    fun onVerdict(reason: String, score: Float, ruleId: String?) {
        lastReason = reason
        lastScore = score
        lastRule = ruleId.orEmpty()
        lastAt = System.currentTimeMillis()
        publish(force = true)
    }

    fun onNodes(count: Int) {
        lastNodes = count
        publish(force = events == 1L)
    }

    private fun publish(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - publishedAt < UI_REFRESH_MS) return
        publishedAt = now
        _state.value = RuntimeStats(
            connected = _state.value.connected,
            events = events,
            stateEvents = stateEvents,
            contentEvents = contentEvents,
            lastPkg = lastPkg,
            lastClass = lastClass,
            lastNodes = lastNodes,
            lastAt = lastAt,
            lastReason = lastReason,
            lastScore = lastScore,
            lastRule = lastRule,
            rootNull = rootNull,
            throttled = throttled,
        )
    }

    private companion object { const val UI_REFRESH_MS = 1_000L }
}
