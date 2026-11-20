package com.wumisc.skipper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun surfaceColor(): Color = MiuixTheme.colorScheme.surface
@Composable
fun onSurfaceColor(): Color = MiuixTheme.colorScheme.onSurface
@Composable
fun secondaryTextColor(): Color = MiuixTheme.colorScheme.onSurfaceVariantSummary

/** 统一的一张「卡片」：圆角、底色、内边距。所有页面都复用它，视觉才整齐。 */
@Composable
fun SkipperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceColor())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
    ) { Column(content = content) }
}

@Composable
fun CardTitle(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.subtitle,
            color = onSurfaceColor(),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Label(text: String, color: Color = secondaryTextColor()) {
    Text(text = text, style = MiuixTheme.textStyles.footnote2, color = color)
}

@Composable
fun HairLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MiuixTheme.textStyles.footnote2, color = color)
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** 一行设置项：左标题/说明，右侧控件。 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.body1, color = onSurfaceColor())
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MiuixTheme.textStyles.footnote2, color = secondaryTextColor())
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** 数值步进器：窗口时长、冷却、上限都用它，避免滑块在小屏上难对准。 */
@Composable
fun Stepper(
    value: String,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepButton("−", canDecrease, onDecrease)
        Box(Modifier.width(62.dp), contentAlignment = Alignment.Center) {
            Text(value, style = MiuixTheme.textStyles.body1, color = onSurfaceColor(), fontWeight = FontWeight.Medium)
        }
        StepButton("+", canIncrease, onIncrease)
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) onSurfaceColor() else secondaryTextColor().copy(alpha = 0.35f)
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MiuixTheme.textStyles.body2, color = color)
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 26.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MiuixTheme.textStyles.footnote1, color = secondaryTextColor())
    }
}
