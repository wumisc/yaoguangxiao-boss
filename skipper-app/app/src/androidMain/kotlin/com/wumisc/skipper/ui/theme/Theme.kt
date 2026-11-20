package com.wumisc.skipper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 唯一的强调色：一点金色，和图标里的和尚金像对得上。 */
val Gold = Color(0xFFE3B25C)
val GoldSoft = Color(0x33E3B25C)
val Danger = Color(0xFFE0655B)
val Safe = Color(0xFF5FB98A)

@Composable
fun SkipperTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MiuixTheme(
        colors = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
