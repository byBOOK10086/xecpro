package com.xecpro.xechide.zygote.hook

import android.os.Binder
import com.v7878.unsafe.invoke.EmulatedStackFrame
import com.xecpro.xechide.zygote.runtime.HideRuntime
import com.xecpro.xechide.zygote.runtime.PackageSettings
import com.xecpro.xechide.zygote.util.FrameKit.argument
import com.xecpro.xechide.zygote.util.FrameKit.arguments
import com.xecpro.xechide.zygote.util.FrameKit.firstArgumentOfType
import com.xecpro.xechide.zygote.util.FrameKit.lastIndexWhere
import com.xecpro.xechide.zygote.util.Mirror
import com.xecpro.xechide.zygote.util.Names
import com.xecpro.xechide.zygote.util.XLog

/**
 * 应用列表隐藏的主战场。
 *
 * `AppsFilterImpl.shouldFilterApplication` 是 PackageManager 判断「A 应用能否看到 B 应用」
 * 的唯一裁决点：在这里返回 true，后续所有查询路径（getInstalledApplications、
 * queryIntentActivities、getPackageInfo……）都会自然地看不到目标应用，
 * 不需要逐个 API 去打补丁。因此它是本模块最关键的 hook。
 *
 * `ComputerEngine` 上的几个内部方法是补充：部分调用绕过了 AppsFilter，
 * 直接走内部查询，需要在返回值上直接置空。
 *
 * ## 调用方 uid 一律取 `Binder.getCallingUid()`
 *
 * 这些方法都在 system_server 的 binder 线程上同步执行，`Binder.getCallingUid()`
 * 就是发起查询的应用 uid。此前按参数位置取 uid 的写法是错的：
 * `getPackageInfoInternal` 的最后一个 int 是 `userId`（主用户恒为 0）而非
 * filterCallingUid，`shouldFilterApplication` 的参数顺序也在 13→16 间漂移过。
 * 按位置猜 uid 在主用户上恒得 0，[HideRuntime.shouldHideFromUid] 对 `uid <= 0`
 * 直接放行，于是这些 hook 实际从未生效——检测器直接 `getPackageInfo` 就能
 * 拿到被隐藏应用的完整信息。system 内部复入路径上 binder uid 是 1000，
 * 恰好按系统调用跳过，语义不变。
 */
class PmsHook(kit: HookKit, runtime: HideRuntime) : HideHook(kit, runtime) {

    override val tag: String = "PmsHook"

    override fun install() {
        installAppsFilter()
        installComputerEngineQueries()
        installUidProbes()
        installArchivedPackage()
    }

    /** binder 线程上的真实调用方 uid；system（1000）与非法值统一返回 -1 表示放行 */
    private fun callingUid(): Int {
        val uid = Binder.getCallingUid()
        return if (uid <= 0 || uid == Names.UID_SYSTEM) -1 else uid
    }

    /**
     * 从帧里取「被查询的目标包名」。
     *
     * 大多数入口的第一个 String 参数就是包名；`getPackageInfoVersioned` 一族
     * 传的是 `VersionedPackage` 对象，包名在其 `packageName` 字段里。
     */
    private fun targetPackageOf(frame: EmulatedStackFrame): String? {
        frame.firstArgumentOfType<String>()?.let { return it }

        for (arg in frame.arguments()) {
            if (arg != null && arg.javaClass.name.endsWith("VersionedPackage")) {
                return Mirror.field(arg, "packageName") as? String
            }
        }
        return null
    }

    /**
     * 签名（Android 14 起）：
     * `shouldFilterApplication(Computer, int callingUid, Object callingSetting, PackageSetting target, int userId)`
     *
     * 目标包名取最后一个 PackageSetting 类型参数；调用方 uid 不再按位置猜
     * （见类注释），直接问 binder。
     */
    private fun installAppsFilter() {
        kit.before(Names.APPS_FILTER_IMPL, "shouldFilterApplication") { _, frame, result ->
            val callingUid = callingUid()
            if (callingUid < 0) return@before

            val settingIndex = frame.lastIndexWhere { PackageSettings.isSettingType(it) }
            if (settingIndex < 0) return@before

            val target = PackageSettings.nameOf(frame.argument(settingIndex)) ?: return@before
            if (runtime.shouldHideFromUid(callingUid, target)) {
                XLog.d(tag) { "shouldFilterApplication 命中: uid=$callingUid -> $target" }
                result.value = true
            }
        }
    }

