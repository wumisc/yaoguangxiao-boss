package com.wumisc.skipper.engine

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SkipEngineTest {

    private val cfg = EngineConfig(windowMillis = 3000, cooldownMillis = 3000, maxPerWindow = 2, minScore = 0.8f)
    private val rules = BuiltinRules.all
    private val fresh = WindowState(openedAt = 0)

    private fun win(pkg: String = "com.qiyi.video", nodes: List<UiNode>, w: Int = 1080, h: Int = 2400) =
        WindowSnapshot(pkg = pkg, width = w, height = h, nodes = nodes)

    private fun skipBtn(id: Int = 1, left: Int = 880, top: Int = 160, right: Int = 1010, bottom: Int = 240, text: String = "跳过", clickable: Boolean = true) =
        UiNode(id = id, text = text, bounds = Box(left, top, right, bottom), clickable = clickable, clickableAncestor = !clickable, depth = 6)

    @Test
    fun `右上角小字跳过 会被点`() {
        val v = SkipEngine.decide(win(pkg = "com.example.news", nodes = listOf(skipBtn())), rules, cfg, fresh, now = 1000)
        assertTrue("应当命中：$v", v is Verdict.Skip)
        assertEquals("general.skip", (v as Verdict.Skip).rule.id)
    }

    @Test
    fun `视频包命中视频规则`() {
        val v = SkipEngine.decide(win(nodes = listOf(skipBtn(text = "关闭广告"))), rules, cfg, fresh, now = 1000)
        assertTrue(v is Verdict.Skip)
        assertEquals(RulePack.VIDEO, (v as Verdict.Skip).rule.pack)
    }

    @Test
    fun `屏幕下半部分的跳过 不点`() {
        val v = SkipEngine.decide(win(nodes = listOf(skipBtn(top = 2000, bottom = 2080))), rules, cfg, fresh, now = 1000)
        assertTrue(v is Verdict.Idle)
    }

    @Test
    fun `跳过登录 不点`() {
        val v = SkipEngine.decide(win(nodes = listOf(skipBtn(text = "跳过登录"))), rules, cfg, fresh, now = 1000)
        assertEquals(IdleReason.NO_NODE, (v as Verdict.Idle).reason)
    }

    @Test
    fun `支付类包 一律不碰`() {
        val v = SkipEngine.decide(win(pkg = "com.unionpay", nodes = listOf(skipBtn())), rules, cfg, fresh, now = 1000)
        assertEquals(IdleReason.BLOCKED_PACKAGE, (v as Verdict.Idle).reason)
    }

    @Test
    fun `窗口超时后不再动作`() {
        val v = SkipEngine.decide(win(nodes = listOf(skipBtn())), rules, cfg, fresh, now = 5000)
        assertEquals(IdleReason.WINDOW_EXPIRED, (v as Verdict.Idle).reason)
    }

    @Test
    fun `同一窗口第二次要等冷却`() {
        val st = WindowState(openedAt = 0, hits = 1, lastHitAt = 900)
        val v = SkipEngine.decide(win(nodes = listOf(skipBtn())), rules, cfg, st, now = 1500)
        assertEquals(IdleReason.COOLDOWN, (v as Verdict.Idle).reason)
    }

    @Test
    fun `超过单窗上限就停手`() {
        val st = WindowState(openedAt = 0, hits = 2, lastHitAt = 900)
        val v = SkipEngine.decide(win(nodes = listOf(skipBtn())), rules, cfg, st, now = 2000)
        assertEquals(IdleReason.WINDOW_LIMIT, (v as Verdict.Idle).reason)
    }

    @Test
    fun `两个候选取分高的`() {
        val weak = UiNode(id = 2, text = "跳过", bounds = Box(800, 400, 1020, 480), clickable = true, clickableAncestor = true, depth = 4)
        val strong = skipBtn(id = 1)
        val v = SkipEngine.decide(win(nodes = listOf(weak, strong)), rules, cfg, fresh, now = 1000)
        assertEquals(1, (v as Verdict.Skip).node.id.toLong())
    }

    @Test
    fun `整块不可点的文字 不点`() {
        val dead = skipBtn(clickable = false).copy(clickableAncestor = false)
        val v = SkipEngine.decide(win(nodes = listOf(dead)), rules, cfg, fresh, now = 1000)
        assertTrue(v is Verdict.Idle)
    }

    @Test
    fun `整屏大小的跳过按钮 视为误判`() {
        val huge = UiNode(id = 3, text = "跳过", bounds = Box(0, 0, 1080, 300), clickable = true, depth = 2)
        val v = SkipEngine.decide(win(nodes = listOf(huge)), rules, cfg, fresh, now = 1000)
        assertTrue(v is Verdict.Idle)
    }

    @Test
    fun `系统界面 不点`() {
        val v = SkipEngine.decide(win(pkg = "com.android.systemui", nodes = listOf(skipBtn())), rules, cfg, fresh, now = 1000)
        assertEquals(IdleReason.BLOCKED_PACKAGE, (v as Verdict.Idle).reason)
    }
}
