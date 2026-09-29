#!/system/bin/sh

# 卸载即清场：配置、构建戳、以及发布给 system_server 的镜像都不留，
# 免得设备上永久躺着两份来历不明的 BCFG blob。

rm -f /data/adb/ksu/cfg/9.cfg /data/adb/ksu/cfg/9.cfg.ver
rm -rf /data/system/sysfwk

exit 0