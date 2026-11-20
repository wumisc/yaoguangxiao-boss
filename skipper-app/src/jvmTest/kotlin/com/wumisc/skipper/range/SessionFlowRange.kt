package com.wumisc.skipper.range

import com.wumisc.skipper.engine.Box
import com.wumisc.skipper.engine.BuiltinRules
import com.wumisc.skipper.engine.EngineConfig
import com.wumisc.skipper.engine.IdleReason
import com.wumisc.skipper.engine.SkipSession
import com.wumisc.skipper.engine.UiNode
import com.wumisc.skipper.engine.Verdict
import com.wumisc.skipper.engine.WindowSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时序靶场：不看单个界面，看「连着发生的一串事」——冷却、上限、切窗、节流、超时。
 * 这些才是真机上最容易出问题的地方。
 */
class SessionFlowRange {

    private val skipNode = UiNode(id = 7, text = "跳过", bounds = Box(880, 150, 1010, 230), clickable = true, depth = 4)

    private fun window(pkg: String = "com.qiyi.video") = WindowSnapshot(
        pkg = pkg,
        width = 1080,
        height = 2400,
        nodes = listOf(skipNode),
    )

    private fun session(config: EngineConfig = EngineConfig(windowMillis = 10_000, cooldownMillis = 3_000, maxPerWindow = 2, minScore = 0.8f)) =
        SkipSession(rules = { BuiltinRules.all }, config = { config })

    @Test
    fun `同一窗口 点一次后立刻冷却`() {
        val s = session()
        val first = s.onEvent("com.qiyi.video", "AdActivity", window(), now = 100)
        assertTrue(first is Verdict.Skip)
        s.markClicked(100)

        val second = s.onEvent("com.qiyi.video", "AdActivity", window(), now = 1_000)
        assertEquals(IdleReason.COOLDOWN, (second as Verdict.Idle).reason)
    }

    @Test
    fun `冷却结束后可以再点 但到上限就停`() {
        val s = session()
        s.onEvent("com.qiyi.video", "AdActivity", window(), now = 100)
        s.markClicked(100)

        val again = s.onEvent("com.qiyi.video", "AdActivity", window(), now = 3_500)
        assertTrue(again is Verdict.Skip)
        s.markClicked(3_500)

        val third = s.onEvent("com.qiyi.video", "AdActivity", window(), now = 7_500)
        assertEquals(IdleReason.WINDOW_LIMIT, (third as Verdict.Idle).reason)
    }

    @Test
    fun `换一个界面 计数清零可以重新点`() {
        val s = session()
        s.onEvent("com.qiyi.video", "AdActivity", window(), now = 100)
        s.markClicked(100)
        s.onEvent("com.qiyi.video", "AdActivity", window(), now = 3_500)
        s.markClicked(3_500)
        assertEquals(IdleReason.WINDOW_LIMIT, (s.onEvent("com.qiyi.video", "AdActivity", window(), now = 7_000) as Verdict.Idle).reason)

        val newWindow = s.onEvent("com.qiyi.video", "MainActivity", window(), now = 7_200)
        assertTrue(newWindow is Verdict.Skip)
    }

    @Test
    fun `超过工作窗口就不再动作`() {
        val s = session(config = EngineConfig(windowMillis = 3_000, cooldownMillis = 3_000, maxPerWindow = 2, minScore = 0.8f))
        s.onEvent("com.qiyi.video", "AdActivity", window(), now = 100)
        val late = s.onEvent("com.qiyi.video", "AdActivity", window(), now = 4_000)
        assertEquals(IdleReason.WINDOW_EXPIRED, (late as Verdict.Idle).reason)
    }

    @Test
    fun `两条事件贴太近 第二条被节流丢掉`() {
        val s = session()
        s.onEvent("com.qiyi.video", "AdActivity", window(), now = 100)
        assertNull(s.onEvent("com.qiyi.video", "AdActivity", window(), now = 150))
    }

    @Test
    fun `命中后 记录里能查到分数`() {
        val s = session()
        val v = s.onEvent("com.qiyi.video", "AdActivity", window(), now = 100) as Verdict.Skip
        assertEquals(7, v.node.id.toLong())
        assertTrue("分数应过 0.8 阈值", v.score >= 0.8f)
    }

    @Test
    fun `总开关之外的规则全关 就只剩用户规则`() {
        // 这里验证的是「规则集合为空 = 不动作」，不涉及开关本身
        val s = SkipSession(rules = { emptyList() }, config = { EngineConfig() })
        val v = s.onEvent("com.qiyi.video", "AdActivity", window(), now = 100)
        assertEquals(IdleReason.NO_NODE, (v as Verdict.Idle).reason)
    }
}
