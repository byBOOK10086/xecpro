package com.xecpro.xechide.zygote.hook

import android.os.Build
// Hooks.hook 返回的是 android.util.Pair；显式 import 以盖过 kotlin.Pair 的默认导入
import android.util.Pair
import com.v7878.unsafe.ArtMethodUtils
import com.v7878.unsafe.Reflection
import com.v7878.unsafe.invoke.EmulatedStackFrame
import com.v7878.unsafe.invoke.Transformers
import com.v7878.vmtools.HookTransformer
import com.v7878.vmtools.Hooks
import com.xecpro.xechide.zygote.util.FrameKit.argument
import com.xecpro.xechide.zygote.util.FrameKit.arguments
import com.xecpro.xechide.zygote.util.FrameKit.returnValue
import com.xecpro.xechide.zygote.util.FrameKit.setReturn
import com.xecpro.xechide.zygote.util.Names
import com.xecpro.xechide.zygote.util.XLog
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

/** hook 回调对返回值的处置意图 */
class HookResult(initial: Any? = null) {
    var replaced: Boolean = false
        private set

    var value: Any? = initial
        set(newValue) {
            field = newValue
            replaced = true
        }

    var failure: Throwable? = null
}

/**
 * 方法 hook 安装器。
 *
 * 对上层只暴露「类名 + 方法名 + 可选参数个数」，内部负责：
 *  1. 沿继承链查找真实的可执行体（框架方法常声明在父类上）；
 *  2. 用 [Hooks] 装上前置/后置回调；
 *  3. 处理 Android 13 前后调用原方法的差异。
 *
 * Android 13 起 `Transformers.invokeExactNoChecks` 可直接透传原始帧；
 * 13 之前需要手动把 ArtMethod 的入口点换回原实现再反射调用，调用完再换回来。
 */
class HookKit(private val loader: ClassLoader?) {

    private companion object {
        const val TAG = "HookKit"
        const val ANY_ARG_COUNT = -1
    }

    private data class Slot(
        val methodName: String,
        val argumentCount: Int,
        var executable: Executable? = null,
        var entryPoints: Pair<Long, Long>? = null,
    )

    private val installed = ConcurrentHashMap<String, MutableList<Slot>>()

    @Volatile
    private var aborted = false

    /** 安装前置回调：先执行 [body]，[body] 未改写返回值时继续调用原方法 */
    fun before(
        className: String,
        methodName: String,
        argumentCount: Int = ANY_ARG_COUNT,
        body: (String, EmulatedStackFrame, HookResult) -> Unit,
    ) = install(className, methodName, argumentCount, true, body)

    /** 安装后置回调：先调用原方法，再把结果交给 [body] */
    fun after(
        className: String,
        methodName: String,
        argumentCount: Int = ANY_ARG_COUNT,
        body: (String, EmulatedStackFrame, HookResult) -> Unit,
    ) = install(className, methodName, argumentCount, false, body)

    /** 该方法是否已成功挂上，避免重复安装 */
    fun isHooked(className: String, methodName: String): Boolean =
        installed[className]?.any { it.methodName == methodName } == true

    /** 跨类名、跨方法名探测第一个存在的可执行体，用于名字随版本变化的方法 */
    fun probe(
        classNames: List<String>,
        methodNames: List<String>,
        argumentCount: Int = ANY_ARG_COUNT,
    ): Executable? {
        for (className in classNames) {
            var current = loadClass(className) ?: continue
            while (true) {
                for (methodName in methodNames) {
                    resolve(current, methodName, argumentCount)?.let { return it }
                }
                current = current.superclass ?: break
            }
        }
        return null
    }

