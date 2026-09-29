package com.xecpro.xechide.zygote.hook

import com.xecpro.xechide.zygote.runtime.HideRuntime
import com.xecpro.xechide.zygote.runtime.PackageSettings
import com.xecpro.xechide.zygote.util.FrameKit.argument
import com.xecpro.xechide.zygote.util.FrameKit.firstArgumentOfType
import com.xecpro.xechide.zygote.util.FrameKit.lastArgumentOfType
import com.xecpro.xechide.zygote.util.FrameKit.lastIndexWhere
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
 */
class PmsHook(kit: HookKit, runtime: HideRuntime) : HideHook(kit, runtime) {

    override val tag: String = "PmsHook"

    override fun install() {
        installAppsFilter()
        installComputerEngineQueries()
        installArchivedPackage()
    }

    /**
     * 签名（Android 14 起）：
     * `shouldFilterApplication(Computer, int callingUid, Object callingSetting, PackageSetting target, int userId)`
     *
     * 参数位置在版本间漂移过，因此不写死下标：
     * 调用方 uid 取第 3 个参数，目标包名取最后一个 PackageSetting 类型参数。
     */
    private fun installAppsFilter() {
        kit.before(Names.APPS_FILTER_IMPL, "shouldFilterApplication") { _, frame, result ->
            val callingUid = frame.argument(2) as? Int ?: return@before
            if (callingUid == Names.UID_SYSTEM) return@before

            val settingIndex = frame.lastIndexWhere { PackageSettings.isSettingType(it) }
            if (settingIndex < 0) return@before

            val target = PackageSettings.nameOf(frame.argument(settingIndex)) ?: return@before
            if (runtime.shouldHideFromUid(callingUid, target)) {
                XLog.d(tag) { "shouldFilterApplication 命中: uid=$callingUid -> $target" }
                result.value = true
            }
        }
    }

    /**
     * 内部查询入口。这些方法没有经过 AppsFilter，需要直接把结果置空。
     *
     * - 目标包名：第一个 String 参数
     * - 调用方 uid：最后一个 int 参数（filterCallingUid）
     */
    private fun installComputerEngineQueries() {
        for (method in QUERY_METHODS) {
            kit.before(Names.COMPUTER_ENGINE, method) { name, frame, result ->
                val target = frame.firstArgumentOfType<String>() ?: return@before
                val callingUid = frame.lastArgumentOfType<Int>() ?: return@before
                if (callingUid == Names.UID_SYSTEM) return@before

                if (runtime.shouldHideFromUid(callingUid, target)) {
                    XLog.d(tag) { "$name 命中: uid=$callingUid -> $target" }
                    result.value = null
                }
            }
        }
    }

    /** 归档应用查询（14 QPR2 新增，部分 ROM 叫 getArchivedPackage） */
    private fun installArchivedPackage() {
        for (method in ARCHIVE_METHODS) {
            kit.before(Names.PACKAGE_MANAGER_SERVICE, method) { name, frame, result ->
                val target = frame.firstArgumentOfType<String>() ?: return@before
                val callingUid = frame.lastArgumentOfType<Int>() ?: return@before
                if (callingUid == Names.UID_SYSTEM) return@before

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
        )

        val ARCHIVE_METHODS = listOf(
            "getArchivedPackageInternal",
            "getArchivedPackage",
        )
    }
}