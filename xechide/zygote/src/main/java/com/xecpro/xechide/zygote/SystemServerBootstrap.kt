package com.xecpro.xechide.zygote

import android.os.Build
import com.v7878.unsafe.Reflection
import com.v7878.unsafe.invoke.Transformers
import com.v7878.vmtools.HookTransformer
import com.v7878.vmtools.Hooks
import com.xecpro.xechide.zygote.hook.ActivityGuardHook
import com.xecpro.xechide.zygote.hook.HookKit
import com.xecpro.xechide.zygote.hook.InstallSourceHook
import com.xecpro.xechide.zygote.hook.PmsHook
import com.xecpro.xechide.zygote.hook.SettingsHideHook
import com.xecpro.xechide.zygote.runtime.HideRuntime
import com.xecpro.xechide.zygote.util.FrameKit.argument
import com.xecpro.xechide.zygote.util.Names
import com.xecpro.xechide.zygote.util.XLog

/**
 * system_server 侧的启动引导。
 *
 * Zygisk 的 system_server 注入发生在类加载器就绪之前，因此这里做两段式：
 * 能直接拿到 system_server 的 ClassLoader 就立刻装 hook（Android 12+），
 * 否则回退到在 `RuntimeInit.findStaticMain` 上等它启动。
 */
object SystemServerBootstrap {

    private const val TAG = "Bootstrap"

    @Volatile
    private var runtime: HideRuntime? = null

    @JvmStatic
    fun init() {
        XLog.i(TAG) { "XecHide 引擎启动" }

        resolveClassLoader()?.let {
            onSystemServer(it)
            return
        }

        XLog.i(TAG) { "未直接取到 system_server 类加载器，改用 findStaticMain 等待" }
        hookRuntimeInit()
    }

    private fun resolveClassLoader(): ClassLoader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

        return runCatching {
            Reflection.getDeclaredMethod(
                Class.forName(Names.ZYGOTE_INIT),
                "getOrCreateSystemServerClassLoader",
            ).invoke(null) as? ClassLoader
        }.getOrNull()
    }

    private fun hookRuntimeInit() {
        val method = runCatching {
            Reflection.getDeclaredMethod(
                Class.forName(Names.RUNTIME_INIT),
                "findStaticMain",
                String::class.java,
                Array<String>::class.java,
                ClassLoader::class.java,
            )
        }.getOrNull()

        if (method == null) {
            XLog.w(TAG) { "找不到 RuntimeInit.findStaticMain，引擎无法启动" }
            return
        }

        Hooks.hook(
            method,
            Hooks.EntryPointType.CURRENT,
            HookTransformer { original, frame ->
                runCatching {
                    if (frame.argument(0) == Names.SYSTEM_SERVER) {
                        (frame.argument(2) as? ClassLoader)?.let { onSystemServer(it) }
                    }
                }.onFailure { XLog.e(TAG, it) { "findStaticMain 回调异常" } }

                Transformers.invokeExactNoChecks(original, frame)
            },
            Hooks.EntryPointType.DIRECT,
        )

        XLog.i(TAG) { "已挂上 findStaticMain" }
    }

    private fun onSystemServer(loader: ClassLoader) {
        if (runtime != null) return

        synchronized(this) {
            if (runtime != null) return

            val state = HideRuntime(loader)
            runtime = state
            state.bootstrap()

            val kit = HookKit(loader)
            val hooks = listOf(
                PmsHook(kit, state),
                InstallSourceHook(kit, state),
                ActivityGuardHook(kit, state),
                SettingsHideHook(kit, state),
            )

            for (hook in hooks) {
                runCatching { hook.install() }
                    .onFailure { XLog.e(TAG, it) { "${hook.tag} 安装失败" } }
            }

            XLog.i(TAG) { "已进入 system_server，hook 安装完毕" }
        }
    }
}