    private fun install(
        className: String,
        methodName: String,
        argumentCount: Int,
        isBefore: Boolean,
        body: (String, EmulatedStackFrame, HookResult) -> Unit,
    ) {
        if (aborted) return

        val executable = findExecutable(className, methodName, argumentCount)
        if (executable == null) {
            XLog.i(TAG) { "跳过（未找到目标）: $className#$methodName($argumentCount)" }
            return
        }

        val slot = Slot(methodName, argumentCount, executable)

        val transformer = HookTransformer { original, frame ->
            val result = HookResult()

            if (isBefore) {
                runCatching { body(methodName, frame, result) }
                    .onFailure { XLog.e(TAG, it) { "$className#$methodName 前置回调异常" } }
            }

            if (!result.replaced) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Transformers.invokeExactNoChecks(original, frame)
                    } else {
                        callOriginalLegacy(slot, original, frame, result)
                    }
                } catch (t: Throwable) {
                    result.failure = t
                }
            }

            if (!isBefore && result.failure == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && frame.type().returnType() != Void.TYPE
            ) {
                result.value = frame.returnValue()
            }

            if (!isBefore) {
                runCatching { body(methodName, frame, result) }
                    .onFailure { XLog.e(TAG, it) { "$className#$methodName 后置回调异常" } }
            }

            result.failure?.let { throw it }

            // 只有确实改写过返回值才回写。前置回调未命中时帧里已是原方法的返回值，
            // 此时无条件回写会把 int/boolean 之类基本类型写成 null（甚至直接抛 NPE），
            // 等于让被挂钩的每个查询都返回空。
            if (result.replaced) frame.setReturn(result.value)
        }

        try {
            val entryPoints = Hooks.hook(
                executable,
                Hooks.EntryPointType.DIRECT,
                transformer,
                Hooks.EntryPointType.DIRECT,
            )
            slot.entryPoints = entryPoints
            installed.computeIfAbsent(className) { mutableListOf() }.add(slot)
            XLog.i(TAG) { "已挂钩: $className#$methodName($argumentCount)" }
        } catch (t: Throwable) {
            // 一次失败通常意味着框架内部结构不匹配，继续尝试只会刷屏
            aborted = true
            XLog.e(TAG, t) { "挂钩失败并中止后续安装: $className#$methodName" }
        }
    }

    /** Android 12 及以下的调用原方法路径：临时还原入口点 */
    @Suppress("DEPRECATION")
    private fun callOriginalLegacy(
        slot: Slot,
        original: MethodHandle,
        frame: EmulatedStackFrame,
        result: HookResult,
    ) {
        val executable = slot.executable ?: return
        val entryPoints = slot.entryPoints
        if (entryPoints == null) {
            // 挂钩刚生效、entryPoints 尚未回填的一瞬：此时换不回原入口点，
            // 退回通用路径，免得把未初始化的返回槽当成返回值交出去。
            Transformers.invokeExactNoChecks(original, frame)
            return
        }

        ArtMethodUtils.setExecutableEntryPoint(executable, entryPoints.second)
        try {
            val receiver = frame.argument(0)
            val args = frame.arguments().drop(1).toTypedArray()
            result.value = (executable as Method).invoke(receiver, *args)
        } finally {
            ArtMethodUtils.setExecutableEntryPoint(executable, entryPoints.first)
        }
    }

    private fun findExecutable(className: String, methodName: String, argumentCount: Int): Executable? {
        var current = loadClass(className) ?: return null
        while (true) {
            resolve(current, methodName, argumentCount)?.let { return it }
            current = current.superclass ?: return null
        }
    }

    private fun loadClass(className: String): Class<*>? = try {
        Class.forName(className, true, loader)
    } catch (t: Throwable) {
        XLog.i(TAG) { "类不存在: $className" }
        null
    }

    private fun resolve(clazz: Class<*>, methodName: String, argumentCount: Int): Executable? {
        if (methodName == Names.CONSTRUCTOR) {
            // 12 以下的构造函数 hook 会把 ART 搞崩，直接放弃
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            return Reflection.getHiddenConstructors(clazz)
                .firstOrNull { argumentCount < 0 || it.parameterCount == argumentCount }
        }

        return Reflection.getHiddenExecutables(clazz)
            .firstOrNull { it.name == methodName && (argumentCount < 0 || it.parameterCount == argumentCount) }
    }
}