package com.wumisc.skipper.learn

/** 联动配置，字段与模块 `learn_config.json` 一一对应。 */
data class LearnConfig(
    val probe: Boolean = true,
    val deep: Boolean = false,
    val autoImport: Boolean = true,
    val autoApply: Boolean = false,
    val deepMaxPerDay: Int = 1,
    val thermalLimit: Int = 2,
    val updatedAt: Long = 0,
)

/** SDK 前缀表的一行：类名前缀 -> SDK 名 -> 域名。 */
data class SdkEntry(val prefix: String, val sdk: String, val domain: String)

/** 候选：还没达到确认阈值，或等待用户手动确认。 */
data class LearnCandidate(
    val domain: String,
    val sdk: String,
    val pkg: String,
    val versionCode: Long,
    val source: String,
    val count: Int,
    val lastSeenAt: Long,
    val state: String = STATE_CANDIDATE,
) {
    companion object {
        const val STATE_CANDIDATE = "candidate"
        const val STATE_CONFIRMED = "confirmed"
        const val STATE_IGNORED = "ignored"
    }
}

/** 已确认、交给模块的域名。 */
data class LearnedDomain(
    val domain: String,
    val sdk: String,
    val pkg: String,
    val source: String,
    val confidence: Double,
    val updatedAt: Long,
)

/** 待 L2 补扫的包。 */
data class LearnQueueItem(
    val pkg: String,
    val versionCode: Long,
    val reason: String,
    val enqueuedAt: Long,
    val attempts: Int = 0,
)

/** WebUI 写下的“学习当前 App”请求。 */
data class LearnRequest(
    val action: String = "learn_now",
    val pkg: String = "",
    val nonce: String = "",
    val at: Long = 0,
)

/** 模块回写的导入结果。 */
data class ModuleStatus(
    val moduleVersion: String = "",
    val lastImportAt: Long = 0,
    val newCount: Int = 0,
    val totalLearned: Int = 0,
    val mounted: Boolean = false,
    val autoApply: Boolean = false,
    val rules: Int = 0,
    val error: String = "",
)

/** UI 一次刷新拿到的全部学习状态。 */
data class LearnUiState(
    val config: LearnConfig = LearnConfig(),
    val candidates: List<LearnCandidate> = emptyList(),
    val learned: List<LearnedDomain> = emptyList(),
    val queue: List<LearnQueueItem> = emptyList(),
    val module: ModuleStatus = ModuleStatus(),
    val lastLearnAt: Long = 0,
    val lastScanAt: Long = 0,
    val thermal: Int = 0,
    val battery: Int = 0,
    val error: String = "",
    val currentPkg: String = "",
) {
    val candidateCount: Int get() = candidates.count { it.state == LearnCandidate.STATE_CANDIDATE }
    val confirmedCount: Int get() = learned.size
}

/**
 * 只读 SDK 表 + 前缀边界匹配。没有正则、没有子串模糊匹配，避免把普通类名认成广告 SDK。
 */
class SdkCatalog(private val entries: List<SdkEntry>) {

    fun match(name: String): List<SdkEntry> {
        val n = name.trim()
        if (n.isEmpty()) return emptyList()
        return entries.filter { e ->
            n == e.prefix || n.startsWith(e.prefix + ".") || n.startsWith(e.prefix + ":")
        }
    }

    fun matchAll(names: Iterable<String>): List<SdkEntry> {
        val seen = LinkedHashMap<String, SdkEntry>()
        for (name in names) {
            for (e in match(name)) seen[e.domain + "\t" + e.sdk] = e
        }
        return seen.values.toList()
    }

    companion object {
        fun parse(text: String): SdkCatalog {
            val out = ArrayList<SdkEntry>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val f = line.split('\t')
                if (f.size < 3) return@forEach
                val prefix = f[0].trim()
                val sdk = f[1].trim()
                val domain = f[2].trim().lowercase()
                if (prefix.isEmpty() || sdk.isEmpty()) return@forEach
                if (!isValidDomain(domain)) return@forEach
                out += SdkEntry(prefix, sdk, domain)
            }
            return SdkCatalog(out)
        }

