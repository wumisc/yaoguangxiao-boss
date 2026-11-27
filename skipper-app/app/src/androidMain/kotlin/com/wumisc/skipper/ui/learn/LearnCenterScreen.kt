package com.wumisc.skipper.ui.learn

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wumisc.skipper.learn.LearnCandidate
import com.wumisc.skipper.learn.LearnUiState
import com.wumisc.skipper.learn.LearnedDomain
import com.wumisc.skipper.ui.components.CardTitle
import com.wumisc.skipper.ui.components.EmptyHint
import com.wumisc.skipper.ui.components.HairLine
import com.wumisc.skipper.ui.components.SettingRow
import com.wumisc.skipper.ui.components.SkipperCard
import com.wumisc.skipper.ui.components.onSurfaceColor
import com.wumisc.skipper.ui.components.secondaryTextColor
import com.wumisc.skipper.ui.theme.Gold
import com.wumisc.skipper.ui.theme.Safe
import com.wumisc.skipper.util.formatClock
import com.wumisc.skipper.util.friendlyName
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 学习中心：只展示 App 自己的学习结果和模块回写状态。
 * 不提供“写入 hosts”按钮——那是模块 WebUI 的职责。
 */
@Composable
fun LearnCenterScreen(
    state: LearnUiState,
    onBack: () -> Unit,
    onLearnNow: () -> Boolean,
    onConfirm: (LearnCandidate) -> Unit,
    onIgnore: (LearnCandidate) -> Unit,
    onRemoveLearned: (LearnedDomain) -> Unit,
    onSetProbe: (Boolean) -> Unit,
    onSetAutoImport: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "← 返回",
                style = MiuixTheme.textStyles.footnote1,
                color = Gold,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "刷新",
                style = MiuixTheme.textStyles.footnote1,
                color = secondaryTextColor(),
                modifier = Modifier.clickable(onClick = onRefresh).padding(vertical = 8.dp),
            )
        }
        Text("学习中心", style = MiuixTheme.textStyles.title1, color = onSurfaceColor(), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "只读类名/清单，不读正文、不读 dex、不联网。命中两次才确认。",
            style = MiuixTheme.textStyles.footnote2,
            color = secondaryTextColor(),
        )

        Spacer(Modifier.height(16.dp))
        SkipperCard {
            CardTitle("模块联动")
            Spacer(Modifier.height(8.dp))
            val m = state.module
            if (m.moduleVersion.isBlank()) {
                Text("模块尚未导入过。装好模块并重启一次，这里会显示结果。", style = MiuixTheme.textStyles.footnote1, color = secondaryTextColor())
            } else {
                Text(
                    "模块 ${m.moduleVersion} · 已导入 ${m.totalLearned} 条",
                    style = MiuixTheme.textStyles.body1,
                    color = onSurfaceColor(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "规则 ${m.rules} 条 · ${if (m.mounted) "hosts 已挂载" else "hosts 未挂载"}" +
                        if (m.lastImportAt > 0) " · 上次 ${formatClock(m.lastImportAt * 1000)}" else "",
                    style = MiuixTheme.textStyles.footnote2,
                    color = secondaryTextColor(),
                )
                if (m.error.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("模块错误：${m.error}", style = MiuixTheme.textStyles.footnote2, color = com.wumisc.skipper.ui.theme.Danger)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            CardTitle("探针")
            Spacer(Modifier.height(2.dp))
            SettingRow(
                title = "智能探针",
                subtitle = "窗口变化时看类名和 viewId；不扫 APK",
                trailing = { Switch(checked = state.config.probe, onCheckedChange = onSetProbe) },
            )
            HairLine()
            SettingRow(
                title = "自动交给模块",
                subtitle = "确认的域名写进 learned.tsv；模块仍需导入",
                trailing = { Switch(checked = state.config.autoImport, onCheckedChange = onSetAutoImport) },
            )
            HairLine()
            SettingRow(
                title = "深度补扫",
                subtitle = "L2 需要充电 + 熄屏 + 空闲；m12-B 才接入，这次不读 dex",
                trailing = {
                    Text(
                        if (state.config.deep) "已开（待接入）" else "关",
                        style = MiuixTheme.textStyles.footnote2,
                        color = secondaryTextColor(),
                    )
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            CardTitle("当前状态")
            Spacer(Modifier.height(8.dp))
            Text(
                "候选 ${state.candidateCount} · 已学 ${state.learned.size} · 队列 ${state.queue.size}",
                style = MiuixTheme.textStyles.body1,
                color = onSurfaceColor(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "电量 ${state.battery}% · 温度档 ${state.thermal}" +
                    if (state.lastLearnAt > 0) " · 上次学习 ${formatClock(state.lastLearnAt * 1000)}" else "",
                style = MiuixTheme.textStyles.footnote2,
                color = secondaryTextColor(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("学习当前 App", primary = true) {
                    val target = state.currentPkg.ifBlank { "先打开目标 App 再回来" }
                    val ok = onLearnNow()
                    Toast.makeText(
                        context,
                        if (ok) "已开始学习 ${friendlyName(target)}" else "先打开目标 App，再回来点学习",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                ActionButton("仅刷新状态") { onRefresh() }
            }
            if (state.currentPkg.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("最近外部前台包：${state.currentPkg}", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
            }
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            CardTitle("候选", trailing = {
                Text(
                    "命中 2 次自动确认",
                    style = MiuixTheme.textStyles.footnote2,
                    color = secondaryTextColor(),
                )
            })
            Spacer(Modifier.height(6.dp))
            val visible = state.candidates.filter { it.state != LearnCandidate.STATE_IGNORED }.take(40)
            if (visible.isEmpty()) {
                EmptyHint("还没有候选。打开一个带广告 SDK 的 App，再回来刷新。")
            } else {
                visible.forEachIndexed { index, c ->
                    if (index > 0) HairLine()
                    CandidateRow(
                        c = c,
                        learned = state.learned.any { it.domain == c.domain && it.pkg == c.pkg },
                        onConfirm = { onConfirm(c) },
                        onIgnore = { onIgnore(c) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            CardTitle("已学域名")
            Spacer(Modifier.height(6.dp))
            if (state.learned.isEmpty()) {
                EmptyHint("确认后的域名会出现在这里，并写进 learned.tsv。")
            } else {
                state.learned.take(60).forEachIndexed { index, item ->
                    if (index > 0) HairLine()
                    LearnedRow(item = item, onRemove = { onRemoveLearned(item) })
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "说明：学习结果只写 App 私有目录。模块导入前还会过保护域、白名单、通配和裸域名；默认不自动写 hosts。",
            style = MiuixTheme.textStyles.footnote2,
            color = secondaryTextColor(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CandidateRow(
    c: LearnCandidate,
    learned: Boolean,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.domain, style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
            Spacer(Modifier.height(2.dp))
            Text(
                "${c.sdk} · ${friendlyName(c.pkg)} · ${c.count}/2 · ${c.source}",
                style = MiuixTheme.textStyles.footnote2,
                color = secondaryTextColor(),
            )
        }
        if (learned) {
            Text("已学", style = MiuixTheme.textStyles.footnote2, color = Safe)
        } else {
            SmallButton("确认", Gold, onConfirm)
        }
        Spacer(Modifier.width(6.dp))
        SmallButton("忽略", secondaryTextColor(), onIgnore)
    }
}

@Composable
private fun LearnedRow(item: LearnedDomain, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.domain, style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
            Spacer(Modifier.height(2.dp))
            Text(
                "${item.sdk} · ${friendlyName(item.pkg)} · ${item.source}",
                style = MiuixTheme.textStyles.footnote2,
                color = secondaryTextColor(),
            )
        }
        SmallButton("移出", secondaryTextColor(), onRemove)
    }
}

@Composable
private fun SmallButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable(onClick = onClick)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, style = MiuixTheme.textStyles.footnote2, color = color)
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    val color = if (primary) Gold else secondaryTextColor()
    Box(
        Modifier
            .clickable(onClick = onClick)
            .background(color.copy(alpha = if (primary) 0.16f else 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, style = MiuixTheme.textStyles.footnote1, color = color, fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal)
    }
}
