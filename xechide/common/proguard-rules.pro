# kotlinx.serialization：序列化器是靠 Companion 上的 serializer() 反射取到的，
# 混淆掉这些成员会让 HideConfig 在解码时直接抛异常（引擎随即静默回退到空配置）。
# 规则取自 kotlinx.serialization 官方文档。
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# 刻意不保留 com.xecpro.xechide.common.** 的类名。
# 配置的编解码全程走 HideConfig.serializer() 这种编译期引用，没有一处按名字反射，
# 所以混淆掉这些类不会影响功能；反之若保留，dump 出来的 dex 里会直接出现
# HideConfig / RuleEngine / BlobCodec 这些一眼就能看出用途的符号，
# 与 zygote 模块「被 dump 也不该有可辨符号」的目标冲突。

-dontwarn android.**
-dontwarn com.android.**