# module.prop 里的 entrypoint 是运行时按字符串反射查找的，这个类的类名与包名不能变。
#
# 除此之外刻意什么都不保留：hook 实现、vmtools、PanamaPort 全部交给 R8 混淆与裁剪。
# 这些库都自带 consumer 规则处理各自的反射点（HMA-OSS 同样只 keep 入口类），
# 而混淆本身就是这个模块的一层隐藏 —— 被 dump 出来的 dex 里不该出现
# PmsHook / shouldHide / xechide 这类一眼可辨的符号。
-keep class com.xecpro.xechide.zygote.HideEntry { premain(); main(); }

# R8Annotations 的注解只在编译期给 R8 读，运行期没有对应实现
-dontwarn com.v7878.r8.annotations.**