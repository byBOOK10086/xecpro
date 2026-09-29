package com.xecpro.xechide.zygote.hook

import com.xecpro.xechide.zygote.runtime.HideRuntime

/** 一组 hook 的安装单元 */
abstract class HideHook(
    protected val kit: HookKit,
    protected val runtime: HideRuntime,
) {
    abstract val tag: String

    abstract fun install()
}