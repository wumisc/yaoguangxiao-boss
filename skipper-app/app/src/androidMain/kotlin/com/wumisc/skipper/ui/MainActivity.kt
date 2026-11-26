package com.wumisc.skipper.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.wumisc.skipper.graph
import com.wumisc.skipper.ui.theme.SkipperTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = graph.settings
        setContent {
            val state by settings.state.collectAsState()
            val dark = when (state.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            SkipperTheme(dark = dark) {
                AppRoot(versionName = versionName)
            }
        }
    }

    private val versionName: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "0.3.1-m11.1"
    }
}
