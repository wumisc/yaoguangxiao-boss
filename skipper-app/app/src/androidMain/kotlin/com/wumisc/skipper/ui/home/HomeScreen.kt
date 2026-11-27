package com.wumisc.skipper.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wumisc.skipper.data.Settings
import com.wumisc.skipper.data.RuntimeStats
import com.wumisc.skipper.data.SkipHit
import com.wumisc.skipper.learn.LearnUiState
import com.wumisc.skipper.ui.components.CardTitle
import com.wumisc.skipper.ui.components.Dot
import com.wumisc.skipper.ui.components.EmptyHint
import com.wumisc.skipper.ui.components.Label
import com.wumisc.skipper.ui.components.Pill
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

@Composable
fun HomeScreen(
    settings: Settings,
    connected: Boolean,
    alive: Boolean,
    todayCount: Int,
    weekCount: Int,
    totalCount: Int,
    latest: SkipHit?,
    stats: RuntimeStats,
    learn: LearnUiState,
    onEnabledChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenLearn: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Header(connected = connected, alive = alive)

        Spacer(Modifier.height(18.dp))
        SkipperCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkipRing(todayCount)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("本周", weekCount.toString())
                    Metric("累计", totalCount.toString())
                    Metric("工作窗口", "${(settings.windowMillis / 1000f).strip()} 秒")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            SettingRow(
                title = if (settings.enabled) "正在工作" else "已暂停",
                subtitle = if (settings.enabled) {
                    "切到新界面后 ${(settings.windowMillis / 1000f).strip()} 秒内找「跳过」"
                } else {
                    "一切点击都停下，只剩记录"
                },
                trailing = {
                    Switch(checked = settings.enabled, onCheckedChange = onEnabledChange)
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        DiagnosticsCard(stats)

        Spacer(Modifier.height(12.dp))
        SkipperCard(onClick = onOpenLearn) {
            CardTitle("学习中心", trailing = {
                Text("进入", style = MiuixTheme.textStyles.footnote2, color = Gold)
            })
            Spacer(Modifier.height(8.dp))
            Text(
                "候选 ${learn.candidateCount} · 已学 ${learn.learned.size}" +
                    if (learn.queue.isNotEmpty()) " · 队列 ${learn.queue.size}" else "",
                style = MiuixTheme.textStyles.body1,
                color = onSurfaceColor(),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (learn.module.moduleVersion.isBlank()) {
                    "模块未连接；先装模块并重启一次"
                } else {
                    "模块 ${learn.module.moduleVersion} · 已导入 ${learn.module.totalLearned} 条"
                },
                style = MiuixTheme.textStyles.footnote2,
                color = secondaryTextColor(),
            )
        }

        if (!connected) {
            Spacer(Modifier.height(12.dp))
            SkipperCard(onClick = onOpenAccessibility) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(com.wumisc.skipper.ui.theme.Danger, 9)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("无障碍还没开", style = MiuixTheme.textStyles.body1, color = onSurfaceColor(), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "系统限制，必须你手动开一次。点这里直接跳过去。",
                            style = MiuixTheme.textStyles.footnote2,
                            color = secondaryTextColor(),
                        )
                    }
                    Text("去开启", style = MiuixTheme.textStyles.footnote1, color = Gold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            CardTitle("最近一次")
            Spacer(Modifier.height(10.dp))
            if (latest == null) {
                EmptyHint("还没有点过。下次开 App 时留意一下。")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(Gold, 9)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friendlyName(latest.pkg), style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
                        Spacer(Modifier.height(2.dp))
                        Text("点掉了「${latest.text}」", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
                    }
                    Text(formatClock(latest.at), style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "只点「跳过」，不屏蔽广告、不注入别人的界面。\n金融、支付、政务、系统设置里永不动作。",
            style = MiuixTheme.textStyles.footnote2,
            color = secondaryTextColor(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosticsCard(stats: RuntimeStats) {
    SkipperCard {
        CardTitle("运行诊断")
        Spacer(Modifier.height(8.dp))
        Text(
            "事件 ${stats.events} · 状态 ${stats.stateEvents} · 内容 ${stats.contentEvents}",
            style = MiuixTheme.textStyles.footnote1,
            color = onSurfaceColor(),
        )
        Spacer(Modifier.height(4.dp))
        if (stats.events == 0L) {
            Text(
                "还没有收到无障碍事件。先确认服务已开启，再触发一次开屏。",
                style = MiuixTheme.textStyles.footnote2,
                color = secondaryTextColor(),
            )
        } else {
            Text(
                "最后：${stats.lastPkg.ifBlank { "—" }}",
                style = MiuixTheme.textStyles.footnote2,
                color = secondaryTextColor(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "节点 ${stats.lastNodes} · ${stats.lastReason}",
                style = MiuixTheme.textStyles.footnote2,
                color = secondaryTextColor(),
            )
            if (stats.lastScore > 0f || stats.lastRule.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "分数 ${"%.2f".format(stats.lastScore)} · 规则 ${stats.lastRule.ifBlank { "—" }}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = secondaryTextColor(),
                )
            }
            if (stats.rootNull > 0 || stats.throttled > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "根节点为空 ${stats.rootNull} 次 · 节流 ${stats.throttled} 次",
                    style = MiuixTheme.textStyles.footnote2,
                    color = secondaryTextColor(),
                )
            }
        }
    }
}

@Composable
private fun Header(connected: Boolean, alive: Boolean) {
    val color = when {
        !connected -> com.wumisc.skipper.ui.theme.Danger
        !alive -> Gold
        else -> Safe
    }
    val label = when {
        !connected -> "未连接"
        !alive -> "等待心跳"
        else -> "运行中"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("跳过", style = MiuixTheme.textStyles.title1, color = onSurfaceColor(), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("姚广孝 BOSS 的配套小件", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
        }
        Pill(label, color)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MiuixTheme.textStyles.title3, color = onSurfaceColor(), fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
    }
}

@Composable
private fun SkipRing(todayCount: Int) {
    val target = (todayCount / 20f).coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, tween(500), label = "ring")
    val track = MiuixTheme.colorScheme.dividerLine
    val accent = Gold
    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track, startAngle = 140f, sweepAngle = 260f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent, startAngle = 140f, sweepAngle = 260f * progress, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(todayCount.toString(), style = MiuixTheme.textStyles.headline1, color = onSurfaceColor(), fontWeight = FontWeight.SemiBold)
            Text("今日跳过", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
        }
    }
}

private fun Float.strip(): String = if (this % 1f == 0f) this.toInt().toString() else this.toString()