    /** 内部查询入口。这些方法没有经过 AppsFilter，需要直接把结果置空。 */
    private fun installComputerEngineQueries() {
        for (method in QUERY_METHODS) {
            kit.before(Names.COMPUTER_ENGINE, method) { name, frame, result ->
                val target = targetPackageOf(frame) ?: return@before
                val callingUid = callingUid()
                if (callingUid < 0) return@before

                if (runtime.shouldHideFromUid(callingUid, target)) {
                    XLog.d(tag) { "$name 命中: uid=$callingUid -> $target" }
                    result.value = null
                }
            }
        }
    }

    /**
     * uid 侧信道。
     *
     * 应用列表本身被隐藏后，检测器还有两条不走列表的反查路径：
     *  - `getPackageUid*`：装没装过，uid 会说话——一律回答 -1（未安装）；
     *  - `getPackagesForUid`：拿 uid 反查包名——把命中隐藏的包从结果里剔除。
     *
     * `getPackagesForUid` 用后置回调改写返回值；它同时会被本模块的
     * [HideRuntime.packagesForUid] 反射调用（uid→包名解析），那条路径上的
     * 重入由 [HideRuntime.packagesForUid] 的线程级保护挡掉。
     */
    private fun installUidProbes() {
        for (method in UID_METHODS) {
            for (className in listOf(Names.COMPUTER_ENGINE, Names.PACKAGE_MANAGER_SERVICE)) {
                kit.before(className, method) { name, frame, result ->
                    val target = targetPackageOf(frame) ?: return@before
                    val callingUid = callingUid()
                    if (callingUid < 0) return@before

                    if (runtime.shouldHideFromUid(callingUid, target)) {
                        XLog.d(tag) { "$name 命中: uid=$callingUid -> $target -> -1" }
                        result.value = -1
                    }
                }
            }
        }

        for (className in listOf(Names.COMPUTER_ENGINE, Names.PACKAGE_MANAGER_SERVICE)) {
            kit.after(className, "getPackagesForUid") { name, _, result ->
                val raw = result.value ?: return@after
                val callingUid = callingUid()
                if (callingUid < 0) return@after

                val names = when (raw) {
                    is Array<*> -> raw.filterIsInstance<String>()
                    is Collection<*> -> raw.filterIsInstance<String>()
                    else -> return@after
                }
                if (names.isEmpty()) return@after

                val kept = names.filterNot { runtime.shouldHideFromUid(callingUid, it) }
                if (kept.size == names.size) return@after

                XLog.d(tag) { "$name 命中: uid=$callingUid 隐藏 ${names.size - kept.size} 个包" }
                result.value = if (raw is Array<*>) kept.toTypedArray() else kept
            }
        }
    }

    /** 归档应用查询（14 QPR2 新增，部分 ROM 叫 getArchivedPackage） */
    private fun installArchivedPackage() {
        for (method in ARCHIVE_METHODS) {
            kit.before(Names.PACKAGE_MANAGER_SERVICE, method) { name, frame, result ->
                val target = targetPackageOf(frame) ?: return@before
                val callingUid = callingUid()
                if (callingUid < 0) return@before

                if (runtime.shouldHideFromUid(callingUid, target)) {
                    XLog.d(tag) { "$name 命中: uid=$callingUid -> $target" }
                    result.value = null
                }
            }
        }
    }

    private companion object {
        val QUERY_METHODS = listOf(
            "getPackageInfoInternal",
            "getApplicationInfoInternal",
            "getPackageInfoVersionedInternal",
        )

        val UID_METHODS = listOf(
            "getPackageUidInternal",
            "getPackageUidVersionedInternal",
            "getPackageUid",
        )

        val ARCHIVE_METHODS = listOf(
            "getArchivedPackageInternal",
            "getArchivedPackage",
        )
    }
}
