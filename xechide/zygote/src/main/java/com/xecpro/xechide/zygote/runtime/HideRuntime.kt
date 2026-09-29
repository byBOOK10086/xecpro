package com.xecpro.xechide.zygote.runtime

import com.xecpro.xechide.common.BlobCodec
import com.xecpro.xechide.common.ConfigJson
import com.xecpro.xechide.common.HideConfig
import com.xecpro.xechide.common.RuleEngine
import com.xecpro.xechide.zygote.util.Names
import com.xecpro.xechide.zygote.util.XLog
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * hook 侧的运行时状态。
 *
 * 持有配置、规则引擎与判定缓存。所有 hook 都通过这里做决策，因此缓存失效只需调 [invalidate]。
 */
class HideRuntime(val loader: ClassLoader?) {

    private companion object {
        const val TAG = "HideRuntime"

        /** AOSP 里 UserHandle.PER_USER_RANGE 就是 100000，跨版本稳定；直接算术取值可绕开隐藏 API 限制 */
        const val PER_USER_RANGE = 100000

        /** 配置轮询间隔：管理器（模块 WebUI）改完 9.cfg 后最多 2 秒生效，不必重启 */
        const val WATCH_INTERVAL_MILLIS = 2_000L

        fun userIdOf(uid: Int): Int = uid / PER_USER_RANGE
    }

    val bridge = PmBridge(loader)

    @Volatile
    private var config: HideConfig = HideConfig()

    @Volatile
    private var rules: RuleEngine = RuleEngine(config)

    /** uid → 该 uid 名下包名（含共享 uid 的多个包） */
    private val uidPackages = ConcurrentHashMap<Int, Array<String>>()

    /** uid → 已确认对该 uid 隐藏的包名，命中后免去重复求值 */
    private val hiddenTargets = ConcurrentHashMap<Int, MutableSet<String>>()

    private val systemPackageCache = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var ready = false

    val currentConfig: HideConfig get() = config

    val isReady: Boolean get() = ready

    /** 后台等 PMS 就绪后加载配置，避免阻塞 system_server 启动 */
    fun bootstrap() {
        thread(name = "xechide-init") {
            bridge.awaitService()
            reload()
            ready = true
            watchConfig()
        }
    }

    /**
     * 盯着配置文件的变化。
     *
     * system_server 里没有现成的文件监听器，而管理器改完 9.cfg 之后不该要求用户重启手机，
     * 所以这里轮询「文件大小 + 修改时间」，一变就重新加载并清缓存。
     *
     * canonical（/data/adb/ksu/cfg/9.cfg）与镜像（/data/system/sysfwk/9.cfg）都要盯：
     * 镜像由 root 侧发布，大小/时间戳与 canonical 同步变化，任一变化都触发重载。
     */
    private fun watchConfig() {
        val files = BlobCodec.READ_PATHS.map { File(it) }
        val size = LongArray(files.size) { files[it].length() }
        val modified = LongArray(files.size) { files[it].lastModified() }

        while (true) {
            try {
                Thread.sleep(WATCH_INTERVAL_MILLIS)

                var changed = false
                files.forEachIndexed { index, file ->
                    val currentSize = file.length()
                    val currentModified = file.lastModified()
                    if (currentSize != size[index] || currentModified != modified[index]) {
                        size[index] = currentSize
                        modified[index] = currentModified
                        changed = true
                    }
                }

                if (changed) reload()
            } catch (t: Throwable) {
                XLog.e(TAG, t) { "配置监听异常" }
            }
        }
    }

    fun reload() {
        var used: String? = null
        var blob: ByteArray? = null

        // 镜像优先：它才是 uid 1000 真正读得到的那一份；canonical 只是它的源头。
        for (path in BlobCodec.READ_PATHS) {
            val bytes = runCatching { File(path).readBytes() }.getOrNull()
            if (!bytes.isNullOrEmpty()) {
                blob = bytes
                used = path
                break
            }
        }

        config = if (blob == null) {
            XLog.i(TAG) { "未找到可读配置，按未配置处理: ${BlobCodec.READ_PATHS.joinToString()}" }
            HideConfig()
        } else {
            ConfigJson.decodeBlob(blob)
        }

        rules = RuleEngine(config)
        invalidate()

        XLog.i(TAG) {
            "配置已加载[$used]: 作用域 ${config.scope.size} 项 / 模板 ${config.templates.size} 项 / 总开关 ${config.enabled}"
        }
    }

    fun invalidate() {
        uidPackages.clear()
        hiddenTargets.clear()
    }

    /** [targetPackage] 是否要对 [callingUid] 隐藏 */
    fun shouldHideFromUid(callingUid: Int, targetPackage: String): Boolean {
        if (!config.enabled || callingUid == Names.UID_SYSTEM) return false
        if (callingUid <= 0 || targetPackage.isEmpty()) return false

        hiddenTargets[callingUid]?.let { if (it.contains(targetPackage)) return true }

        val isSystem = isSystemPackage(targetPackage, userIdOf(callingUid))

        for (caller in packagesForUid(callingUid)) {
            if (rules.shouldHide(caller, targetPackage, isSystem)) {
                hiddenTargets.computeIfAbsent(callingUid) { ConcurrentHashMap.newKeySet() }
                    .add(targetPackage)
                return true
            }
        }

        return false
    }

    /** [targetPackage] 的安装来源对 [callingUid] 该如何伪造 */
    fun installSourceActionForUid(callingUid: Int, targetPackage: String): RuleEngine.SourceAction {
        if (!config.enabled || callingUid == Names.UID_SYSTEM) return RuleEngine.SourceAction.DISABLED

        val isSystem = isSystemPackage(targetPackage, userIdOf(callingUid))

        for (caller in packagesForUid(callingUid)) {
            val action = rules.installSourceAction(caller, targetPackage, isSystem)
            if (action != RuleEngine.SourceAction.DISABLED) return action
        }

        return RuleEngine.SourceAction.DISABLED
    }

    /** [targetPackage] 的 Activity 是否应拒绝被 [callingUid] 拉起 */
    fun shouldBlockActivityLaunch(callingUid: Int, targetPackage: String): Boolean {
        if (!config.enabled || callingUid == Names.UID_SYSTEM) return false

        for (caller in packagesForUid(callingUid)) {
            if (!rules.activityGuardEnabled(caller)) continue
            if (rules.shouldHide(caller, targetPackage, isSystemPackage(targetPackage, userIdOf(callingUid)))) {
                return true
            }
        }

        return false
    }

    /** [callingUid] 视角下是否要隐藏 [kind] 类别的系统设置项 */
    fun shouldHideSettingFromUid(callingUid: Int, kind: RuleEngine.SettingKind): Boolean {
        if (!config.enabled || callingUid == Names.UID_SYSTEM) return false
        if (callingUid <= 0) return false

        for (caller in packagesForUid(callingUid)) {
            if (rules.settingHidden(caller, kind)) return true
        }

        return false
    }

    fun packagesForUid(uid: Int): Array<String> =
        uidPackages.getOrPut(uid) { bridge.packagesForUid(uid) }

    private fun isSystemPackage(packageName: String, userId: Int): Boolean =
        systemPackageCache.getOrPut(packageName) { bridge.isSystemPackage(packageName, userId) }
}