package com.wumisc.skipper.data

import android.content.SharedPreferences
import com.wumisc.skipper.engine.BuiltinRules
import com.wumisc.skipper.engine.RuleSource
import com.wumisc.skipper.engine.SkipRule
import com.wumisc.skipper.engine.Safety
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.net.URLEncoder

class RuleStore(private val prefs: SharedPreferences) {

    private val _user = MutableStateFlow(readUser())
    val userRules: StateFlow<List<SkipRule>> = _user.asStateFlow()

    val builtin: List<SkipRule> = BuiltinRules.all

    /** 当前真正喂给引擎的规则集合：分组开关 + 用户规则（用户规则永远最高优先）。 */
    fun activeRules(settings: Settings): List<SkipRule> {
        val packs = buildList {
            if (settings.packGeneral) addAll(BuiltinRules.byPack(com.wumisc.skipper.engine.RulePack.GENERAL))
            if (settings.packVideo) addAll(BuiltinRules.byPack(com.wumisc.skipper.engine.RulePack.VIDEO))
        }
        return _user.value + packs
    }

    fun addUser(packageName: String, text: String): Boolean {
        val cleanPkg = packageName.trim()
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return false
        val rule = SkipRule(
            id = "user.${System.currentTimeMillis()}",
            pack = com.wumisc.skipper.engine.RulePack.USER,
            title = cleanText,
            packagePatterns = listOf(if (cleanPkg.isEmpty()) "*" else cleanPkg),
            textPatterns = listOf("^\\s*${Regex.escape(cleanText)}\\s*$"),
            excludeTextPatterns = Safety.dangerTextRegexes,
            maxTopRatio = 0.55f,
            weight = 1.1f,
            source = RuleSource.USER,
        )
        val next = _user.value + rule
        _user.value = next
        save(next)
        return true
    }

    fun removeUser(id: String) {
        val next = _user.value.filterNot { it.id == id }
        _user.value = next
        save(next)
    }

    private fun save(rules: List<SkipRule>) {
        val payload = rules.joinToString("\n") { rule ->
            val pkg = rule.packagePatterns.firstOrNull() ?: "*"
            listOf(rule.id, enc(pkg), enc(rule.title)).joinToString("|")
        }
        prefs.edit().putString(KEY_USER, payload).apply()
    }

    private fun readUser(): List<SkipRule> {
        val raw = prefs.getString(KEY_USER, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 3) return@mapNotNull null
            val pkg = runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrDefault("*")
            val text = runCatching { URLDecoder.decode(parts[2], "UTF-8") }.getOrDefault("")
            if (text.isBlank()) return@mapNotNull null
            SkipRule(
                id = parts[0],
                pack = com.wumisc.skipper.engine.RulePack.USER,
                title = text,
                packagePatterns = listOf(pkg),
                textPatterns = listOf("^\\s*${Regex.escape(text)}\\s*$"),
                excludeTextPatterns = Safety.dangerTextRegexes,
                maxTopRatio = 0.55f,
                weight = 1.1f,
                source = RuleSource.USER,
            )
        }.toList()
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object { const val KEY_USER = "user_rules_v1" }
}