        fun isValidDomain(domain: String): Boolean {
            if (domain.length < 4 || domain.length > 253) return false
            if (!domain.contains('.')) return false
            if (domain.any { !(it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') }) return false
            if (domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) return false
            return true
        }
    }
}

/**
 * 四个 TSV 的编解码。字段顺序严格按 m12 契约，模块只读第一列。
 * 这里不依赖 Android，JVM 靶场可以直接测。
 */
object LearnTsv {

    private fun cell(v: String): String = v.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun encodeCandidates(list: List<LearnCandidate>): String = buildString {
        appendLine("# domain\tsdk\tpkg\tversionCode\tsource\tcount\tlastSeenAt\tstate")
        list.forEach {
            append(cell(it.domain)); append('\t')
            append(cell(it.sdk)); append('\t')
            append(cell(it.pkg)); append('\t')
            append(it.versionCode); append('\t')
            append(cell(it.source)); append('\t')
            append(it.count); append('\t')
            append(it.lastSeenAt); append('\t')
            append(cell(it.state)); append('\n')
        }
    }

    fun decodeCandidates(text: String): List<LearnCandidate> = text.lineSequence()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 8) return@mapNotNull null
            val domain = f[0].trim().lowercase()
            if (!SdkCatalog.isValidDomain(domain)) return@mapNotNull null
            LearnCandidate(
                domain = domain,
                sdk = f[1].trim(),
                pkg = f[2].trim(),
                versionCode = f[3].trim().toLongOrNull() ?: 0L,
                source = f[4].trim(),
                count = f[5].trim().toIntOrNull() ?: 0,
                lastSeenAt = f[6].trim().toLongOrNull() ?: 0L,
                state = f[7].trim().ifBlank { LearnCandidate.STATE_CANDIDATE },
            )
        }.toList()

    fun encodeLearned(list: List<LearnedDomain>): String = buildString {
        appendLine("# domain\tsdk\tpkg\tsource\tconfidence\tupdatedAt")
        list.forEach {
            append(cell(it.domain)); append('\t')
            append(cell(it.sdk)); append('\t')
            append(cell(it.pkg)); append('\t')
            append(cell(it.source)); append('\t')
            append(it.confidence); append('\t')
            append(it.updatedAt); append('\n')
        }
    }

    fun decodeLearned(text: String): List<LearnedDomain> = text.lineSequence()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 6) return@mapNotNull null
            val domain = f[0].trim().lowercase()
            if (!SdkCatalog.isValidDomain(domain)) return@mapNotNull null
            LearnedDomain(
                domain = domain,
                sdk = f[1].trim(),
                pkg = f[2].trim(),
                source = f[3].trim(),
                confidence = f[4].trim().toDoubleOrNull() ?: 0.0,
                updatedAt = f[5].trim().toLongOrNull() ?: 0L,
            )
        }.toList()

    fun encodeQueue(list: List<LearnQueueItem>): String = buildString {
        appendLine("# pkg\tversionCode\treason\tenqueuedAt\tattempts")
        list.forEach {
            append(cell(it.pkg)); append('\t')
            append(it.versionCode); append('\t')
            append(cell(it.reason)); append('\t')
            append(it.enqueuedAt); append('\t')
            append(it.attempts); append('\n')
        }
    }

    fun decodeQueue(text: String): List<LearnQueueItem> = text.lineSequence()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 5) return@mapNotNull null
            val pkg = f[0].trim()
            if (pkg.isEmpty()) return@mapNotNull null
            LearnQueueItem(
                pkg = pkg,
                versionCode = f[1].trim().toLongOrNull() ?: 0L,
                reason = f[2].trim(),
                enqueuedAt = f[3].trim().toLongOrNull() ?: 0L,
                attempts = f[4].trim().toIntOrNull() ?: 0,
            )
        }.toList()

    /** 同一域名 + 包 + 版本只保留一行；候选表按最近命中排序。 */
    fun mergeCandidate(old: List<LearnCandidate>, incoming: LearnCandidate): List<LearnCandidate> {
        val key = incoming.domain to (incoming.pkg + "#" + incoming.versionCode)
        val rest = old.filterNot { (it.domain to (it.pkg + "#" + it.versionCode)) == key }
        return (rest + incoming).sortedByDescending { it.lastSeenAt }
    }
}
