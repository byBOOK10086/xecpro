package com.xecpro.xechide.zygote.util

import com.v7878.unsafe.invoke.EmulatedStackFrame
import com.v7878.unsafe.invoke.EmulatedStackFrame.RETURN_VALUE_IDX

/**
 * [EmulatedStackFrame] 的取值/写值封装。
 *
 * 原生 API 按 shorty 字符区分基本类型，这里统一收敛成一个 `Any?` 接口，
 * hook 实现里就不必再关心参数的实际类型。
 */
object FrameKit {

    fun EmulatedStackFrame.argumentCount(): Int = type().parameterCount()

    fun EmulatedStackFrame.returnType(): Class<*> = type().returnType()

    fun EmulatedStackFrame.argumentType(index: Int): Class<*> = accessor().getArgumentType(index)

    fun EmulatedStackFrame.shortyAt(index: Int): Char = accessor().getArgumentShorty(index)

    fun EmulatedStackFrame.argumentTypes(): Array<Class<*>> =
        Array(argumentCount()) { argumentType(it) }

    /** 取第 [index] 个参数；index 0 是接收者（静态方法则为 null） */
    fun EmulatedStackFrame.argument(index: Int): Any? {
        val accessor = accessor()
        return when (accessor.getArgumentShorty(index)) {
            'L' -> accessor.getReference(index)
            'Z' -> accessor.getBoolean(index)
            'B' -> accessor.getByte(index)
            'C' -> accessor.getChar(index)
            'S' -> accessor.getShort(index)
            'I' -> accessor.getInt(index)
            'J' -> accessor.getLong(index)
            'F' -> accessor.getFloat(index)
            'D' -> accessor.getDouble(index)
            else -> null
        }
    }

    fun EmulatedStackFrame.arguments(): Array<Any?> = Array(argumentCount()) { argument(it) }

    fun EmulatedStackFrame.setArgument(index: Int, value: Any?) {
        val accessor = accessor()
        when (accessor.getArgumentShorty(index)) {
            'L' -> accessor.setReference(index, value)
            'Z' -> accessor.setBoolean(index, value as Boolean)
            'B' -> accessor.setByte(index, value as Byte)
            'C' -> accessor.setChar(index, value as Char)
            'S' -> accessor.setShort(index, value as Short)
            'I' -> accessor.setInt(index, value as Int)
            'J' -> accessor.setLong(index, value as Long)
            'F' -> accessor.setFloat(index, value as Float)
            'D' -> accessor.setDouble(index, value as Double)
        }
    }

    /** 返回值为 void 时写入会被忽略，避免在 void 帧上抛异常 */
    fun EmulatedStackFrame.setReturn(value: Any?) {
        if (returnType() == Void.TYPE) return
        accessor().setValue(RETURN_VALUE_IDX, value)
    }

    fun EmulatedStackFrame.returnValue(): Any? = accessor().getValue(RETURN_VALUE_IDX)

    /** 按类型定位第一个参数，用于签名随版本漂移的方法（如 ZygoteProcess.start） */
    inline fun <reified T> EmulatedStackFrame.firstArgumentOfType(): T? =
        (0 until argumentCount())
            .firstOrNull { argumentType(it) == T::class.java }
            ?.let { argument(it) as? T }

    inline fun <reified T> EmulatedStackFrame.lastArgumentOfType(): T? =
        (argumentCount() - 1 downTo 0)
            .firstOrNull { argumentType(it) == T::class.java }
            ?.let { argument(it) as? T }

    /** 从后往前找最后一个满足条件的参数下标，找不到返回 -1 */
    inline fun EmulatedStackFrame.lastIndexWhere(predicate: (Class<*>) -> Boolean): Int =
        (argumentCount() - 1 downTo 0).firstOrNull { predicate(argumentType(it)) } ?: -1
}