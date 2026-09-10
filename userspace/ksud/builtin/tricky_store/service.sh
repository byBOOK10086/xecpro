#!/system/bin/sh
# Reconstructed init for the built-in TEESimulator-RS engine.
# NOTE: verify the daemon invocation against your exact engine release before shipping.
MODPATH=${0%/*}
DATA=/data/adb/.xudc_secure

# Idempotent data-dir setup (preserves user updates).
mkdir -p "$DATA" 2>/dev/null
chmod 700 "$DATA" 2>/dev/null
[ -f "$DATA/keybox.xml" ] || cp -f "$MODPATH/keybox.xml" "$DATA/keybox.xml" 2>/dev/null
[ -f "$DATA/target.txt" ] || cp -f "$MODPATH/target.txt" "$DATA/target.txt" 2>/dev/null
chmod 600 "$DATA/keybox.xml" "$DATA/target.txt" 2>/dev/null

# Launch the engine daemon. Data-dir/file paths are compiled into the binary.
# daemon expects the module dir as $1 so it can locate classes.dex.
"$MODPATH/daemon" "$MODPATH" >/dev/null 2>&1 &