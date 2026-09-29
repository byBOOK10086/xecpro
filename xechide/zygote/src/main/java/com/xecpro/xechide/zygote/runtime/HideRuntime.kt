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
        }
    }

    fun reload() {
        val blob = runCatching { File(BlobCodec.CONFIG_PATH).readBytes() }.getOrNull()

        config = if (blob == null || blob.isEmpty()) {
            XLog.i(TAG) { "未找到配置文件，按未配置处理: ${BlobCodec.CONFIG_PATH}" }
            HideConfig()
        } else {
            ConfigJson.decodeBlob(blob)
        }

        rules = RuleEngine(config)
        invalidate()

        XLog.i(TAG) {
            "配置已加载: 作用域 ${config.scope.size} 项 / 模板 ${config.templates.size} 项 / 总开关 ${config.enabled}"
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

    fun packagesForUid(uid: Int): Array<String> =
        uidPackages.getOrPut(uid) { bridge.packagesForUid(uid) }

    private fun isSystemPackage(packageName: String, userId: Int): Boolean =
        systemPackageCache.getOrPut(packageName) { bridge.isSystemPackage(packageName, userId) }
}