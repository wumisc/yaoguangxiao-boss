package com.wumisc.skipper.range

import com.wumisc.skipper.engine.Advisor
import com.wumisc.skipper.engine.Box
import com.wumisc.skipper.engine.EngineConfig
import com.wumisc.skipper.engine.UiNode
import com.wumisc.skipper.engine.WindowSnapshot
import com.wumisc.skipper.engine.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 侦察兵靶场：只许在「开屏窗口 + 顶部 + 像跳过 + 不危险」时才提建议。 */
class AdvisorRange {

    private val config = EngineConfig(windowMillis = 3_000, deniedPackages = listOf("com.coolapk.market"))
    private val state = WindowState(openedAt = 1_000)

    private fun window(nodes: List<UiNode>, pkg: String = "com.demo.app") =
        WindowSnapshot(pkg = pkg, activityClass = "com.demo.app.SplashActivity", width = 1080, height = 2400, nodes = nodes)

    private fun node(
        text: String,
        left: Int = 880,
        top: Int = 150,
        right: Int = 1010,
        bottom: Int = 230,
        clickable: Boolean = true,
    ) = UiNode(id = 1, text = text, bounds = Box(left, top, right, bottom), clickable = clickable)

    @Test
    fun `开屏里看到没规则的跳过 会提建议`() {
        val s = Advisor.suspect(window(listOf(node("跳过 3s"))), state, config, now = 2_000)
        assertNotNull(s)
        assertEquals("跳过 3s", s!!.label)
        assertEquals("SplashActivity", s.activity)
    }

    @Test
    fun `超过工作窗口 不再建议`() {
        assertNull(Advisor.suspect(window(listOf(node("跳过"))), state, config, now = 9_000))
    }

    @Test
    fun `底部的跳过 不算开屏按钮`() {
        assertNull(Advisor.suspect(window(listOf(node("跳过", top = 2000, bottom = 2080))), state, config, now = 2_000))
    }

    @Test
    fun `裸关闭 不碰`() {
        assertNull(Advisor.suspect(window(listOf(node("关闭"))), state, config, now = 2_000))
    }

    @Test
    fun `登录跳过 是危险词 不碰`() {
        assertNull(Advisor.suspect(window(listOf(node("跳过登录"))), state, config, now = 2_000))
    }

    @Test
    fun `用户钉死的包 提都不提`() {
        assertNull(
            Advisor.suspect(
                window(listOf(node("跳过")), pkg = "com.coolapk.market"), state, config, now = 2_000,
            )
        )
    }

    @Test
    fun `点不动的文字 不算候选`() {
        assertNull(
            Advisor.suspect(
                window(listOf(node("跳过", clickable = false))), state, config, now = 2_000,
            )
        )
    }
}
