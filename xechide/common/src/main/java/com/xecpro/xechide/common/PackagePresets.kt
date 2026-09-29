package com.xecpro.xechide.common

/**
 * 内置预设包名表。
 *
 * 只放「包名 → 类别」这类事实数据，不含任何判定逻辑，方便后续热更新扩充。
 */
object PackagePresets {

    const val ROOT = "root"
    const val DETECTOR = "detector"
    const val XPOSED = "xposed"
    const val SHIZUKU = "shizuku"
    const val AUTOMATION = "automation"

    val names: List<String> = listOf(ROOT, DETECTOR, XPOSED, SHIZUKU, AUTOMATION)

    private val rootApps = setOf(
        "com.topjohnwu.magisk",
        "io.github.huskydg.magisk",
        "me.weishu.kernelsu",
        "com.xecpro.kernel",
        "eu.chainfire.supersu",
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "com.smedialink.oneclickroot",
        "com.zhiqupk.root.global",
        "com.alephzain.framaroot",
        "com.geohot.towelroot",
        "me.phh.superuser",
        "org.superuser",
        "com.apkmodifier.pro",
        "com.termux",
        "com.termux.api",
    )

    private val detectorApps = setOf(
        "com.scottyab.rootbeer",
        "com.joeykrim.rootcheck",
        "com.saurik.substrate",
        "com.zachspong.temprootremovejb",
        "com.amphoras.hidemyroot",
        "com.amphoras.hidemyrootadfree",
        "com.formyhm.hideroot",
        "com.formyhm.hiderootPremium",
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus",
        "com.rootcloak",
        "com.rootcloakpremium",
        "com.rootcloakshortcut",
        "com.rootcloakshortcutpro",
        "com.cyberalpha.rootcloak",
        "com.ramdroid.appquarantine",
        "com.ramdroid.appquarantinepro",
        "com.byapps.hidemyroot",
        "com.oasisfeng.island",
        "com.oasisfeng.island.fdroid",
    )

    private val xposedApps = setOf(
        "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager",
        "com.solohsu.android.edxp.manager",
        "org.lsposed.manager",
        "org.lsposed.lspatch",
        "io.github.lsposed.manager",
        "com.variable.apkhook",
    )

    private val shizukuApps = setOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.redirect",
        "rikka.shizuku",
        "moe.shizuku.fontprovider",
    )

    private val automationApps = setOf(
        "com.oasisfeng.greenify",
        "com.llamalab.automate",
        "net.dinglisch.android.taskerm",
        "com.teslacoilsw.launcher",
        "ch.deletescape.lawnchair.plah",
    )

    /** 预设名 → 包名集合；未知名字返回空集 */
    fun byName(name: String): Set<String> = when (name) {
        ROOT -> rootApps
        DETECTOR -> detectorApps
        XPOSED -> xposedApps
        SHIZUKU -> shizukuApps
        AUTOMATION -> automationApps
        else -> emptySet()
    }
}