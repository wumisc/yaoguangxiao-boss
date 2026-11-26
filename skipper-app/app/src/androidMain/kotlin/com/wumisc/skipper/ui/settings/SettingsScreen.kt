package com.wumisc.skipper.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wumisc.skipper.data.Settings
import com.wumisc.skipper.ui.components.CardTitle
import com.wumisc.skipper.ui.components.HairLine
import com.wumisc.skipper.ui.components.Label
import com.wumisc.skipper.ui.components.SettingRow
import com.wumisc.skipper.ui.components.SkipperCard
import com.wumisc.skipper.ui.components.Stepper
import com.wumisc.skipper.ui.components.onSurfaceColor
import com.wumisc.skipper.ui.components.secondaryTextColor
import com.wumisc.skipper.ui.theme.Gold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    settings: Settings,
    versionName: String,
    onEdit: ((Settings) -> Settings) -> Unit,
    onClearHistory: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("设置", style = MiuixTheme.textStyles.title1, color = onSurfaceColor(), fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(16.dp))
        SkipperCard {
            CardTitle("反应速度")
            Spacer(Modifier.height(6.dp))
            SettingRow(
                title = "工作窗口",
                subtitle = "切到新界面后，多久之内允许动作；开屏加载慢可调大",
                trailing = {
                    Stepper(
                        value = "${(settings.windowMillis / 1000f)} 秒",
                        canDecrease = settings.windowMillis > 1_500,
                        canIncrease = settings.windowMillis < 8_000,
                        onDecrease = { onEdit { it.copy(windowMillis = it.windowMillis - 500) } },
                        onIncrease = { onEdit { it.copy(windowMillis = it.windowMillis + 500) } },
                    )
                },
            )
            HairLine()
            SettingRow(
                title = "冷却",
                subtitle = "同一次开屏，两次点击之间至少隔多久",
                trailing = {
                    Stepper(
                        value = "${settings.cooldownMillis / 1000} 秒",
                        canDecrease = settings.cooldownMillis > 1_000,
                        canIncrease = settings.cooldownMillis < 6_000,
                        onDecrease = { onEdit { it.copy(cooldownMillis = it.cooldownMillis - 1_000) } },
                        onIncrease = { onEdit { it.copy(cooldownMillis = it.cooldownMillis + 1_000) } },
                    )
                },
            )
            HairLine()
            SettingRow(
                title = "单次上限",
                subtitle = "一个界面最多点几次，防止连环误点",
                trailing = {
                    Stepper(
                        value = "${settings.maxPerWindow} 次",
                        canDecrease = settings.maxPerWindow > 1,
                        canIncrease = settings.maxPerWindow < 4,
                        onDecrease = { onEdit { it.copy(maxPerWindow = it.maxPerWindow - 1) } },
                        onIncrease = { onEdit { it.copy(maxPerWindow = it.maxPerWindow + 1) } },
                    )
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            CardTitle("手感与外观")
            Spacer(Modifier.height(4.dp))
            SettingRow("点掉时震动", "很轻的一下，纯提示", trailing = {
                Switch(checked = settings.vibrateOnSkip, onCheckedChange = { on -> onEdit { it.copy(vibrateOnSkip = on) } })
            })
            HairLine()
            SettingRow(
                "主题",
                "默认跟随系统，浅色/深色都能手动钉住",
                trailing = {
                    Box(
                        Modifier
                            .clickable { onEdit { it.copy(themeMode = (it.themeMode + 1) % 3) } }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Text(themeLabel(settings.themeMode), style = MiuixTheme.textStyles.footnote2, color = Gold)
                    }
                },
            )
            HairLine()
            SettingRow("保留记录", "关掉后只记在内存，退出即清空", trailing = {
                Switch(checked = settings.keepHistory, onCheckedChange = { on -> onEdit { it.copy(keepHistory = on) } })
            })
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard(onClick = onOpenDebug) {
            CardTitle("调试")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("调试日志", style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
                    Spacer(Modifier.height(2.dp))
                    Label("记下每次事件、候选与拒绝原因，只在本机内存里；点这行进去看")
                }
                Switch(checked = settings.debugLog, onCheckedChange = { on -> onEdit { it.copy(debugLog = on) } })
            }
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard(onClick = onOpenAccessibility) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("无障碍服务", style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
                    Spacer(Modifier.height(2.dp))
                    Label("系统里手动开一次，之后不用管")
                }
                Text("去系统设置", style = MiuixTheme.textStyles.footnote2, color = Gold)
            }
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("清空记录", style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
                    Spacer(Modifier.height(2.dp))
                    Label("只删日志，不动规则和设置")
                }
                Text("清空", style = MiuixTheme.textStyles.footnote2, color = com.wumisc.skipper.ui.theme.Danger)
            }
        }

        Spacer(Modifier.height(12.dp))
        SkipperCard {
            CardTitle("关于")
            Spacer(Modifier.height(8.dp))
            Label("姚广孝 · 跳过  $versionName")
            Spacer(Modifier.height(6.dp))
            Label("只点跳过、不屏蔽广告、不注入界面、不联网。\n金融 / 支付 / 政务 / 系统界面永不动作。仅供学习研究。")
        }
        Spacer(Modifier.height(28.dp))
    }
}

private fun themeLabel(mode: Int): String = when (mode) {
    1 -> "浅色"
    2 -> "深色"
    else -> "跟随系统"
}
