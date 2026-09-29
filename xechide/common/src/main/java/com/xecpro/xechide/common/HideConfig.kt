package com.xecpro.xechide.common

import kotlinx.serialization.Serializable

/**
 * 隐藏配置的根模型。
 *
 * 落盘时经 [BlobCodec] 封装成 BCFG 容器（XOR "xdcv1"），与内置模块存储格式保持一致，
 * 因此混在 /data/adb/ksu/cfg/ 的编号文件里不会显得突兀。
 */
@Serializable
data class HideConfig(
    /** 配置版本，用于向后兼容迁移 */
    var version: Int = CONFIG_VERSION,

    /** 总开关，关闭后所有 hook 直接放行 */
    var enabled: Boolean = true,

    /** 是否拦截被隐藏应用的 Activity 启动（HMA 的 activity launch protection） */
    var blockActivityLaunch: Boolean = true,

    /** 是否对所有作用域应用做安装源伪造 */
    var spoofInstallSource: Boolean = true,

    /**
     * 白名单模式下的关键包保护。
     *
     * 白名单模式（「除列表外全部隐藏」）最容易出事的地方是连 android / systemui /
     * permissioncontroller / GMS 一起隐藏，目标应用随即崩溃或无限重试。
     * 打开后这些包在白名单模式下始终可见，除非被显式写进 oppositePackages。
     */
    var protectEssentialPackages: Boolean = true,

    /** 新装应用默认套用的规则；null 表示不自动套用 */
    var defaultRule: AppRule? = null,

    /** 全局应用模板：规则可通过名字引用 */
    val templates: MutableMap<String, HideTemplate> = mutableMapOf(),

    /** 作用域：key 为「被隐藏方」的应用包名，value 为该应用看到的隐藏规则 */
    val scope: MutableMap<String, AppRule> = mutableMapOf(),
) {
    /** 模板：一组包名，供规则引用；黑白名单语义由引用它的规则决定 */
    @Serializable
    data class HideTemplate(
        val packages: MutableSet<String> = mutableSetOf(),
    )

    /**
     * 单个目标应用的规则。
     *
     * 匹配优先级：oppositePackages（反向例外） > packages/templates 汇总 > 黑白名单语义。
     */
    @Serializable
    data class AppRule(
        /** true=白名单：只有列表内的应用对该目标可见；false=黑名单：列表内的应用被隐藏 */
        var whitelist: Boolean = false,

        /** 白名单模式下是否排除系统应用（避免把系统应用也一起隐藏导致异常） */
        var excludeSystemApps: Boolean = true,

        /** 是否伪造该目标看到的安装来源 */
        var hideInstallSource: Boolean = false,

        /** 是否连同系统应用的安装来源一起伪造 */
        var hideSystemInstallSource: Boolean = false,

        /** 相对全局开关反转 Activity 启动保护 */
        var invertActivityGuard: Boolean = false,

        /** 对目标应用隐藏「已安装的无障碍服务」列表（HMA 的 hide accessibility services） */
        var hideAccessibility: Boolean = false,

        /** 对目标应用隐藏开发者选项已开启的状态（development_settings_enabled / adb_enabled） */
        var hideDeveloperOptions: Boolean = false,

        /** 对目标应用隐藏「已启用的输入法」列表 */
        var hideInputMethods: Boolean = false,

        /** 引用的模板名 */
        var templates: MutableSet<String> = mutableSetOf(),

        /** 附加列表 */
        var packages: MutableSet<String> = mutableSetOf(),

        /** 反向例外：白名单模式下强制隐藏、黑名单模式下强制放行 */
        var oppositePackages: MutableSet<String> = mutableSetOf(),
    )

    companion object {
        const val CONFIG_VERSION = 1
    }
}