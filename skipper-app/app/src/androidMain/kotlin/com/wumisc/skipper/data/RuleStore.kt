package com.wumisc.skipper.data

import android.content.SharedPreferences
import com.wumisc.skipper.engine.BuiltinRules
import com.wumisc.skipper.engine.RulePack
import com.wumisc.skipper.engine.SkipRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RuleStore(private val prefs: SharedPreferences) {

    private val _user = MutableStateFlow(RuleCodec.decode(prefs.getString(KEY_USER, null)))
    val userRules: StateFlow<List<SkipRule>> = _user.asStateFlow()

    val builtin: List<SkipRule> = BuiltinRules.all

    /** 当前真正喂给引擎的规则集合：用户规则永远最高优先，其次才是分组开关。 */
    fun activeRules(settings: Settings): List<SkipRule> {
        val packs = buildList {
            if (settings.packGeneral) addAll(BuiltinRules.byPack(RulePack.GENERAL))
            if (settings.packVideo) addAll(BuiltinRules.byPack(RulePack.VIDEO))
        }
        return _user.value + packs
    }

    fun addUser(packageName: String, text: String): Boolean {
        val rule = RuleCodec.userRule(packageName, text, "user.${System.currentTimeMillis()}") ?: return false
        val next = _user.value + rule
        _user.value = next
        prefs.edit().putString(KEY_USER, RuleCodec.encode(next)).apply()
        return true
    }

    fun removeUser(id: String) {
        val next = _user.value.filterNot { it.id == id }
        _user.value = next
        prefs.edit().putString(KEY_USER, RuleCodec.encode(next)).apply()
    }

    private companion object { const val KEY_USER = "user_rules_v2" }
}
