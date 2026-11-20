package com.wumisc.skipper.ui.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wumisc.skipper.data.SkipHit
import com.wumisc.skipper.ui.components.CardTitle
import com.wumisc.skipper.ui.components.EmptyHint
import com.wumisc.skipper.ui.components.HairLine
import com.wumisc.skipper.ui.components.Label
import com.wumisc.skipper.ui.components.SkipperCard
import com.wumisc.skipper.ui.components.onSurfaceColor
import com.wumisc.skipper.ui.components.secondaryTextColor
import com.wumisc.skipper.ui.theme.Gold
import com.wumisc.skipper.util.dayLabel
import com.wumisc.skipper.util.formatClock
import com.wumisc.skipper.util.friendlyName
import com.wumisc.skipper.util.isToday
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogScreen(
    hits: List<SkipHit>,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("记录", style = MiuixTheme.textStyles.title1, color = onSurfaceColor(), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("只记成功点掉的那一下", style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
            }
            if (hits.isNotEmpty()) {
                Box(Modifier.clickable(onClick = onClear).padding(8.dp)) {
                    Text("清空", style = MiuixTheme.textStyles.footnote1, color = Gold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SkipperCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("共 ${hits.size} 次", style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
                    Spacer(Modifier.height(2.dp))
                    Label("今天 ${hits.count { isToday(it.at) }} 次")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (hits.isEmpty()) {
            SkipperCard { EmptyHint("还没有记录。\n开屏广告出现时，点掉就会记在这里。") }
        } else {
            var lastDay = ""
            hits.forEach { hit ->
                val day = dayLabel(hit.at)
                if (day != lastDay) {
                    lastDay = day
                    Spacer(Modifier.height(4.dp))
                    Text(day, style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor(), modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                    Spacer(Modifier.height(6.dp))
                }
                HitRow(hit)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HitRow(hit: SkipHit) {
    SkipperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(friendlyName(hit.pkg), style = MiuixTheme.textStyles.body1, color = onSurfaceColor(), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text(hit.ruleId.substringBefore('.'), style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "点掉了「${hit.text}」 · 置信度 ${(hit.score * 100).toInt()}%",
                    style = MiuixTheme.textStyles.footnote2,
                    color = secondaryTextColor(),
                )
            }
            Text(formatClock(hit.at), style = MiuixTheme.textStyles.footnote1, color = onSurfaceColor())
        }
    }
}
