#!/system/bin/sh

# 开机把隐藏规则发布到 system_server 读得到的位置。
#
# hook 运行在 system_server（uid 1000）进程里，而规则文件存放在 /data/adb/ksu/cfg/9.cfg：
# 该目录 0700 root、SELinux 标签 adb_data_file，uid 1000 既过不了 DAC、也不在允许域里，
# 直接读只会拿到 EACCES。这里把同一份 blob 原样复制到 /data/system/sysfwk/9.cfg ——
# 父目录 0770 system、标签 system_data_file，正是 system_server 日常读写的那一类，
# 文件放开到 0644 后它就能读到。
#
# 镜像与 canonical 是同一份字节，hook 侧两份都在轮询，改哪一份都会在 2 秒内生效。
# 安装期（20-install-config.sh）和管理器保存时（webroot/index.html）也会发布，这里只是
# 保证「换了设备状态 / 开机后 canonical 被改过」时两者仍然一致。

CFG_FILE=/data/adb/ksu/cfg/9.cfg
MIRROR_DIR=/data/system/sysfwk
MIRROR_FILE=/data/system/sysfwk/9.cfg

[ -f "$CFG_FILE" ] || exit 0

# post-fs-data 阶段 /data 已挂载，但个别机型上 /data/system 可能还没就绪，留几次重试。
attempt=0
while [ "$attempt" -lt 10 ]; do
    if mkdir -p "$MIRROR_DIR" 2>/dev/null; then
        if cp -f "$CFG_FILE" "$MIRROR_FILE" 2>/dev/null; then
            chmod 0755 "$MIRROR_DIR" 2>/dev/null
            chmod 0644 "$MIRROR_FILE" 2>/dev/null
            exit 0
        fi
    fi
    attempt=$((attempt + 1))
    sleep 2
done

exit 0
