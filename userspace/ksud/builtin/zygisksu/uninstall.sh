#!/system/bin/sh
# Zygisk Next (built-in) - 清理运行时状态
rm -rf /data/adb/zygisksu 2>/dev/null
rm -f /data/adb/ksu/bin/znctl 2>/dev/null
rm -f /data/adb/ksu/bin/zygisk-ctl 2>/dev/null
rm -f /data/adb/service.d/.zn_cleanup.sh 2>/dev/null
