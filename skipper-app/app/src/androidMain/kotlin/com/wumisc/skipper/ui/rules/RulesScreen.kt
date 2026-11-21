package com.wumisc.skipper.ui.rules

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wumisc.skipper.data.Settings
import com.wumisc.skipper.data.Suggestion
import com.wumisc.skipper.engine.BuiltinRules
import com.wumisc.skipper.engine.RulePack
import com.wumisc.skipper.engine.SkipRule
import com.wumisc.skipper.ui.components.CardTitle
import com.wumisc.skipper.ui.components.EmptyHint
import com.wumisc.skipper.ui.components.HairLine
import com.wumisc.skipper.ui.components.Label
import com.wumisc.skipper.ui.components.SkipperCard
import com.wumisc.skipper.ui.components.onSurfaceColor
import com.wumisc.skipper.ui.components.secondaryTextColor
import com.wumisc.skipper.ui.theme.Gold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RulesScreen(
    settings: Settings,
    userRules: List<SkipRule>,
    importedCount: Int,
    denyList: List<String>,
    suggestions: List<Suggestion>,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onTogglePack: (RulePack, Boolean) -> Unit,
    onAddUserRule: () -> Unit,
    onRemoveUserRule: (String) -> Unit,
    onAddDeny: () -> Unit,
    onRemoveDeny: (String) -> Unit,
    onAddSuggestion: (Suggestion) -> Unit,
    onIgnoreSuggestion: (Suggestion) -> Unit,
    onClearSuggestions: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("规则", style = MiuixTheme.textStyles.title1, color = onSurfaceColor(), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            "一条规则只做一件事：认出一个词，并且确认它在顶部、够小、点得动。",
            style = MiuixTheme.textStyles.footnote2,
            color = secondaryTextColor(),
        )

        Spacer(Modifier.height(16.dp))
        Segments(tab, listOf("内置", "我的"), onTabChange)

        Spacer(Modifier.height(14.dp))
        if (tab == 0) {
            BuiltinPacks(settings, importedCount, onTogglePack)
        } else {
            DenyCard(denyList, onAddDeny, onRemoveDeny)
            Spacer(Modifier.height(12.dp))
            DiscoverCard(suggestions, onAddSuggestion, onIgnoreSuggestion, onClearSuggestions)
            Spacer(Modifier.height(12.dp))
            UserRules(userRules, onAddUserRule, onRemoveUserRule)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Segments(selected: Int, labels: List<String>, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) MiuixTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (active) onSurfaceColor() else secondaryTextColor(),
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun BuiltinPacks(settings: Settings, importedCount: Int, onTogglePack: (RulePack, Boolean) -> Unit) {
    SkipperCard {
        CardTitle("通用跳过")
        Spacer(Modifier.height(4.dp))
        Label("所有 App 生效 · ${BuiltinRules.byPack(RulePack.GENERAL).size} 条")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("跳过 / 跳过广告 / 关闭广告 / Skip", style = MiuixTheme.textStyles.body2, color = onSurfaceColor(), modifier = Modifier.weight(1f))
            Switch(checked = settings.packGeneral, onCheckedChange = { onTogglePack(RulePack.GENERAL, it) })
        }
        Spacer(Modifier.height(8.dp))
        Label("只认整词，带「跳过登录」「跳过下载」这类尾巴的一律不点。")
    }

    Spacer(Modifier.height(12.dp))
    SkipperCard {
        CardTitle("视频开屏")
        Spacer(Modifier.height(4.dp))
        Label("${BuiltinRules.byPack(RulePack.VIDEO).size} 个 App · 按钮位置更靠上")
        Spacer(Modifier.height(10.dp))
        HairLine()
        val apps = BuiltinRules.byPack(RulePack.VIDEO)
        apps.forEach { rule ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rule.title, style = MiuixTheme.textStyles.body2, color = onSurfaceColor(), modifier = Modifier.weight(1f))
                Text(rule.packagePatterns.firstOrNull().orEmpty(), style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
            }
        }
        HairLine()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("这一组一起开关", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor(), modifier = Modifier.weight(1f))
            Switch(checked = settings.packVideo, onCheckedChange = { onTogglePack(RulePack.VIDEO, it) })
        }
    }

    Spacer(Modifier.height(12.dp))
    SkipperCard {
        CardTitle("专项规则")
        Spacer(Modifier.height(4.dp))
        Label("$importedCount 个 App · 从公开规则库提炼的开屏专项")
        Spacer(Modifier.height(10.dp))
        HairLine()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("按包名生效，比通用规则更宽容", style = MiuixTheme.textStyles.body2, color = onSurfaceColor(), modifier = Modifier.weight(1f))
            Switch(checked = settings.packImported, onCheckedChange = { onTogglePack(RulePack.IMPORTED, it) })
        }
        Spacer(Modifier.height(8.dp))
        Label("只补「跳过」这一件事：不含屏蔽、拦截、去广告逻辑。")
    }
}

