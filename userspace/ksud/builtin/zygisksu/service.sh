#!/system/bin/sh
# Zygisk Next (built-in) - service stage
MODDIR=${0%/*}

# 兼容官方 KernelSU 守护进程路径（与 post-fs-data 阶段保持一致的双保险）。
DAEMON=/data/adb/xudc
if [ -x "$DAEMON" ]; then
  mkdir -p /data/adb/ksu/bin 2>/dev/null
  for L in /data/adb/ksud /data/adb/ksu/bin/ksud; do
    [ -L "$L" ] && [ "$(readlink "$L")" = "$DAEMON" ] && continue
    rm -f "$L" 2>/dev/null
    ln -sf "$DAEMON" "$L" 2>/dev/null
  done
fi

if [ "$ZYGISK_ENABLED" = "1" ]; then
  exit 0
fi

"$MODDIR/bin/zygiskd" service-stage
