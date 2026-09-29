package com.xecpro.xechide.zygote;

import android.util.Log;

import com.v7878.r8.annotations.DoNotObfuscate;
import com.v7878.r8.annotations.DoNotObfuscateType;
import com.v7878.r8.annotations.DoNotShrink;
import com.v7878.r8.annotations.DoNotShrinkType;

/**
 * Zygisk 入口。
 *
 * 由 ZygoteLoader 生成的 .so 在 system_server 里反射调用，类名与方法名都不能被混淆，
 * 因此这里全量标注 R8 保留注解。
 */
@SuppressWarnings("unused")
@DoNotObfuscateType
@DoNotShrinkType
public final class HideEntry {

    public static final String TAG = "HideEntry";

    @DoNotObfuscate
    @DoNotShrink
    public static void premain() {
        // system_server 注入场景下无需在 main 之前做事
    }

    @DoNotObfuscate
    @DoNotShrink
    public static void main() {
        try {
            SystemServerBootstrap.init();
        } catch (Throwable t) {
            Log.e("AndroidRuntime", "[HideEntry] 引擎初始化失败", t);
        }
    }
}