@Composable
private fun UserRules(rules: List<SkipRule>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    SkipperCard(onClick = onAdd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("+ 添加一条规则", style = MiuixTheme.textStyles.body1, color = Gold, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("可空包名", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
        }
    }
    Spacer(Modifier.height(12.dp))
    if (rules.isEmpty()) {
        SkipperCard {
            EmptyHint("还没有自己的规则。\n遇到内置没覆盖的广告，把按钮上的字填进来就行。")
        }
    } else {
        SkipperCard {
            rules.forEachIndexed { index, rule ->
                if (index > 0) HairLine()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.title, style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (rule.packagePatterns.firstOrNull() == "*") "全部 App" else rule.packagePatterns.first(),
                            style = MiuixTheme.textStyles.footnote2,
                            color = secondaryTextColor(),
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRemove(rule.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("删除", style = MiuixTheme.textStyles.footnote2, color = com.wumisc.skipper.ui.theme.Danger)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Label("优先级最高：先看你的规则，再看内置。")
}

/**
 * 「永不跳过」：用户自己钉死的包名。它比任何规则都硬——
 * 误跳过伤到功能一次，就够毁掉信任，所以必须留一个亲手关死某个 App 的开关。
 */
@Composable
private fun DenyCard(list: List<String>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    SkipperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("永不跳过")
            Spacer(Modifier.weight(1f))
            Box(Modifier.clickable(onClick = onAdd).padding(6.dp)) {
                Text("+ 添加", style = MiuixTheme.textStyles.footnote2, color = Gold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Label("这些 App 一个按钮都不点，优先级高于通用/专项/我的规则。")
        if (list.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Label("还没有。被误点过就把它加进来。")
        } else {
            Spacer(Modifier.height(10.dp))
            HairLine()
            list.forEach { pkg ->
                Row(
                    Modifier.fillMaxWidth().clickable { onRemove(pkg) }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pkg, style = MiuixTheme.textStyles.body2, color = onSurfaceColor(), modifier = Modifier.weight(1f))
                    Text("移除", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
                }
            }
            HairLine()
        }
    }
}


/**
 * 「发现」：侦察兵看到的、像跳过但现有规则没覆盖的按钮。
 * 只提建议，点不点、加不加规则都由用户决定。
 */
@Composable
private fun DiscoverCard(
    list: List<Suggestion>,
    onAdd: (Suggestion) -> Unit,
    onIgnore: (Suggestion) -> Unit,
    onClear: () -> Unit,
) {
    SkipperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("发现")
            Spacer(Modifier.weight(1f))
            if (list.isNotEmpty()) {
                Box(Modifier.clickable(onClick = onClear).padding(6.dp)) {
                    Text("清空", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Label("使用时攒下来的「像跳过却没规则」的按钮，加一条就归你管。")
        if (list.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            EmptyHint("还没有。多开几个 App 的开屏就会有了。")
        } else {
            Spacer(Modifier.height(10.dp))
            HairLine()
            list.take(8).forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("「${s.label}」", style = MiuixTheme.textStyles.body2, color = onSurfaceColor())
                        Text(
                            "${s.pkg}${if (s.hits > 1) " · 见过 ${s.hits} 次" else ""}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = secondaryTextColor(),
                        )
                    }
                    Box(Modifier.clickable { onIgnore(s) }.padding(6.dp)) {
                        Text("忽略", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
                    }
                    Box(Modifier.clickable { onAdd(s) }.padding(6.dp)) {
                        Text("＋ 规则", style = MiuixTheme.textStyles.footnote2, color = Gold)
                    }
                }
            }
            HairLine()
        }
    }
}
