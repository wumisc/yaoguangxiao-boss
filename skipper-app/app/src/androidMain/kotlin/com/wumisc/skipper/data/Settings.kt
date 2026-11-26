package com.wumisc.skipper.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一份设置 = 引擎的全部外部条件。引擎不读文件，只读这个对象。 */
data class Settings(
    val enabled: Boolean = true,
    val windowMillis: Long = 5_000,
    val cooldownMillis: Long = 3_000,
    val maxPerWindow: Int = 2,
    val vibrateOnSkip: Boolean = false,
    /** 0 跟随系统 / 1 浅色 / 2 深色。 */
    val themeMode: Int = 0,
    val packGeneral: Boolean = true,
    val packVideo: Boolean = true,
    val packImported: Boolean = true,
    val keepHistory: Boolean = true,
    val debugLog: Boolean = false,
    /** 永不跳过的包名，逗号分隔。 */
    val denyPackages: String = "",
)

class SettingsStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    /** 只读的联动信号：服务在跑吗、最后一次心跳是什么时候。不参与设置持久化。 */
    private val _aliveAt = MutableStateFlow(prefs.getLong(KEY_ALIVE, 0L))
    val aliveAt: StateFlow<Long> = _aliveAt.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var lastAlivePersist = 0L

    fun edit(block: (Settings) -> Settings) {
        val next = block(_state.value)
        _state.value = next
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putLong(KEY_WINDOW, next.windowMillis)
            .putLong(KEY_COOLDOWN, next.cooldownMillis)
            .putInt(KEY_MAX, next.maxPerWindow)
            .putBoolean(KEY_VIBRATE, next.vibrateOnSkip)
            .putInt(KEY_THEME, next.themeMode)
            .putBoolean(KEY_PACK_GENERAL, next.packGeneral)
            .putBoolean(KEY_PACK_VIDEO, next.packVideo)
            .putBoolean(KEY_PACK_IMPORTED, next.packImported)
            .putBoolean(KEY_HISTORY, next.keepHistory)
            .putBoolean(KEY_DEBUG, next.debugLog)
            .putString(KEY_DENY, next.denyPackages)
            .apply()
    }

    fun markAlive(now: Long) {
        _aliveAt.value = now
        // 心跳每 5 秒一次；只把最近一次落盘，避免每 5 秒都写一次 SharedPreferences。
        if (now - lastAlivePersist >= ALIVE_PERSIST_MS) {
            lastAlivePersist = now
            prefs.edit().putLong(KEY_ALIVE, now).apply()
        }
    }

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    private fun read() = Settings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        windowMillis = prefs.getLong(KEY_WINDOW, 5_000),
        cooldownMillis = prefs.getLong(KEY_COOLDOWN, 3_000),
        maxPerWindow = prefs.getInt(KEY_MAX, 2),
        vibrateOnSkip = prefs.getBoolean(KEY_VIBRATE, false),
        themeMode = prefs.getInt(KEY_THEME, 0),
        packGeneral = prefs.getBoolean(KEY_PACK_GENERAL, true),
        packVideo = prefs.getBoolean(KEY_PACK_VIDEO, true),
        packImported = prefs.getBoolean(KEY_PACK_IMPORTED, true),
        keepHistory = prefs.getBoolean(KEY_HISTORY, true),
        debugLog = prefs.getBoolean(KEY_DEBUG, false),
        denyPackages = prefs.getString(KEY_DENY, "").orEmpty(),
    )

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_WINDOW = "window_ms"
        const val KEY_COOLDOWN = "cooldown_ms"
        const val KEY_MAX = "max_per_window"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_THEME = "theme_mode"
        @Suppress("unused")
        const val KEY_DARK_LEGACY = "dark"
        const val KEY_PACK_GENERAL = "pack_general"
        const val KEY_PACK_VIDEO = "pack_video"
        const val KEY_PACK_IMPORTED = "pack_imported"
        const val KEY_HISTORY = "history"
        const val KEY_ALIVE = "alive_at"
        const val ALIVE_PERSIST_MS = 30_000L
        const val KEY_DEBUG = "debug_log"
        const val KEY_DENY = "deny_packages"
    }
}
