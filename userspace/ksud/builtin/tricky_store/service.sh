#!/system/bin/sh
# Built-in TEESimulator-RS engine init.
# Mirrors upstream module/service.sh (Enginex0/TEESimulator-RS): supervisor-wrapped
# daemon, cwd inside the module dir, and a fully seeded config dir — the engine
# refuses to produce attestation certs without hbk, and keystore-side components
# need world-readable files under the data dir.
MODPATH=${0%/*}
cd "$MODPATH" || exit
DATA=/data/adb/.xudc_secure

# Data-dir setup (idempotent, preserves user edits).
mkdir -p "$DATA" 2>/dev/null
chmod 755 "$DATA" 2>/dev/null
[ -f "$DATA/keybox.xml" ] || cp -f "$MODPATH/keybox.xml" "$DATA/keybox.xml" 2>/dev/null
[ -f "$DATA/target.txt" ] || cp -f "$MODPATH/target.txt" "$DATA/target.txt" 2>/dev/null
# Spoofed security patch date mirrors the system prop unless the user overrides.
[ -f "$DATA/security_patch.txt" ] || printf 'system=prop\n' > "$DATA/security_patch.txt" 2>/dev/null
# Device-unique hardware-bound key seed, 32 bytes, generated once (upstream: dd).
[ -f "$DATA/hbk" ] || dd if=/dev/random of="$DATA/hbk" bs=32 count=1 2>/dev/null
chmod 644 "$DATA/keybox.xml" "$DATA/target.txt" "$DATA/security_patch.txt" "$DATA/hbk" 2>/dev/null
# Stale status from a previous boot would misreport engine state.
rm -f "$DATA/tee_status.txt" 2>/dev/null

# Fork-based supervisor for instant restart (upstream init).
./supervisor ./daemon "$MODPATH" >/dev/null 2>&1 &
