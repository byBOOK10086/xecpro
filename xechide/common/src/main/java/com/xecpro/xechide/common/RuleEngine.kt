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

    /** 该目标应用是否被纳入隐藏作用域 */
    fun isScoped(callerPackage: String): Boolean = config.scope.containsKey(callerPackage)

    /** 合并规则自身列表与引用模板后的包名集合 */
    fun resolveList(rule: HideConfig.AppRule): Set<String> {
        if (rule.templates.isEmpty()) return rule.packages
        val merged = HashSet<String>(rule.packages.size + 16)
        merged.addAll(rule.packages)
        for (name in rule.templates) {
            config.templates[name]?.let { merged.addAll(it.packages) }
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
            if (targetIsSystemApp && rule.excludeSystemApps) false else !list.contains(targetPackage)
        } else {
            list.contains(targetPackage)
        }
    }
}