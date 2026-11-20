package com.wumisc.skipper.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.wumisc.skipper.graph
import com.wumisc.skipper.ui.theme.SkipperTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = graph.settings
        setContent {
            val state by settings.state.collectAsState()
            SkipperTheme(dark = state.darkTheme) {
                AppRoot(versionName = versionName)
            }
        }
    }

    private val versionName: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "0.2.0"
    }
}
