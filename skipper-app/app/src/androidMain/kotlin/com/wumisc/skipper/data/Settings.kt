package com.wumisc.skipper.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一份设置 = 引擎的全部外部条件。引擎不读文件，只读这个对象。 */
data class Settings(
    val enabled: Boolean = true,
    val windowMillis: Long = 3_000,
    val cooldownMillis: Long = 3_000,
    val maxPerWindow: Int = 2,
    val vibrateOnSkip: Boolean = false,
    val darkTheme: Boolean = true,
    val packGeneral: Boolean = true,
    val packVideo: Boolean = true,
    val keepHistory: Boolean = true,
    val debugLog: Boolean = false,
)

class SettingsStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    /** 只读的联动信号：服务在跑吗、最后一次心跳是什么时候。不参与设置持久化。 */
    private val _aliveAt = MutableStateFlow(prefs.getLong(KEY_ALIVE, 0L))
    val aliveAt: StateFlow<Long> = _aliveAt.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun edit(block: (Settings) -> Settings) {
        val next = block(_state.value)
        _state.value = next
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putLong(KEY_WINDOW, next.windowMillis)
            .putLong(KEY_COOLDOWN, next.cooldownMillis)
            .putInt(KEY_MAX, next.maxPerWindow)
            .putBoolean(KEY_VIBRATE, next.vibrateOnSkip)
            .putBoolean(KEY_DARK, next.darkTheme)
            .putBoolean(KEY_PACK_GENERAL, next.packGeneral)
            .putBoolean(KEY_PACK_VIDEO, next.packVideo)
            .putBoolean(KEY_HISTORY, next.keepHistory)
            .putBoolean(KEY_DEBUG, next.debugLog)
            .apply()
    }

    fun markAlive(now: Long) {
        _aliveAt.value = now
        prefs.edit().putLong(KEY_ALIVE, now).apply()
    }

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    private fun read() = Settings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        windowMillis = prefs.getLong(KEY_WINDOW, 3_000),
        cooldownMillis = prefs.getLong(KEY_COOLDOWN, 3_000),
        maxPerWindow = prefs.getInt(KEY_MAX, 2),
        vibrateOnSkip = prefs.getBoolean(KEY_VIBRATE, false),
        darkTheme = prefs.getBoolean(KEY_DARK, true),
        packGeneral = prefs.getBoolean(KEY_PACK_GENERAL, true),
        packVideo = prefs.getBoolean(KEY_PACK_VIDEO, true),
        keepHistory = prefs.getBoolean(KEY_HISTORY, true),
        debugLog = prefs.getBoolean(KEY_DEBUG, false),
    )

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_WINDOW = "window_ms"
        const val KEY_COOLDOWN = "cooldown_ms"
        const val KEY_MAX = "max_per_window"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_DARK = "dark"
        const val KEY_PACK_GENERAL = "pack_general"
        const val KEY_PACK_VIDEO = "pack_video"
        const val KEY_HISTORY = "history"
        const val KEY_ALIVE = "alive_at"
        const val KEY_DEBUG = "debug_log"
    }
}
