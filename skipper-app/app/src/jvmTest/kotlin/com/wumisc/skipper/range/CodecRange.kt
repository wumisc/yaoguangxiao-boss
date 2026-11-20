package com.wumisc.skipper.range

import com.wumisc.skipper.data.HitCodec
import com.wumisc.skipper.data.RuleCodec
import com.wumisc.skipper.data.SkipHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 存储靶场：记录和规则都要能原样存、原样读，包括中文和分隔符。 */
class CodecRange {

    @Test
    fun `用户规则 存进去再读出来 一模一样`() {
        val rules = listOfNotNull(
            RuleCodec.userRule("", "跳过", "user.1"),
            RuleCodec.userRule("com.example.app", "跳过广告|关闭", "user.2"),
            RuleCodec.userRule("com.example.video", "Skip Ad", "user.3"),
        )
        val payload = RuleCodec.encode(rules)
        val back = RuleCodec.decode(payload)

        assertEquals(rules.size, back.size)
        assertEquals(rules[0].title, back[0].title)
        assertEquals("*", back[0].packagePatterns.first())
        // 文字里带 | 也不能把字段拆坏
        assertEquals("跳过广告|关闭", back[1].title)
        assertEquals("com.example.app", back[1].packagePatterns.first())
        assertEquals("Skip Ad", back[2].title)
    }

    @Test
    fun `空文字不生成规则`() {
        assertNull(RuleCodec.userRule("com.a.b", "   ", "user.9"))
    }

    @Test
    fun `脏数据不会让规则库炸掉`() {
        val back = RuleCodec.decode("garbage\nuser.1|onlytwo|\n\n")
        assertTrue(back.isEmpty())
    }

    @Test
    fun `命中记录 存进去再读出来 一模一样`() {
        val hit = SkipHit(at = 1_700_000_000_000, pkg = "com.qiyi.video", ruleId = "video.com.qiyi.video", text = "跳过|广告", score = 0.94f)
        val line = HitCodec.encode(hit)
        val back = HitCodec.decode(line)
        assertTrue(back != null)
        assertEquals(hit, back)
    }

    @Test
    fun `记录按新到旧读 并且有上限`() {
        val lines = (1..5).map {
            HitCodec.encode(SkipHit(at = it.toLong(), pkg = "com.a.$it", ruleId = "r", text = "跳过", score = 0.9f))
        }
        val back = HitCodec.decodeAll(lines, max = 3)
        assertEquals(3, back.size)
        assertEquals(5L, back[0].at)
        assertEquals(4L, back[1].at)
    }

    @Test
    fun `坏行直接跳过 不抛异常`() {
        assertNull(HitCodec.decode("not|enough"))
        assertNull(HitCodec.decode("abc|def|ghi|jkl|mno"))
    }
}
