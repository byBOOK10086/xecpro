#!/system/bin/sh

# XecHide 靠 Zygisk 把 dex 注入 system_server 才生效。没有 Zygisk 框架时装上去
# 只会得到一个「看起来装好了、实际什么都没做」的模块，所以直接拦在安装阶段。

find_zygisk() {
    for id in "$@"; do
        [ -d "/data/adb/modules/$id" ] || continue
        [ -f "/data/adb/modules/$id/disable" ] && continue
        [ -f "/data/adb/modules/$id/remove" ] && continue
        echo "$id"
        return 0
    done
    return 1
}

ZYGISK_ID=$(find_zygisk zygisksu rezygisk zygisk_on_ksu yukizygisk onyxzygisk admirepowered)

if [ -z "$ZYGISK_ID" ]; then
    abort "! 未检测到已启用的 Zygisk 框架；本模块需要 Zygisk 才能注入 system_server，安装已中止"
fi

ui_print "- Zygisk 框架: $ZYGISK_ID"