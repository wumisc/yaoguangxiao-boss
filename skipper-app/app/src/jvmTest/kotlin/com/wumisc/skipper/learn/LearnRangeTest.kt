package com.wumisc.skipper.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnRangeTest {

    @Test
    fun `SDK 前缀只匹配包边界，不匹配子串`() {
        val c = SdkCatalog(
            listOf(
                SdkEntry("com.bytedance.sdk.openadsdk", "pangle", "pangolin-sdk-toutiao.com"),
                SdkEntry("com.example.ad", "demo", "demo-ads.example.com"),
            ),
        )
        assertEquals(1, c.match("com.bytedance.sdk.openadsdk.TTAdSdk").size)
        assertEquals(1, c.match("com.bytedance.sdk.openadsdk:id/ad_container").size)
        assertTrue(c.match("com.notbytedance.sdk.openadsdk.TTAdSdk").isEmpty())
        assertTrue(c.match("com.example.advertiser.Foo").isEmpty())
    }

    @Test
    fun `SDK 表解析跳过坏行与保护域`() {
        val c = SdkCatalog.parse(
            """
            # header
            com.foo.sdk	demo	demo-ads.example.com
            bad	line	no-dot
            com.bar.sdk	demo	bad domain
            """.trimIndent(),
        )
        assertEquals(1, c.matchAll(listOf("com.foo.sdk.AdView")).size)
    }

    @Test
    fun `候选 TSV 往返`() {
        val list = listOf(
            LearnCandidate("ads.example.com", "demo", "com.demo.app", 42, "ui", 2, 1764163200),
            LearnCandidate("track.example.com", "demo", "com.demo.app", 42, "manifest", 1, 1764163201),
        )
        val text = LearnTsv.encodeCandidates(list)
        val back = LearnTsv.decodeCandidates(text)
        assertEquals(list, back)
    }

    @Test
    fun `已学 TSV 往返并保留来源`() {
        val list = listOf(
            LearnedDomain("ads.example.com", "demo", "com.demo.app", "ui", 0.92, 1764163200),
        )
        assertEquals(list, LearnTsv.decodeLearned(LearnTsv.encodeLearned(list)))
    }

    @Test
    fun `队列 TSV 往返`() {
        val list = listOf(LearnQueueItem("com.demo.app", 42, "no-l0-l1", 1764163200, 0))
        assertEquals(list, LearnTsv.decodeQueue(LearnTsv.encodeQueue(list)))
    }

    @Test
    fun `坏域名不会被解出来`() {
        val text = """
            # domain	sdk	pkg	versionCode	source	count	lastSeenAt	state
            no-dot	demo	com.demo.app	42	ui	1	1764163200	candidate
            *.wild.example.com	demo	com.demo.app	42	ui	1	1764163200	candidate
            good.example.com	demo	com.demo.app	42	ui	1	1764163200	candidate
        """.trimIndent()
        assertEquals(1, LearnTsv.decodeCandidates(text).size)
    }

    @Test
    fun `默认配置并不自动开深度补扫`() {
        val c = LearnConfig()
        assertTrue(c.probe)
        assertTrue(c.autoImport)
        assertFalse(c.autoApply)
        assertFalse(c.deep)
    }
}
