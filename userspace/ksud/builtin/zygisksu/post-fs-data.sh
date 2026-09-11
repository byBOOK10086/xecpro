#!/system/bin/sh
# Zygisk Next (built-in) - post-fs-data stage
# 运行时架构分发 + 启动 zygiskd daemon，等价官方 customize.sh 的安装期逻辑。

MODDIR=${0%/*}
cd "$MODDIR" || exit

# --- 架构检测 ---
case "$(uname -m)" in
  aarch64)       ARCH=arm64 ;;
  armv7*|armv8l) ARCH=arm   ;;
  x86_64)        ARCH=x64   ;;
  *)             ARCH=arm64 ;;
esac

# --- 32bit 支持检测 ---
HAS32BIT=false
if [ -n "$(getprop ro.product.cpu.abilist32)" ] || [ -n "$(getprop ro.system.product.cpu.abilist32)" ]; then
  HAS32BIT=true
fi

# --- 部署二进制到 zygiskd 期望的标准位置 ---
mkdir -p "$MODDIR/bin" "$MODDIR/lib" "$MODDIR/lib64"

if [ "$ARCH" = "x64" ]; then
  if [ "$HAS32BIT" = "true" ]; then
    cp -f "$MODDIR/bin/x86/zygiskd" "$MODDIR/bin/zygiskd32"
    cp -f "$MODDIR/lib/x86/libzygisk.so" "$MODDIR/lib/"
    cp -f "$MODDIR/lib/x86/libzn_loader.so" "$MODDIR/lib/"
  fi
  cp -f "$MODDIR/bin/x86_64/zygiskd" "$MODDIR/bin/zygiskd64"
  cp -f "$MODDIR/lib/x86_64/libzygisk.so" "$MODDIR/lib64/"
  cp -f "$MODDIR/lib/x86_64/libpayload.so" "$MODDIR/lib64/"
  cp -f "$MODDIR/lib/x86_64/libzn_loader.so" "$MODDIR/lib64/"
  ln -sf ./zygiskd64 "$MODDIR/bin/zygiskd"
else
  if [ "$ARCH" = "arm" ] || [ "$HAS32BIT" = "true" ]; then
    cp -f "$MODDIR/bin/armeabi-v7a/zygiskd" "$MODDIR/bin/zygiskd32"
    cp -f "$MODDIR/lib/armeabi-v7a/libzygisk.so" "$MODDIR/lib/"
    cp -f "$MODDIR/lib/armeabi-v7a/libzn_loader.so" "$MODDIR/lib/"
  fi
  if [ "$ARCH" = "arm64" ]; then
    cp -f "$MODDIR/bin/arm64-v8a/zygiskd" "$MODDIR/bin/zygiskd64"
    cp -f "$MODDIR/lib/arm64-v8a/libzygisk.so" "$MODDIR/lib64/"
    cp -f "$MODDIR/lib/arm64-v8a/libpayload.so" "$MODDIR/lib64/"
    cp -f "$MODDIR/lib/arm64-v8a/libzn_loader.so" "$MODDIR/lib64/"
    ln -sf ./zygiskd64 "$MODDIR/bin/zygiskd"
  else
    cp -f "$MODDIR/lib/armeabi-v7a/libpayload.so" "$MODDIR/lib/"
    ln -sf ./zygiskd32 "$MODDIR/bin/zygiskd"
  fi
fi

# --- machikado 注入载荷选择 ---
MARCH=$ARCH
if [ "$ARCH" != "arm" ] && [ "$HAS32BIT" = "true" ]; then
  MARCH="${ARCH}_32"
fi
cp -f "$MODDIR/machikado.$MARCH" "$MODDIR/machikado"

chmod 755 "$MODDIR/bin/zygiskd" "$MODDIR/bin/zygiskd64" "$MODDIR/bin/zygiskd32" 2>/dev/null

# --- 持久配置目录 ---
mkdir -p /data/adb/zygisksu
[ -f /data/adb/zygisksu/znctx ] && mv /data/adb/zygisksu/znctx /data/adb/zygisksu/znctx.old
[ -f /data/adb/zygisksu/modules_info ] && mv /data/adb/zygisksu/modules_info /data/adb/zygisksu/modules_info.old

# --- znctl 链接（管理器 / 命令行控制入口） ---
mkdir -p /data/adb/ksu/bin
rm -f /data/adb/ksu/bin/zygisk-ctl /data/adb/ksu/bin/znctl
ln -sf "$MODDIR/bin/zygiskd" /data/adb/ksu/bin/znctl

# --- 启动 daemon ---
export ZYGISK_ENABLED
[ -f /data/adb/zygisksu/klog ] && [ "1" = "$(cat /data/adb/zygisksu/klog)" ] && export KLOG_ENABLED=1
./bin/zygiskd daemon

if [ -d /data/adb/ksu/log ]; then
  cp /data/adb/zygisksu/znctx /data/adb/ksu/log/znctx 2>/dev/null
  cp /data/adb/zygisksu/modules_info /data/adb/ksu/log/modules_info 2>/dev/null
fi
