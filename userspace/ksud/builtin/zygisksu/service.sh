#!/system/bin/sh
# Zygisk Next (built-in) - service stage
MODDIR=${0%/*}

if [ "$ZYGISK_ENABLED" = "1" ]; then
  exit 0
fi

"$MODDIR/bin/zygiskd" service-stage
