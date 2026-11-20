package com.wumisc.skipper.range

import com.wumisc.skipper.engine.Box
import com.wumisc.skipper.engine.BuiltinRules
import com.wumisc.skipper.engine.EngineConfig
import com.wumisc.skipper.engine.IdleReason
import com.wumisc.skipper.engine.SkipEngine
import com.wumisc.skipper.engine.UiNode
import com.wumisc.skipper.engine.Verdict
import com.wumisc.skipper.engine.WindowSnapshot
import com.wumisc.skipper.engine.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 靶场：把「一个真实界面」写成一行 TSV，逐行喂给引擎，看它点不点、为什么不点。
 * 新增一条场景只需要往 scenarios.tsv 里加一行，代码不用动。
 */
class ScenarioRange {

    private val config = EngineConfig(windowMillis = 3_000, cooldownMillis = 3_000, maxPerWindow = 2, minScore = 0.8f)

    @Test
    fun `按场景表逐行验证`() {
        val rows = loadRows()
        assertTrue("场景表为空", rows.isNotEmpty())
        val failures = mutableListOf<String>()

        rows.forEachIndexed { index, row ->
            val snapshot = WindowSnapshot(
                pkg = row.pkg,
                width = row.width,
                height = row.height,
                nodes = listOf(
                    UiNode(
                        id = 1,
                        text = row.label,
                        bounds = Box(row.left, row.top, row.right, row.bottom),
                        clickable = row.clickable,
                        clickableAncestor = row.ancestorClickable,
                        depth = 5,
                    ),
                ),
            )
            val verdict = SkipEngine.decide(snapshot, BuiltinRules.all, config, WindowState(openedAt = 0), now = 1_000)
            val actual = describe(verdict)
            if (actual != row.expect) {
                failures += "#${index + 1} ${row.name}: 期望 ${row.expect}，实际 $actual"
            }
        }

        assertTrue("靶场失败 ${failures.size} 条：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun describe(verdict: Verdict): String = when (verdict) {
        is Verdict.Skip -> "skip"
        is Verdict.Idle -> "idle:${verdict.reason}"
    }

    private fun loadRows(): List<Row> {
        val stream = javaClass.classLoader!!.getResourceAsStream("range/scenarios.tsv")
            ?: error("找不到 range/scenarios.tsv")
        return stream.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val cells = line.split("\t")
                Row(
                    name = cells[0],
                    pkg = cells[1],
                    width = cells[2].toInt(),
                    height = cells[3].toInt(),
                    expect = cells[4],
                    label = cells[5].replace("␣", " "),
                    left = cells[6].toInt(),
                    top = cells[7].toInt(),
                    right = cells[8].toInt(),
                    bottom = cells[9].toInt(),
                    clickable = cells[10].toBoolean(),
                    ancestorClickable = cells[11].toBoolean(),
                )
            }
    }

    private data class Row(
        val name: String,
        val pkg: String,
        val width: Int,
        val height: Int,
        val expect: String,
        val label: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val clickable: Boolean,
        val ancestorClickable: Boolean,
    )
}
