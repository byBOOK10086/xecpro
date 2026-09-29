package com.xecpro.xechide.zygote.hook

import android.content.Intent
import com.xecpro.xechide.zygote.runtime.HideRuntime
import com.xecpro.xechide.zygote.util.FrameKit.argument
import com.xecpro.xechide.zygote.util.Mirror
import com.xecpro.xechide.zygote.util.Names
import com.xecpro.xechide.zygote.util.XLog

/**
 * Activity 启动保护。
 *
 * 仅隐藏包名还不够：如果某个应用持有目标应用的显式 ComponentName，
 * 仍能直接拉起它，而「拉得起来」本身就是隐藏失效的证据。
 * 这里在 `ActivityStarter.execute()` 上拦截，把结果改成「类不存在」。
 */
class ActivityGuardHook(kit: HookKit, runtime: HideRuntime) : HideHook(kit, runtime) {

    override val tag: String = "ActivityGuardHook"

    /** START_CLASS_NOT_FOUND：读不到时退回 4（AOSP 长期稳定的取值） */
    private val classNotFound: Int by lazy {
        Mirror.staticInt("android.app.ActivityManager", "START_CLASS_NOT_FOUND", 4)
    }

    override fun install() {
        kit.before(Names.ACTIVITY_STARTER, "execute") { _, frame, result ->
            val starter = frame.argument(0) ?: return@before

            val intent = Mirror.field(starter, "mIntent") as? Intent ?: return@before
            val target = intent.component?.packageName ?: return@before

            // mCallingUid 是发起方的真实 uid；取不到时用当前 binder 调用者兜底
            val callingUid = (Mirror.field(starter, "mCallingUid") as? Int)
                ?: android.os.Binder.getCallingUid()

            if (runtime.shouldBlockActivityLaunch(callingUid, target)) {
                XLog.d(tag) { "拦截 Activity 启动: uid=$callingUid -> $target" }
                result.value = classNotFound
            }
        }
    }
}