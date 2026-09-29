package com.xecpro.xechide.zygote.hook

import android.os.Binder
import android.os.Bundle
import com.xecpro.xechide.common.RuleEngine
import com.xecpro.xechide.zygote.runtime.HideRuntime
import com.xecpro.xechide.zygote.util.FrameKit.argument
import com.xecpro.xechide.zygote.util.Names
import com.xecpro.xechide.zygote.util.XLog

/**
 * 系统设置项隐藏。
 *
 * 隐藏应用只能挡住「包管理」这一层，很多检测器改走设置项来侧信道判断环境，因此还需要：
 *  1. 无障碍服务列表（判断有没有装自动化/无障碍工具）；
 *  2. 输入法列表（判断有没有装可疑输入法）；
 *  3. 开发者选项与 ADB 状态（判断是不是调试环境）。
 *
 * 三者都发生在 system_server 内、都以「调用方 uid」为判定依据，判定逻辑与包隐藏共用
 * [RuleEngine.settingHidden]，因此管理器里改一处规则即可同时生效。
 *
 * 所有挂钩都以「方法存在才装」为前提：不同 Android 版本的方法名有增删，
 * 找不到就跳过，不影响其余 hook。
 */
class SettingsHideHook(kit: HookKit, runtime: HideRuntime) : HideHook(kit, runtime) {

    override val tag: String = "SettingsHideHook"

    override fun install() {
        installAccessibility()
        installInputMethod()
        installDeveloperOptions()
    }

    /** 无障碍服务列表：直接返回空表，调用方看到的「已安装无障碍服务」为空 */
    private fun installAccessibility() {
        for (method in ACCESSIBILITY_METHODS) {
            kit.before(Names.ACCESSIBILITY_MANAGER_SERVICE, method) { hooked, _, result ->
                if (!hidden(RuleEngine.SettingKind.ACCESSIBILITY)) return@before
                result.value = emptyResult()
                XLog.i(TAG) { "隐藏无障碍列表: $hooked" }
            }
        }
    }

    /** 输入法列表：同上 */
    private fun installInputMethod() {
        for (method in INPUT_METHOD_METHODS) {
            kit.before(Names.INPUT_METHOD_MANAGER_SERVICE, method) { hooked, _, result ->
                if (!hidden(RuleEngine.SettingKind.INPUT_METHODS)) return@before
                result.value = emptyResult()
                XLog.i(TAG) { "隐藏输入法列表: $hooked" }
            }
        }
    }

    /**
     * 开发者选项状态。
     *
     * SettingsProvider 跑在 system 进程里，应用读 `Settings.Global` 最终会走它的
     * `call(String method, String arg, Bundle extras)`，返回值放在 `value` 这个 key 下。
     * 这里把它改写成字符串 "0"：`Settings.Global.getInt` 是「取字符串再解析」，
     * 因此返回 "0" 时读出来就是 0（关闭）。
     */
    private fun installDeveloperOptions() {
        kit.before(Names.SETTINGS_PROVIDER, CALL_METHOD, CALL_ARGUMENT_COUNT) { _, frame, result ->
            val method = frame.argument(0) as? String ?: return@before
            if (method !in CALL_GET_METHODS) return@before

            val key = frame.argument(1) as? String ?: return@before
            if (key !in DEVELOPER_KEYS) return@before

            if (!hidden(RuleEngine.SettingKind.DEVELOPER_OPTIONS)) return@before

            result.value = Bundle().apply { putString(Names.SETTINGS_VALUE_KEY, "0") }
            XLog.i(TAG) { "隐藏开发者选项: $key" }
        }
    }

    /**
     * 空列表返回值。
     *
     * 用 ArrayList 而不是 emptyList()：前者对 `List` / `Collection` / `ArrayList`
     * 三种声明都能通过帧的类型校验；每次新建是为了不让调用方改写共享实例。
     */
    private fun emptyResult(): Any = ArrayList<Any>(0)

    private fun hidden(kind: RuleEngine.SettingKind): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Names.UID_SYSTEM) return false
        return runtime.shouldHideSettingFromUid(uid, kind)
    }

    private companion object {
        const val TAG = "SettingsHideHook"

        /** SettingsProvider.call 的参数个数（method / arg / extras） */
        const val CALL_ARGUMENT_COUNT = 3
        const val CALL_METHOD = "call"

        val ACCESSIBILITY_METHODS = listOf(
            "getInstalledAccessibilityList",
            "getInstalledAccessibilityServiceList",
            "getEnabledAccessibilityServiceList",
        )

        val INPUT_METHOD_METHODS = listOf(
            "getInputMethodList",
            "getInputMethodListAsUser",
            "getEnabledInputMethodList",
            "getEnabledInputMethodListAsUser",
        )

        /** SettingsProvider.call 里读取设置用的方法名（AOSP SettingsCallConstants） */
        val CALL_GET_METHODS = setOf("GET_global", "GET_secure", "GET_system")

        /** 判断「调试环境」用到的设置键 */
        val DEVELOPER_KEYS = setOf(
            "development_settings_enabled",
            "adb_enabled",
            "adb_wifi_enabled",
        )
    }
}
