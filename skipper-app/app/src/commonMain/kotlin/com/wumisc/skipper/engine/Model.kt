package com.wumisc.skipper.engine

/** 屏幕坐标下的矩形。只用整数，避免浮点抖动带来的判定漂移。 */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()
    val centerY: Int get() = (top + bottom) / 2
    val centerX: Int get() = (left + right) / 2
}

/**
 * 无障碍树的一帧快照节点。
 * 引擎不认识 AccessibilityNodeInfo，只认识这些字段——所以引擎能在 JVM 上跑单测。
 */
data class UiNode(
    val id: Int,
    val text: String? = null,
    val desc: String? = null,
    val viewId: String? = null,
    val className: String = "",
    val bounds: Box,
    val clickable: Boolean = false,
    /** 自身不可点，但有可点的祖先（后面那条链能点）。 */
    val clickableAncestor: Boolean = false,
    val depth: Int = 0,
) {
    val label: String get() = text?.takeIf { it.isNotBlank() } ?: desc ?: ""
}

/** 一次窗口状态（一个包名 + 一个界面）。 */
data class WindowSnapshot(
    val pkg: String,
    val activityClass: String? = null,
    val width: Int,
    val height: Int,
    val nodes: List<UiNode> = emptyList(),
) {
    val isLandscape: Boolean get() = width > height
}
