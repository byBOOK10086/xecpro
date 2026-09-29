package com.xecpro.xechide.zygote.runtime

import android.os.IBinder
import com.v7878.unsafe.Reflection
import com.xecpro.xechide.zygote.util.Names
import com.xecpro.xechide.zygote.util.XLog

/**
 * 对 `IPackageManager` 的反射访问层。
 *
 * 刻意不引入编译期桩类：所有调用都走 [Reflection]（vmtools 的隐藏 API 绕过实现），
 * 这样签名随版本漂移时只需在运行时试错，不需要为每个 Android 版本维护一份 stub。
 */
class PmBridge(private val loader: ClassLoader?) {

    private companion object {
        const val TAG = "PmBridge"

        const val FLAG_SYSTEM = 0x0000_0001
        const val FLAG_UPDATED_SYSTEM_APP = 0x0000_0080

        /** PackageManager.GET_SIGNING_CERTIFICATES，直接写常量避免依赖编译期 SDK 版本 */
        const val GET_SIGNING_CERTIFICATES = 0x0800_0000
    }

    private val iface: Class<*>? by lazy { load("android.content.pm.IPackageManager") }

    @Volatile
    private var service: Any? = null

    /** 等待 PMS 发布；返回 null 表示 system_server 还没把服务注册进来 */
    fun awaitService(timeoutMillis: Long = 30_000L): Any? {
        service?.let { return it }

        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            obtain()?.let {
                service = it
                XLog.i(TAG) { "已获取 IPackageManager" }
                return it
            }
            Thread.sleep(250)
        }

        XLog.w(TAG) { "等待 IPackageManager 超时" }
        return null
    }

    private fun obtain(): Any? = runCatching {
        val serviceManager = load("android.os.ServiceManager") ?: return null
        val binder = Reflection.getDeclaredMethod(serviceManager, "getService", String::class.java)
            .invoke(null, Names.PACKAGE_SERVICE) as? IBinder ?: return null

        val stub = load("android.content.pm.IPackageManager\$Stub") ?: return null
        Reflection.getDeclaredMethod(stub, "asInterface", IBinder::class.java).invoke(null, binder)
    }.getOrNull()

    fun packagesForUid(uid: Int): Array<String> {
        val pm = service ?: return emptyArray()
        val target = iface ?: return emptyArray()
        return runCatching {
            Reflection.getDeclaredMethod(target, "getPackagesForUid", Int::class.java)
                .invoke(pm, uid) as? Array<String>
        }.getOrNull() ?: emptyArray()
    }

    /**
     * 目标是否为系统应用。
     *
     * 探测失败时按「是系统应用」处理：白名单模式下会把系统应用保留可见，
     * 这是更安全的一侧，不至于因为一次反射失败把桌面/系统 UI 隐藏掉。
     */
    fun isSystemPackage(packageName: String, userId: Int): Boolean {
        val pm = service ?: return true
        val target = iface ?: return true

        val info = queryApplicationInfo(pm, target, packageName, userId) ?: return true
        val flags = runCatching {
            Reflection.getDeclaredField(info.javaClass, "flags").getInt(info)
        }.getOrNull() ?: return true

        return (flags and FLAG_SYSTEM) != 0 || (flags and FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /** 查询安装来源信息；失败返回 null，调用方据此放行 */
    fun installSourceInfo(packageName: String, userId: Int): Any? {
        val pm = service ?: return null
        val target = iface ?: return null

        return runCatching {
            Reflection.getDeclaredMethod(
                target,
                "getInstallSourceInfo",
                String::class.java,
                Int::class.java,
            ).invoke(pm, packageName, userId)
        }.getOrNull()
    }

    /** 取 Play 商店的签名信息，用于伪造「用户安装」来源 */
    fun playStoreSigningInfo(userId: Int): Any? {
        val pm = service ?: return null
        val target = iface ?: return null

        return runCatching {
            val info = Reflection.getDeclaredMethod(
                target,
                "getPackageInfo",
                String::class.java,
                Long::class.java,
                Int::class.java,
            ).invoke(pm, Names.PLAY_STORE, GET_SIGNING_CERTIFICATES.toLong(), userId)

            Reflection.getDeclaredMethod(info!!.javaClass, "getSigningInfo").invoke(info)
        }.getOrNull()
    }

    /**
     * 构造一个伪造的 `InstallSourceInfo`。
     *
     * 构造函数在不同版本参数个数不同，这里按参数个数从多到少试。
     */
    fun buildInstallSourceInfo(
        installerPackageName: String?,
        signingInfo: Any?,
        packageSource: Int,
        userId: Int,
    ): Any? {
        val clazz = load("android.content.pm.InstallSourceInfo") ?: return null

        val candidates = listOf<Array<Any?>>(
            arrayOf(
                installerPackageName,
                signingInfo,
                installerPackageName,
                installerPackageName,
                installerPackageName,
                packageSource,
            ),
            arrayOf(installerPackageName, signingInfo, packageSource),
            arrayOf(installerPackageName, signingInfo),
        )

        for (args in candidates) {
            val ctor = clazz.constructors.firstOrNull { it.parameterCount == args.size } ?: continue
            val built = runCatching {
                ctor.isAccessible = true
                ctor.newInstance(*args)
            }.getOrNull()
            if (built != null) return built
        }

        return null
    }

    private fun queryApplicationInfo(pm: Any, target: Class<*>, packageName: String, userId: Int): Any? {
        // Android 13 起 flags 由 int 变为 long
        val longVariant = runCatching {
            Reflection.getDeclaredMethod(
                target,
                "getApplicationInfo",
                String::class.java,
                Long::class.java,
                Int::class.java,
            ).invoke(pm, packageName, 0L, userId)
        }.getOrNull()
        if (longVariant != null) return longVariant

        return runCatching {
            Reflection.getDeclaredMethod(
                target,
                "getApplicationInfo",
                String::class.java,
                Int::class.java,
                Int::class.java,
            ).invoke(pm, packageName, 0, userId)
        }.getOrNull()
    }

    private fun load(name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()
}