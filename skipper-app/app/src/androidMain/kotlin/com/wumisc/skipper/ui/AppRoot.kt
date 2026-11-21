package com.wumisc.skipper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wumisc.skipper.data.Settings
import com.wumisc.skipper.data.SkipHit
import com.wumisc.skipper.engine.RulePack
import com.wumisc.skipper.graph
import com.wumisc.skipper.ui.components.onSurfaceColor
import com.wumisc.skipper.ui.components.secondaryTextColor
import com.wumisc.skipper.ui.home.HomeScreen
import com.wumisc.skipper.ui.log.LogScreen
import com.wumisc.skipper.ui.rules.RulesScreen
import com.wumisc.skipper.ui.settings.DebugScreen
import com.wumisc.skipper.ui.settings.SettingsScreen
import com.wumisc.skipper.ui.theme.Gold
import com.wumisc.skipper.util.isThisWeek
import com.wumisc.skipper.util.isToday
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class Tab(val label: String) { HOME("首页"), RULES("规则"), LOG("记录"), SETTINGS("设置") }

@Composable
fun AppRoot(versionName: String) {
    val context = LocalContext.current
    val graph = remember { context.graph }
    val settings by graph.settings.state.collectAsState()
    val connected by graph.settings.connected.collectAsState()
    val aliveAt by graph.settings.aliveAt.collectAsState()
    val hits by graph.hits.hits.collectAsState()
    val userRules by graph.rules.userRules.collectAsState()
    val suggestions by graph.suggest.items.collectAsState()

    val debugLines by graph.debug.lines.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var rulesTab by remember { mutableIntStateOf(0) }
    var showDebug by remember { androidx.compose.runtime.mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }
    val alive = System.currentTimeMillis() - aliveAt < 12_000

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MiuixTheme.colorScheme.background,
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                    ),
                ),
            )
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (showDebug) {
                    DebugScreen(
                        lines = debugLines,
                        debugOn = settings.debugLog,
                        onCopy = { copyDebug(context, graph.debug.exportText()) },
                        onClear = { graph.debug.clear() },
                        onClose = { showDebug = false },
                    )
                    return@Box
                }
                when (Tab.entries[tab]) {
                    Tab.HOME -> HomeScreen(
                        settings = settings,
                        connected = connected,
                        alive = alive,
                        todayCount = hits.count { isToday(it.at) },
                        weekCount = hits.count { isThisWeek(it.at) },
                        totalCount = hits.size,
                        latest = hits.firstOrNull(),
                        onEnabledChange = { on -> graph.settings.edit { it.copy(enabled = on) } },
                        onOpenAccessibility = { openAccessibility(context) },
                    )

                    Tab.RULES -> RulesScreen(
                        settings = settings,
                        userRules = userRules,
                        importedCount = graph.rules.importedRules.size,
                        denyList = settings.denyPackages.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        suggestions = suggestions,
                        tab = rulesTab,
                        onTabChange = { rulesTab = it },
                        onTogglePack = { pack, on ->
                            graph.settings.edit {
                                when (pack) {
                                    RulePack.GENERAL -> it.copy(packGeneral = on)
                                    RulePack.VIDEO -> it.copy(packVideo = on)
                                    RulePack.IMPORTED -> it.copy(packImported = on)
                                    RulePack.USER -> it
                                }
                            }
                        },
                        onAddUserRule = {
                            promptAddRule(context) { pkg, text ->
                                graph.rules.addUser(pkg, text)
                            }
                        },
                        onRemoveUserRule = { graph.rules.removeUser(it) },
                        onAddDeny = {
                            promptText(context, "永不跳过", "包名，例如 com.coolapk.market") { pkg ->
                                val cur = graph.settings.state.value.denyPackages.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                                if (pkg.isNotBlank() && pkg !in cur) {
                                    graph.settings.edit { it.copy(denyPackages = (cur + pkg).joinToString(",")) }
                                }
                            }
                        },
                        onRemoveDeny = { pkg ->
                            val cur = graph.settings.state.value.denyPackages.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            graph.settings.edit { it.copy(denyPackages = cur.filterNot { it == pkg }.joinToString(",")) }
                        },
                        onAddSuggestion = { s ->
                            graph.rules.addUser(s.pkg, s.label)
                            graph.suggest.ignore(s.pkg, s.label)
                        },
                        onIgnoreSuggestion = { s -> graph.suggest.ignore(s.pkg, s.label) },
                        onClearSuggestions = { graph.suggest.clear() },
                    )

                    Tab.LOG -> LogScreen(
                        hits = hits,
                        onClear = { graph.hits.clear() },
                        onExport = { copyHits(context, hits) },
                    )

                    Tab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        versionName = versionName,
                        onEdit = { block: (Settings) -> Settings -> graph.settings.edit(block) },
                        onClearHistory = { graph.hits.clear() },
                        onOpenAccessibility = { openAccessibility(context) },
                        onOpenDebug = { showDebug = true },
                    )
                }
            }
            if (!showDebug) BottomBar(current = tab, onSelect = { tab = it })
        }
    }
}

@Composable
private fun BottomBar(current: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEachIndexed { index, item ->
            val active = index == current
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    item.label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (active) Gold else secondaryTextColor(),
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .fillMaxWidth(if (active) 0.22f else 0f)
                        .background(Gold),
                )
            }
        }
    }
}

private fun copyHits(context: Context, hits: List<com.wumisc.skipper.data.SkipHit>) {
    val head = "time\tpkg\trule\tlabel\tscore"
    val body = hits.joinToString("\n") { h ->
        val t = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(h.at))
        "$t\t${h.pkg}\t${h.ruleId}\t${h.text}\t${h.score}"
    }
    copyDebug(context, "$head\n$body")
}

private fun copyDebug(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("skipper-debug", text))
        Toast.makeText(context, "日志已复制到剪贴板（${text.lineSequence().count()} 行）", Toast.LENGTH_SHORT).show()
    }
}

private fun openAccessibility(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun promptAddRule(context: Context, onAdd: (String, String) -> Unit) {
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 24, 48, 0)
    }
    val pkgInput = EditText(context).apply {
        hint = "包名，可空（空 = 所有 App）"
        setSingleLine()
    }
    val textInput = EditText(context).apply {
        hint = "按钮上的字，例如：跳过"
        setSingleLine()
    }
    container.addView(pkgInput)
    container.addView(textInput)
    android.app.AlertDialog.Builder(context)
        .setTitle("添加一条规则")
        .setView(container)
        .setPositiveButton("添加") { _, _ -> onAdd(pkgInput.text.toString(), textInput.text.toString()) }
        .setNegativeButton("取消", null)
        .show()
}

private fun promptText(context: Context, title: String, hint: String, onOk: (String) -> Unit) {
    val input = EditText(context).apply {
        this.hint = hint
        setSingleLine()
    }
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 24, 48, 0)
        addView(input)
    }
    android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setView(container)
        .setPositiveButton("确定") { _, _ -> onOk(input.text.toString().trim()) }
        .setNegativeButton("取消", null)
        .show()
}
