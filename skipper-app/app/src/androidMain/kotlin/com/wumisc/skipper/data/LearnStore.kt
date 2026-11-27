package com.wumisc.skipper.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.wumisc.skipper.learn.LearnCandidate
import com.wumisc.skipper.learn.LearnConfig
import com.wumisc.skipper.learn.LearnRequest
import com.wumisc.skipper.learn.LearnTsv
import com.wumisc.skipper.learn.LearnUiState
import com.wumisc.skipper.learn.LearnedDomain
import com.wumisc.skipper.learn.LearnQueueItem
import com.wumisc.skipper.learn.ModuleStatus
import com.wumisc.skipper.learn.SdkEntry
import com.wumisc.skipper.learn.SdkCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * App 侧的唯一学习存储。所有跨进程边界都在 files/learn/ 下：
 * learned.tsv 给模块读，learn_config.json 双向读，module_status.json 只读。
 *
 * 写盘全部走 IO 线程；TSV/JSON 先写临时文件再 rename，失败保留旧文件。
 */
class LearnStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File(context.filesDir, "learn").apply { mkdirs() }
    private val configFile = File(dir, "learn_config.json")
    private val candidatesFile = File(dir, "candidates.tsv")
    private val learnedFile = File(dir, "learned.tsv")
    private val queueFile = File(dir, "queue.tsv")
    private val statusFile = File(dir, "status.json")
    private val moduleFile = File(dir, "module_status.json")
    private val requestFile = File(dir, "learn_request.json")
    private val prefs = context.getSharedPreferences("learn", Context.MODE_PRIVATE)

    private val catalog: SdkCatalog = runCatching {
        SdkCatalog.parse(context.assets.open("learn/sdk_domains.tsv").bufferedReader().use { it.readText() })
    }.getOrElse { SdkCatalog(emptyList()) }

    private var config = LearnConfig()
    private var candidates: List<LearnCandidate> = emptyList()
    private var learned: List<LearnedDomain> = emptyList()
    private var queue: List<LearnQueueItem> = emptyList()
    private var module = ModuleStatus()
    private var lastLearnAt = 0L
    private var lastScanAt = 0L
    private var lastStatusWrite = 0L
    private var currentPkg = ""
    private var lastError = ""

    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    init {
        ensureDefaultConfig()
        reloadFromDisk()
        publish()
        writeStatus(force = true)
    }

    fun catalog(): SdkCatalog = catalog

    /** 只读当前配置，不触发磁盘。 */
    fun config(): LearnConfig = config

    @Synchronized
    fun setConfig(next: LearnConfig) {
        config = next.copy(updatedAt = System.currentTimeMillis() / 1000)
        publish()
        scope.launch { writeConfig() }
    }

    @Synchronized
    fun setCurrentPackage(pkg: String) {
        if (pkg == currentPkg) return
        currentPkg = pkg
        publish()
    }

    /** 记一批同一窗口/同一清单命中的 SDK。同一批内同一域名只算一次。 */
    @Synchronized
    fun record(pkg: String, versionCode: Long, hits: List<SdkEntry>, source: String, force: Boolean = false) {
        if ((!config.probe && !force) || pkg.isBlank() || hits.isEmpty()) return
        val nowSec = System.currentTimeMillis() / 1000
        val unique = LinkedHashMap<String, SdkEntry>()
        hits.forEach { e ->
            val d = e.domain.lowercase()
            if (!SdkCatalog.isValidDomain(d) || isProtected(d)) return@forEach
            unique[d] = e
        }
        if (unique.isEmpty()) return

        val next = ArrayList(candidates)
        for ((domain, entry) in unique) {
            val idx = next.indexOfFirst {
                it.domain == domain && it.pkg == pkg && it.versionCode == versionCode
            }
            if (idx >= 0) {
                val old = next[idx]
                if (old.state == LearnCandidate.STATE_IGNORED) continue
                val c = old.copy(
                    sdk = entry.sdk,
                    source = source,
                    count = old.count + 1,
                    lastSeenAt = nowSec,
                    state = if (old.count + 1 >= CONFIRM_COUNT) LearnCandidate.STATE_CONFIRMED else old.state,
                )
                next[idx] = c
                if (c.count >= CONFIRM_COUNT) addLearned(c)
            } else {
                val c = LearnCandidate(
                    domain = domain,
                    sdk = entry.sdk,
                    pkg = pkg,
                    versionCode = versionCode,
                    source = source,
                    count = 1,
                    lastSeenAt = nowSec,
                )
                next += c
            }
        }
        candidates = trimCandidates(next)
        lastLearnAt = nowSec
        publish()
        scope.launch {
            writeCandidates()
            writeLearned()
            writeStatus()
        }
    }

    /** 无 L0/L1 命中时把包放进 L2 队列；同包同版本只排一次。 */
    @Synchronized
    fun enqueue(pkg: String, versionCode: Long, reason: String = "no-l0-l1") {
        if (pkg.isBlank()) return
        if (queue.any { it.pkg == pkg && it.versionCode == versionCode }) return
        queue = (queue + LearnQueueItem(pkg, versionCode, reason, System.currentTimeMillis() / 1000))
            .takeLast(QUEUE_LIMIT)
        publish()
        scope.launch { writeQueue() }
    }

    @Synchronized
    fun confirm(domain: String, pkg: String, versionCode: Long) {
        val idx = candidates.indexOfFirst { it.domain == domain && it.pkg == pkg && it.versionCode == versionCode }
        if (idx < 0) return
        val c = candidates[idx].copy(state = LearnCandidate.STATE_CONFIRMED, lastSeenAt = System.currentTimeMillis() / 1000)
        candidates = candidates.toMutableList().also { it[idx] = c }
        addLearned(c, force = true)
        lastLearnAt = c.lastSeenAt
        publish()
        scope.launch {
            writeCandidates()
            writeLearned()
            writeStatus()
        }
    }

    @Synchronized
    fun ignore(domain: String, pkg: String, versionCode: Long) {
        val idx = candidates.indexOfFirst { it.domain == domain && it.pkg == pkg && it.versionCode == versionCode }
        if (idx < 0) return
        candidates = candidates.toMutableList().also {
            it[idx] = it[idx].copy(state = LearnCandidate.STATE_IGNORED)
        }
        learned = learned.filterNot { it.domain == domain && it.pkg == pkg }
        publish()
        scope.launch {
            writeCandidates()
            writeLearned()
            writeStatus()
        }
    }

    @Synchronized
    fun removeLearned(domain: String, pkg: String) {
        learned = learned.filterNot { it.domain == domain && it.pkg == pkg }
        publish()
        scope.launch { writeLearned(); writeStatus() }
    }

    @Synchronized
    fun reloadConfig() {
        config = readConfig()
        publish()
    }

    @Synchronized
    fun reloadModuleStatus() {
        module = readModuleStatus()
        publish()
    }

    @Synchronized
    fun refresh() {
        reloadFromDisk()
        publish()
    }

    fun readPendingRequest(): LearnRequest? = runCatching {
        if (!requestFile.exists()) return null
        val o = JSONObject(requestFile.readText())
        LearnRequest(
            action = o.optString("action", "learn_now"),
            pkg = o.optString("pkg", ""),
            nonce = o.optString("nonce", ""),
            at = o.optLong("at", 0L),
        )
    }.getOrNull()

    fun consumeRequest(nonce: String): Boolean {
        if (nonce.isBlank()) return false
        if (prefs.getString(KEY_LAST_NONCE, "") == nonce) return false
        prefs.edit().putString(KEY_LAST_NONCE, nonce).apply()
        runCatching { requestFile.delete() }
        return true
    }

    private fun addLearned(c: LearnCandidate, force: Boolean = false) {
        if (!force && !config.autoImport) return
        if (isProtected(c.domain)) return
        val item = LearnedDomain(
            domain = c.domain,
            sdk = c.sdk,
            pkg = c.pkg,
            source = c.source,
            confidence = when (c.source) {
                "manifest" -> 0.95
                "dex" -> 0.88
                else -> 0.92
            },
            updatedAt = System.currentTimeMillis() / 1000,
        )
        val rest = learned.filterNot { it.domain == item.domain && it.pkg == item.pkg }
        learned = (rest + item).sortedByDescending { it.updatedAt }.take(LEARNED_LIMIT)
    }

    private fun trimCandidates(list: List<LearnCandidate>): List<LearnCandidate> {
        val live = list.filter { it.state != LearnCandidate.STATE_IGNORED }
            .sortedByDescending { it.lastSeenAt }
            .take(CANDIDATE_LIMIT)
        val ignored = list.filter { it.state == LearnCandidate.STATE_IGNORED }.takeLast(50)
        return (live + ignored).sortedByDescending { it.lastSeenAt }
    }

    private fun reloadFromDisk() {
        config = readConfig()
        candidates = runCatching { LearnTsv.decodeCandidates(candidatesFile.readText()) }.getOrElse { emptyList() }
        learned = runCatching { LearnTsv.decodeLearned(learnedFile.readText()) }.getOrElse { emptyList() }
        queue = runCatching { LearnTsv.decodeQueue(queueFile.readText()) }.getOrElse { emptyList() }
        module = readModuleStatus()
    }

    private fun ensureDefaultConfig() {
        if (configFile.exists()) return
        writeConfigBlocking(LearnConfig(updatedAt = System.currentTimeMillis() / 1000))
    }

    private fun readConfig(): LearnConfig = runCatching {
        if (!configFile.exists()) return LearnConfig()
        val o = JSONObject(configFile.readText())
        LearnConfig(
            probe = o.optBoolean("probe", true),
            deep = o.optBoolean("deep", false),
            autoImport = o.optBoolean("autoImport", true),
            autoApply = o.optBoolean("autoApply", false),
            deepMaxPerDay = o.optInt("deepMaxPerDay", 1),
            thermalLimit = o.optInt("thermalLimit", 2),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }.getOrElse { LearnConfig() }

    private fun writeConfig() = writeConfigBlocking(config)

    private fun writeConfigBlocking(cfg: LearnConfig) {
        val o = JSONObject()
            .put("schema", 1)
            .put("probe", cfg.probe)
            .put("deep", cfg.deep)
            .put("autoImport", cfg.autoImport)
            .put("autoApply", cfg.autoApply)
            .put("deepMaxPerDay", cfg.deepMaxPerDay)
            .put("thermalLimit", cfg.thermalLimit)
            .put("updatedAt", cfg.updatedAt)
        atomicWrite(configFile, o.toString())
    }

    private fun writeCandidates() = atomicWrite(candidatesFile, LearnTsv.encodeCandidates(candidates))
    private fun writeLearned() = atomicWrite(learnedFile, LearnTsv.encodeLearned(learned))
    private fun writeQueue() = atomicWrite(queueFile, LearnTsv.encodeQueue(queue))

    private fun readModuleStatus(): ModuleStatus = runCatching {
        if (!moduleFile.exists()) return ModuleStatus()
        val o = JSONObject(moduleFile.readText())
        ModuleStatus(
            moduleVersion = o.optString("moduleVersion", ""),
            lastImportAt = o.optLong("lastImportAt", 0L),
            newCount = o.optInt("newCount", 0),
            totalLearned = o.optInt("totalLearned", 0),
            mounted = o.optBoolean("mounted", false),
            autoApply = o.optBoolean("autoApply", false),
            rules = o.optInt("rules", 0),
            error = o.optString("error", ""),
        )
    }.getOrElse { ModuleStatus() }

    private fun writeStatus(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastStatusWrite < STATUS_INTERVAL_MS) return
        lastStatusWrite = now
        val snapshot = _state.value
        val o = JSONObject()
            .put("schema", 1)
            .put("appVersion", appVersion())
            .put("running", true)
            .put("phase", "idle")
            .put("probe", snapshot.config.probe)
            .put("deep", snapshot.config.deep)
            .put("queue", snapshot.queue.size)
            .put("candidates", snapshot.candidateCount)
            .put("learned", snapshot.learned.size)
            .put("lastLearnAt", snapshot.lastLearnAt)
            .put("lastScanAt", snapshot.lastScanAt)
            .put("thermal", snapshot.thermal)
            .put("battery", snapshot.battery)
            .put("error", snapshot.error)
        atomicWrite(statusFile, o.toString())
    }

    private fun publish() {
        _state.value = LearnUiState(
            config = config,
            candidates = candidates,
            learned = learned,
            queue = queue,
            module = module,
            lastLearnAt = lastLearnAt,
            lastScanAt = lastScanAt,
            thermal = thermalStatus(),
            battery = batteryPercent(),
            error = lastError,
            currentPkg = currentPkg,
        )
        // 状态写盘节流，且绝不占用调用线程（无障碍主线程 / Compose 主线程）。
        scope.launch { writeStatus() }
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrElse { "" }

    private fun batteryPercent(): Int = runCatching {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level < 0) 0 else (level * 100 / scale.coerceAtLeast(1))
    }.getOrElse { 0 }

    private fun thermalStatus(): Int = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
        } else 0
    }.getOrElse { 0 }

    @Synchronized
    private fun atomicWrite(file: File, text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }.onFailure { lastError = it.message.orEmpty() }
    }

    private fun isProtected(domain: String): Boolean {
        val d = domain.lowercase()
        return PROTECTED.any { d == it || d.endsWith(".$it") }
    }

    companion object {
        private const val CONFIRM_COUNT = 2
        private const val CANDIDATE_LIMIT = 2000
        private const val LEARNED_LIMIT = 2000
        private const val QUEUE_LIMIT = 50
        private const val STATUS_INTERVAL_MS = 5_000L
        private const val KEY_LAST_NONCE = "last_nonce"

        private val PROTECTED = listOf(
            "qq.com", "baidu.com", "alibaba.com", "alipay.com", "taobao.com", "tmob.com",
            "jd.com", "weibo.com", "weixin.qq.com", "tencent.com", "bytedance.com",
            "kuaishou.com", "google.com", "apple.com", "microsoft.com",
        )
    }
}
