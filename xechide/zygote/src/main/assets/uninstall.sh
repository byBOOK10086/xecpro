#!/system/bin/sh

# 卸载即清场：配置与构建戳都不留，免得设备上永久躺着一份来历不明的 BCFG blob。

rm -f /data/adb/ksu/cfg/9.cfg /data/adb/ksu/cfg/9.cfg.ver

exit 0