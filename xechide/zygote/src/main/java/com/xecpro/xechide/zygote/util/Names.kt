package com.xecpro.xechide.zygote.util

/**
 * hook 目标常量。
 *
 * 全部是 AOSP 内部类/方法名，属于客观事实；按 Android 版本分支的地方在
 * 各 hook 实现里显式判断，不在这里做版本矩阵。
 */
object Names {

    const val SYSTEM_SERVER = "com.android.server.SystemServer"
    const val RUNTIME_INIT = "com.android.internal.os.RuntimeInit"
    const val ZYGOTE_INIT = "com.android.internal.os.ZygoteInit"

    const val COMPUTER_ENGINE = "com.android.server.pm.ComputerEngine"
    const val PACKAGE_MANAGER_SERVICE = "com.android.server.pm.PackageManagerService"
    const val APPS_FILTER_IMPL = "com.android.server.pm.AppsFilterImpl"
    const val APPS_FILTER = "com.android.server.pm.AppsFilter"

    const val ACTIVITY_STARTER = "com.android.server.wm.ActivityStarter"
    const val ACTIVITY_TASK_SUPERVISOR = "com.android.server.wm.ActivityTaskSupervisor"
    const val ACTIVITY_STACK_SUPERVISOR = "com.android.server.wm.ActivityStackSupervisor"

    const val ZYGOTE_PROCESS = "android.os.ZygoteProcess"
    const val NATIVE_ZYGOTE_PROCESS = "android.os.NativeZygoteProcess"
    const val SERVICE_RECORD = "com.android.server.am.ServiceRecord"

    const val ACTIVITY_MANAGER_SERVICE = "com.android.server.am.ActivityManagerService"
    const val PROCESS_LIST = "com.android.server.am.ProcessList"

    const val CONTENT_PROVIDER_TRANSPORT = "android.content.ContentProvider\$Transport"

    const val PACKAGE_SERVICE = "package"
    const val PACKAGE_NATIVE_SERVICE = "package_native"

    const val CONSTRUCTOR = "<init>"

    // ---- 设置项隐藏的 hook 目标 ----

    const val ACCESSIBILITY_MANAGER_SERVICE =
        "com.android.server.accessibility.AccessibilityManagerService"
    const val INPUT_METHOD_MANAGER_SERVICE =
        "com.android.server.inputmethod.InputMethodManagerService"

    /**
     * 设置项存储。AOSP 的 SettingsProvider 声明在 `system` 进程里，
     * 因此可以直接在 system_server 内拦下 GET_global / GET_secure 这类读取。
     */
    const val SETTINGS_PROVIDER = "com.android.providers.settings.SettingsProvider"

    /** [android.provider.Settings.NameValueTable.VALUE]，call() 返回值放在这个 key 下 */
    const val SETTINGS_VALUE_KEY = "value"

    /** 应用商店包名：伪造「用户安装」来源时统一指向它 */
    const val PLAY_STORE = "com.android.vending"

    const val UID_SYSTEM = 1000
}