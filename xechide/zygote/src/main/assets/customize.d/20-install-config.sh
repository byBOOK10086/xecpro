#!/system/bin/sh

# 配置与内核内置模块同库、同容器格式、同命名风格：/data/adb/ksu/cfg/ 下的编号
# BCFG blob（整段按 xdcv1 异或）。放在这里，一个 `ls` 看不出它和 0.cfg~4.cfg 的区别。
#
# 只在缺失时写入默认规则，避免覆盖用户已经调好的配置。

CFG_DIR=/data/adb/ksu/cfg
CFG_FILE="$CFG_DIR/9.cfg"

# 发布给 system_server 读的镜像。
#
# hook 跑在 system_server（uid 1000）里，而 /data/adb 是 0700 root、标签 adb_data_file，
# uid 1000 既过不了 DAC 也不在允许域里；/data/system 是 0770 system、标签 system_data_file，
# 正是它日常读写的那一类，所以复制一份过去、放开到 0644。
MIRROR_DIR=/data/system/sysfwk
MIRROR_FILE=/data/system/sysfwk/9.cfg

publish_mirror() {
    [ -f "$CFG_FILE" ] || return 0
    mkdir -p "$MIRROR_DIR" || return 0
    cp -f "$CFG_FILE" "$MIRROR_FILE" || return 0
    chmod 0755 "$MIRROR_DIR" 2>/dev/null || true
    chmod 0644 "$MIRROR_FILE" 2>/dev/null || true
    return 0
}

if [ ! -d "$CFG_DIR" ]; then
    mkdir -p "$CFG_DIR"
    chmod 0700 "$CFG_DIR"
fi

if [ -f "$CFG_FILE" ]; then
    ui_print "- 保留已有配置: $CFG_FILE"
else
    cp -f "$MODPATH/9.cfg" "$CFG_FILE"
    chmod 0600 "$CFG_FILE"

    # ksud 会给每个 blob 配一个 <token>.ver 构建戳。补一个同款戳，
    # 免得 9.cfg 在一堆成对的 0.cfg / 0.cfg.ver 里反而显眼。
    STAMP=$(find "$CFG_DIR" -maxdepth 1 -name '*.cfg.ver' -print -quit 2>/dev/null)
    if [ -n "$STAMP" ]; then
        cp -f "$STAMP" "$CFG_DIR/9.cfg.ver"
    fi

    ui_print "- 已写入默认隐藏规则: $CFG_FILE"
fi

publish_mirror

# 安装包里的这份明文副本不留在模块目录
rm -f "$MODPATH/9.cfg"