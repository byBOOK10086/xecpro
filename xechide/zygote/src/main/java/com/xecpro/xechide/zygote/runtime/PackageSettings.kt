package com.xecpro.xechide.zygote.runtime

import com.v7878.unsafe.Reflection

/**
 * 从 PMS 内部的 PackageSetting 对象上取包名。
 *
 * 不同版本这个类叫 `PackageSetting` / `PackageSettingBase` / `PackageImpl` / `AndroidPackage`，
 * 取包名的入口也从 `name` 字段变成 `mName` 再变成 `getPackageName()`，所以全部按候选顺序试。
 */
object PackageSettings {

    private val TYPE_NAMES = setOf(
        "PackageSetting",
        "PackageSettingBase",
        "PackageImpl",
        "AndroidPackage",
    )

    private val NAME_ACCESSORS = listOf("getPackageName", "getName")
    private val NAME_FIELDS = listOf("mName", "name", "packageName")

    fun isSettingType(clazz: Class<*>?): Boolean = clazz != null && clazz.simpleName in TYPE_NAMES

    fun nameOf(setting: Any?): String? {
        if (setting == null) return null

        for (accessor in NAME_ACCESSORS) {
            val value = runCatching {
                Reflection.getDeclaredMethod(setting.javaClass, accessor).invoke(setting)
            }.getOrNull()
            if (value is String && value.isNotEmpty()) return value
        }

        for (field in NAME_FIELDS) {
            val value = runCatching {
                Reflection.getDeclaredField(setting.javaClass, field).get(setting)
            }.getOrNull()
            if (value is String && value.isNotEmpty()) return value
        }

        return null
    }
}