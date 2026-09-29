package com.xecpro.xechide.zygote.util

import com.v7878.unsafe.Reflection

/** 少量反射便利方法，补齐 vmtools [Reflection] 没有覆盖的场景 */
object Mirror {

    /** 沿继承链查找实例字段，找不到返回 null */
    fun field(receiver: Any, name: String): Any? {
        var current: Class<*>? = receiver.javaClass
        while (current != null && current != Any::class.java) {
            // 取成 val 再传：current 是会被循环改写的局部变量，直接传进 lambda 无法智能转换
            val clazz = current
            val found = runCatching { Reflection.getDeclaredField(clazz, name).get(receiver) }
            if (found.isSuccess) return found.getOrNull()
            current = clazz.superclass
        }
        return null
    }

    /** 读取静态 int 常量，失败时用 [fallback]，避免把版本差异写死 */
    fun staticInt(className: String, fieldName: String, fallback: Int): Int = runCatching {
        val clazz = Class.forName(className, false, null)
        Reflection.getDeclaredField(clazz, fieldName).getInt(null)
    }.getOrDefault(fallback)
}