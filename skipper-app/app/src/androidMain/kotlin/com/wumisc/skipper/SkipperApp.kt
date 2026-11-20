package com.wumisc.skipper

import android.app.Application
import android.content.Context
import com.wumisc.skipper.data.DebugLog
import com.wumisc.skipper.data.HitStore
import com.wumisc.skipper.data.RuleStore
import com.wumisc.skipper.data.SettingsStore
import java.io.File

/**
 * 整个进程只有这一份图：Activity 和 Service 拿到的是同一组对象。
 * 这就是「前端和后端是一体」的物理基础——不是靠广播传话，而是共用同一份状态。
 */
class AppGraph(context: Context) {
    private val prefs = context.getSharedPreferences("skipper", Context.MODE_PRIVATE)

    val settings = SettingsStore(prefs)
    val rules = RuleStore(prefs)
    val hits = HitStore(File(context.filesDir, "hits.log"))
    val debug = DebugLog()
}

class SkipperApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

val Context.graph: AppGraph
    get() = (applicationContext as SkipperApp).graph
