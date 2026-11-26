package com.wumisc.skipper.platform

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.wumisc.skipper.engine.Box
import com.wumisc.skipper.engine.UiNode
import com.wumisc.skipper.engine.WindowSnapshot

/**
 * 平台适配层：把 Android 的无障碍树翻译成引擎认识的纯数据。
 * 引擎不认识 AccessibilityNodeInfo，所有 Android 细节都被挡在这里。
 */
class AndroidSnapshot(
    val window: WindowSnapshot,
    private val index: Map<Int, AccessibilityNodeInfo>,
) {
    fun node(id: Int): AccessibilityNodeInfo? = index[id]
}

object SnapshotMapper {

    private const val MAX_DEPTH = 14
    private const val MAX_NODES = 240

    fun map(pkg: String, activityClass: String?, root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): AndroidSnapshot {
        val nodes = ArrayList<UiNode>(64)
        val index = HashMap<Int, AccessibilityNodeInfo>(64)
        val rect = Rect()

        fun walk(node: AccessibilityNodeInfo, depth: Int, parentClickable: Boolean) {
            if (nodes.size >= MAX_NODES || depth > MAX_DEPTH) return
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                val id = nodes.size
                val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                nodes += UiNode(
                    id = id,
                    text = text,
                    desc = desc,
                    viewId = node.viewIdResourceName,
                    className = node.className?.toString().orEmpty(),
                    bounds = Box(rect.left, rect.top, rect.right, rect.bottom),
                    clickable = node.isClickable,
                    clickableAncestor = parentClickable,
                    depth = depth,
                )
                index[id] = node
            }
            var i = 0
            while (i < node.childCount && nodes.size < MAX_NODES) {
                val child = node.getChild(i)
                if (child != null) {
                    walk(child, depth + 1, parentClickable || node.isClickable)
                }
                i++
            }
        }

        walk(root, 0, false)
        return AndroidSnapshot(
            window = WindowSnapshot(pkg = pkg, activityClass = activityClass, width = screenWidth, height = screenHeight, nodes = nodes),
            index = index,
        )
    }
}
