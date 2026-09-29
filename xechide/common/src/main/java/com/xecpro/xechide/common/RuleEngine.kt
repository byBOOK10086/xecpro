package com.xecpro.xechide.common

/**
 * 规则求值引擎。
 *
 * 纯函数式：只依赖构造时传入的 [HideConfig]，不持有任何 Android 状态，
 * 因此管理端与 hook 端可以共用同一份判定逻辑，避免两边行为漂移。
 */
class RuleEngine(private val config: HideConfig) {

    /** 安装来源伪造的处置方式 */
    enum class SourceAction {
        /** 不处理 */
        DISABLED,

        /** 伪装成应用商店安装（用户来源） */
        SPOOF_USER,

        /** 伪装成系统预装 */
        SPOOF_SYSTEM,
    }

    /** 可隐藏的系统设置项类别 */
    enum class SettingKind {
        /** 已安装/已启用的无障碍服务 */
        ACCESSIBILITY,

        /** 开发者选项状态（development_settings_enabled / adb_enabled） */
        DEVELOPER_OPTIONS,

        /** 已启用的输入法 */
        INPUT_METHODS,
    }

    companion object {
        /**
         * 白名单模式下的关键包。
         *
         * 与 [HideConfig.protectEssentialPackages] 配合：这些包一旦被隐藏，目标应用
         * 通常会因为拿不到自己的依赖组件而崩溃或反复重启，所以默认豁免。
         * 需要连它们一起隐藏时，写进规则的 oppositePackages 即可。
         */
        val ESSENTIAL_PACKAGES: Set<String> = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.downloads",
            "com.android.shell",
            "com.google.android.gms",
            "com.google.android.gsf",
        )
    }

    /** 该目标应用是否被纳入隐藏作用域 */
    fun isScoped(callerPackage: String): Boolean = config.scope.containsKey(callerPackage)

    /**
     * 合并规则自身列表与引用模板后的包名集合。
     *
     * 模板名先查配置里自定义的 [HideConfig.templates]，再叠加内置预设
     * （[PackagePresets]）：因此 `"templates": ["root"]` 这种写法和自定义模板一样可用，
     * 内置预设的包名表不必复制进配置文件。
     */
    fun resolveList(rule: HideConfig.AppRule): Set<String> {
        if (rule.templates.isEmpty()) return rule.packages
        val merged = HashSet<String>(rule.packages.size + 64)
        merged.addAll(rule.packages)
        for (name in rule.templates) {
            config.templates[name]?.let { merged.addAll(it.packages) }
            merged.addAll(PackagePresets.byName(name))
        }
        return merged
    }

    /**
     * 判断 [targetPackage] 是否要对 [callerPackage] 隐藏。
     *
     * @param targetIsSystemApp 目标是否为系统应用（白名单模式据此决定是否豁免）
     */
    fun shouldHide(
        callerPackage: String,
        targetPackage: String,
        targetIsSystemApp: Boolean,
    ): Boolean {
        if (!config.enabled) return false
        if (callerPackage == targetPackage) return false

        val rule = config.scope[callerPackage] ?: config.defaultRule ?: return false
        return evaluate(rule, targetPackage, targetIsSystemApp)
    }

    /** 该目标看到的安装来源应如何伪造 */
    fun installSourceAction(
        callerPackage: String,
        targetPackage: String,
        targetIsSystemApp: Boolean,
    ): SourceAction {
        if (!config.enabled || !config.spoofInstallSource) return SourceAction.DISABLED

        val rule = config.scope[callerPackage] ?: return SourceAction.DISABLED
        if (!rule.hideInstallSource) return SourceAction.DISABLED
        if (targetIsSystemApp && !rule.hideSystemInstallSource) return SourceAction.DISABLED

        return if (targetIsSystemApp) SourceAction.SPOOF_SYSTEM else SourceAction.SPOOF_USER
    }

    /** 该目标是否启用 Activity 启动保护 */
    fun activityGuardEnabled(callerPackage: String): Boolean {
        if (!config.enabled || !config.blockActivityLaunch) return false

        val rule = config.scope[callerPackage] ?: return false
        return !rule.invertActivityGuard
    }

    /**
     * [callerPackage] 视角下是否要隐藏 [kind] 类别的系统设置项。
     *
     * 作用域规则优先，其次回落到 defaultRule；两者都没有该应用时不隐藏。
     */
    fun settingHidden(callerPackage: String, kind: SettingKind): Boolean {
        if (!config.enabled) return false

        val rule = config.scope[callerPackage] ?: config.defaultRule ?: return false
        return when (kind) {
            SettingKind.ACCESSIBILITY -> rule.hideAccessibility
            SettingKind.DEVELOPER_OPTIONS -> rule.hideDeveloperOptions
            SettingKind.INPUT_METHODS -> rule.hideInputMethods
        }
    }

    private fun evaluate(
        rule: HideConfig.AppRule,
        targetPackage: String,
        targetIsSystemApp: Boolean,
    ): Boolean {
        if (rule.oppositePackages.contains(targetPackage)) {
            // 白名单模式下强制隐藏，黑名单模式下强制放行
            return !rule.whitelist
        }

        val list = resolveList(rule)

        return if (rule.whitelist) {
            // 白名单模式下先看关键包豁免，再看系统应用豁免
            when {
                config.protectEssentialPackages && ESSENTIAL_PACKAGES.contains(targetPackage) -> false
                targetIsSystemApp && rule.excludeSystemApps -> false
                else -> !list.contains(targetPackage)
            }
        } else {
            list.contains(targetPackage)
        }
    }
}