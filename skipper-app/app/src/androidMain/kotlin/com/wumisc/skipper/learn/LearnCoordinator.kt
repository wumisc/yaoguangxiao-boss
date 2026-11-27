package com.wumisc.skipper.learn

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.FileObserver
import com.wumisc.skipper.data.LearnStore
import com.wumisc.skipper.engine.UiNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * L0/L1 的唯一入口。
 *
 * 约束：
 * - 只在 TYPE_WINDOW_STATE_CHANGED 跑 L0；内容事件不参与学习。
 * - L1 每个包每个版本只查一次清单，IO 线程跑，不阻塞跳过引擎。
 * - 不读 dex、不枚举全部包、不申请 QUERY_ALL_PACKAGES、不申请 INTERNET。
 */
class LearnCoordinator(
    private val context: Context,
    private val store: LearnStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File(context.filesDir, "learn").apply { mkdirs() }
    private val checked = HashSet<String>()
    private val windows = LinkedHashMap<String, CachedWindow>()
    private var lastExternalPkg = ""
    private var observer: FileObserver? = null

    data class CachedWindow(val nodes: List<UiNode>, val at: Long)

    val state = store.state
    val catalog: SdkCatalog = store.catalog()

    fun start() {
        if (observer != null) return
        observe()
        handlePendingRequest()
        scope.launch { store.refresh() }
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
    }

    /**
     * AccessibilityService 每次拿到快照后调用。
     * 即使跳过总开关关闭，学习探针仍可工作；两者互不绑定。
     */
    fun onWindow(pkg: String, nodes: List<UiNode>, isStateEvent: Boolean) {
        if (pkg.isBlank() || pkg == context.packageName) return
        lastExternalPkg = pkg
        store.setCurrentPackage(pkg)
        windows[pkg] = CachedWindow(nodes, System.currentTimeMillis())
        trimWindows()

        if (!isStateEvent) return
        if (!store.config().probe) return

        val hits = catalog.matchAll(names(nodes))
        if (hits.isNotEmpty()) {
            store.record(pkg, versionCode(pkg), hits, "ui")
            return
        }

        // L0 没认出来，才查一次清单；同一包同一版本只查一次。
        val key = pkg + "#" + versionCode(pkg)
        if (checked.add(key)) {
            scope.launch {
                val manifestHits = manifestHits(pkg)
                if (manifestHits.isNotEmpty()) {
                    store.record(pkg, versionCode(pkg), manifestHits, "manifest")
                } else {
                    store.enqueue(pkg, versionCode(pkg))
                }
            }
        }
    }

    /**
     * WebUI 的「学习当前 App」最终落到这里。
     * pkg 为空时用最后一个外部前台包；不会把姚广孝自己当成目标。
     */
    fun learnNow(pkg: String = ""): Boolean {
        val target = pkg.ifBlank { lastExternalPkg }
        if (target.isBlank() || target == context.packageName) return false
        scope.launch {
            val cached = windows[target]?.nodes.orEmpty()
            val version = versionCode(target)
            val uiHits = catalog.matchAll(names(cached))
            if (uiHits.isNotEmpty()) store.record(target, version, uiHits, "ui", force = true)
            val manifest = manifestHits(target)
            if (manifest.isNotEmpty()) store.record(target, version, manifest, "manifest", force = true)
            if (uiHits.isEmpty() && manifest.isEmpty()) store.enqueue(target, version)
            store.setCurrentPackage(target)
        }
        return true
    }

    fun refresh() {
        scope.launch {
            store.refresh()
            store.reloadModuleStatus()
        }
    }

    fun reloadConfig() {
        store.reloadConfig()
    }

    fun reloadModuleStatus() {
        store.reloadModuleStatus()
    }

    fun setProbe(on: Boolean) {
        store.setConfig(store.config().copy(probe = on))
    }

    fun setAutoImport(on: Boolean) {
        store.setConfig(store.config().copy(autoImport = on))
    }

    fun confirm(candidate: LearnCandidate) {
        store.confirm(candidate.domain, candidate.pkg, candidate.versionCode)
    }

    fun ignore(candidate: LearnCandidate) {
        store.ignore(candidate.domain, candidate.pkg, candidate.versionCode)
    }

    fun removeLearned(item: LearnedDomain) {
        store.removeLearned(item.domain, item.pkg)
    }

    private fun observe() {
        @Suppress("DEPRECATION")
        observer = object : FileObserver(dir, CREATE or CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                when (path) {
                    "learn_request.json" -> handlePendingRequest()
                    "learn_config.json" -> store.reloadConfig()
                    "module_status.json" -> store.reloadModuleStatus()
                }
            }
        }.also { it.startWatching() }
    }

    private fun handlePendingRequest() {
        val req = store.readPendingRequest() ?: return
        if (!store.consumeRequest(req.nonce)) return
        if (req.action == "learn_now") learnNow(req.pkg)
    }

    private fun names(nodes: List<UiNode>): List<String> {
        val out = ArrayList<String>(nodes.size * 2)
        nodes.forEach { n ->
            n.viewId?.let { out += it }
            if (n.className.isNotBlank()) out += n.className
        }
        return out
    }

    private fun manifestHits(pkg: String): List<SdkEntry> = runCatching {
        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        val info = pm.getPackageInfo(pkg, flags)
        val classes = ArrayList<String>()
        info.activities?.forEach { it.name?.let(classes::add) }
        info.services?.forEach { it.name?.let(classes::add) }
        info.providers?.forEach { it.name?.let(classes::add) }
        info.applicationInfo?.className?.let(classes::add)
        catalog.matchAll(classes)
    }.getOrElse { emptyList() }

    private fun versionCode(pkg: String): Long = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrElse { 0L }

    private fun trimWindows() {
        if (windows.size <= WINDOW_CACHE) return
        val oldest = windows.minByOrNull { it.value.at }?.key ?: return
        windows.remove(oldest)
    }

    private companion object {
        const val WINDOW_CACHE = 8
    }
}
