package com.wumisc.skipper.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wumisc.skipper.ui.components.EmptyHint
import com.wumisc.skipper.ui.components.onSurfaceColor
import com.wumisc.skipper.ui.components.secondaryTextColor
import com.wumisc.skipper.ui.theme.Danger
import com.wumisc.skipper.ui.theme.Gold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DebugScreen(
    lines: List<String>,
    debugOn: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(8.dp)) {
                Text("返回", style = MiuixTheme.textStyles.footnote1, color = secondaryTextColor())
            }
            Spacer(Modifier.width(4.dp))
            Text("调试日志", style = MiuixTheme.textStyles.subtitle, color = onSurfaceColor(), fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClear).padding(8.dp)) {
                Text("清空", style = MiuixTheme.textStyles.footnote1, color = Danger)
            }
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onCopy).padding(8.dp)) {
                Text("复制全部", style = MiuixTheme.textStyles.footnote1, color = Gold)
            }
        }

        if (!debugOn) {
            Spacer(Modifier.height(8.dp))
            Text(
                "调试日志开关是关的，这里不会出现新内容。去「设置 → 调试日志」打开。",
                style = MiuixTheme.textStyles.footnote2,
                color = secondaryTextColor(),
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(12.dp),
        ) {
            if (lines.isEmpty()) {
                EmptyHint("还没有日志。\n打开开关后，去触发一次开屏广告再回来。")
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    lines.forEach { line ->
                        Text(
                            line,
                            style = MiuixTheme.textStyles.footnote2,
                            color = onSurfaceColor(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
