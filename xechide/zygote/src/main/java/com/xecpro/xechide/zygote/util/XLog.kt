package com.xecpro.xechide.zygote.util

import android.util.Log

/**
 * 统一日志出口。
 *
 * 默认只输出 warn 以上，避免在 system_server 里刷 logcat 暴露 hook 的存在；
 * 调试时通过 [verbose] / [enabled] 打开。
 */
object XLog {

    /** 固定 tag，不使用模块名，减少被 grep 命中的概率 */
    private const val TAG = "AndroidRuntime"

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var verbose: Boolean = false

    fun v(scope: String, message: () -> String) {
        if (enabled && verbose) Log.v(TAG, line(scope, message()))
    }

    fun d(scope: String, message: () -> String) {
        if (enabled && verbose) Log.d(TAG, line(scope, message()))
    }

    fun i(scope: String, message: () -> String) {
        if (enabled) Log.i(TAG, line(scope, message()))
    }

    fun w(scope: String, message: () -> String) {
        if (enabled) Log.w(TAG, line(scope, message()))
    }

    fun e(scope: String, cause: Throwable? = null, message: () -> String) {
        if (!enabled) return
        if (cause == null) Log.e(TAG, line(scope, message())) else Log.e(TAG, line(scope, message()), cause)
    }

    private fun line(scope: String, message: String) = "[$scope] $message"
}