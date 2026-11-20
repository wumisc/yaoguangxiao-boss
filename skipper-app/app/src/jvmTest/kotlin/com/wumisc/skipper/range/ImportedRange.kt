package com.wumisc.skipper.range

import com.wumisc.skipper.data.ImportedCodec
import com.wumisc.skipper.engine.Box
import com.wumisc.skipper.engine.EngineConfig
import com.wumisc.skipper.engine.Safety
import com.wumisc.skipper.engine.SkipEngine
import com.wumisc.skipper.engine.UiNode
import com.wumisc.skipper.engine.Verdict
import com.wumisc.skipper.engine.WindowSnapshot
import com.wumisc.skipper.engine.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 专项包的靶场：不是测「能不能解析」，而是测三件会出人命的事——
 *  1. 黑名单包绝不进包；
 *  2. 危险文字绝不进正则；
 *  3. 解析出来的规则，喂给引擎真能点中「跳过」。
 */
class ImportedRange {

    private val payload: String by lazy {
        File("src/androidMain/assets/rules/imported.tsv").readText()
    }

    private val rules by lazy { ImportedCodec.decode(payload) }

    @Test
    fun `专项包能解析且规模合理`() {
        assertTrue("专项包为空或没进 assets", rules.isNotEmpty())
        assertTrue("包数异常：${rules.size}", rules.size in 40..400)
        assertTrue("文字/ id 都空", rules.all { it.textPatterns.isNotEmpty() || it.viewIdPatterns.isNotEmpty() })
        assertEquals("id 不应重复", rules.size, rules.map { it.id }.distinct().size)
    }

    @Test
    fun `专项包不含黑名单应用`() {
        val bad = rules.filter { Safety.isBlocked(it.packagePatterns.first()) }
        assertTrue("混进了黑名单包：${bad.map { it.id }}", bad.isEmpty())
    }

    @Test
    fun `专项包的文字正则不会点危险按钮`() {
        val corpus = listOf(
            "跳过登录", "跳过 5", "跳过注册领5元", "跳过支付", "跳过下载安装",
            "跳过授权", "跳过绑定银行卡", "跳过实名认证", "跳过开通会员", "跳过预约",
        )
        val hits = rules.flatMap { rule ->
            rule.textPatterns.filter { p -> Regex(p).matches("跳过登录") }
                .map { "${rule.id} → $it" }
        }
        assertTrue("有正则会点「跳过登录」：$hits", hits.isEmpty())

        val safe = rules.filter { r -> r.textPatterns.any { Regex(it).matches("跳过 5") } }
        assertTrue("连「跳过 5」都不认，规则写坏了", safe.isNotEmpty())
        assertTrue("危险语料不应被任何规则命中", corpus.none { word ->
            rules.any { r -> r.textPatterns.any { Regex(it).matches(word) } && word != "跳过 5" }
        })
    }

    @Test
    fun `按 id 也能点中开屏跳过`() {
        val rule = rules.firstOrNull { it.viewIdPatterns.isNotEmpty() }
        assertTrue("专项包里没有任何 id 规则", rule != null)
        val pkg = rule!!.packagePatterns.first()
        val name = rule.viewIdPatterns.first()
            .removePrefix("^\\s*")
            .removeSuffix("\\s*$")
            .replace("\\", "")
        val nodes = listOf(
            UiNode(
                id = 1,
                viewId = "$pkg:id/$name",
                bounds = Box(880, 150, 1010, 230),
                clickable = true,
                depth = 6,
            ),
        )
        val verdict = SkipEngine.decide(
            WindowSnapshot(pkg = pkg, width = 1080, height = 2400, nodes = nodes),
            rules,
            EngineConfig(),
            WindowState(openedAt = 0),
            now = 1_000,
        )
        assertTrue("id 规则没点中：$verdict", verdict is Verdict.Skip)
    }

    @Test
    fun `专项包里没有屏蔽类语义`() {
        val forbidden = listOf("remove", "block", "filter", "hosts")
        val suspicious = rules.filter { r -> r.textPatterns.any { p -> forbidden.any { p.contains(it) } } }
        assertTrue("混进了屏蔽/拦截语义：${suspicious.map { it.id }}", suspicious.isEmpty())
    }
}
