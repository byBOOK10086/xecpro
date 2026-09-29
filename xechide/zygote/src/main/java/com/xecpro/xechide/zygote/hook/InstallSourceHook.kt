package com.xecpro.xechide.zygote.hook

import android.os.Binder
import com.xecpro.xechide.common.RuleEngine
import com.xecpro.xechide.zygote.runtime.HideRuntime
import com.xecpro.xechide.zygote.util.FrameKit.firstArgumentOfType
import com.xecpro.xechide.zygote.util.Names
import com.xecpro.xechide.zygote.util.XLog

/**
 * 安装来源伪造。
 *
 * 应用列表被隐藏后，安装来源仍会泄露线索（「你是从哪个应用装上的」），
 * 常见的检测器就靠这一项反查。这里把来源改写成两种无害形态：
 * 用户安装 → 应用商店；系统预装 → 无来源。
 */
class InstallSourceHook(kit: HookKit, runtime: HideRuntime) : HideHook(kit, runtime) {

    override val tag: String = "InstallSourceHook"

    /** 应用商店安装：来源、签名都指向 Play 商店 */
    private val userFake: Any? by lazy {
        runtime.bridge.buildInstallSourceInfo(
            installerPackageName = Names.PLAY_STORE,
            signingInfo = runtime.bridge.playStoreSigningInfo(0),
            packageSource = PACKAGE_SOURCE_STORE,
            userId = 0,
        )
    }

    /** 系统预装：全部置空 */
    private val systemFake: Any? by lazy {
        runtime.bridge.buildInstallSourceInfo(
            installerPackageName = null,
            signingInfo = null,
            packageSource = PACKAGE_SOURCE_UNSPECIFIED,
            userId = 0,
        )
    }

    override fun install() {
        for (className in TARGET_CLASSES) {
            kit.before(className, "getInstallerPackageName") { name, frame, result ->
                val target = frame.firstArgumentOfType<String>() ?: return@before
                when (runtime.installSourceActionForUid(Binder.getCallingUid(), target)) {
                    RuleEngine.SourceAction.SPOOF_USER -> {
                        XLog.d(tag) { "$name -> $target 伪造为应用商店来源" }
                        result.value = Names.PLAY_STORE
                    }

                    RuleEngine.SourceAction.SPOOF_SYSTEM -> {
                        XLog.d(tag) { "$name -> $target 伪造为系统预装" }
                        result.value = null
                    }

                    RuleEngine.SourceAction.DISABLED -> Unit
                }
            }

            kit.before(className, "getInstallSourceInfo") { name, frame, result ->
                val target = frame.firstArgumentOfType<String>() ?: return@before
                when (runtime.installSourceActionForUid(Binder.getCallingUid(), target)) {
                    RuleEngine.SourceAction.SPOOF_USER -> userFake?.let {
                        XLog.d(tag) { "$name -> $target 伪造为应用商店来源" }
                        result.value = it
                    }

                    RuleEngine.SourceAction.SPOOF_SYSTEM -> systemFake?.let {
                        XLog.d(tag) { "$name -> $target 伪造为系统预装" }
                        result.value = it
                    }

                    RuleEngine.SourceAction.DISABLED -> Unit
                }
            }
        }
    }

    private companion object {
        val TARGET_CLASSES = listOf(Names.COMPUTER_ENGINE, Names.PACKAGE_MANAGER_SERVICE)

        const val PACKAGE_SOURCE_UNSPECIFIED = 0
        const val PACKAGE_SOURCE_STORE = 1
    }
}