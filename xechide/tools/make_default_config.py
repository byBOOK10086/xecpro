#!/usr/bin/env python3
"""生成 XecHide 的默认配置 blob。

配置以 ksud 内置模块的同款 BCFG 容器落盘（见 userspace/ksud/src/module.rs 的
pack_builtin_module），整段按 key `xdcv1` 循环异或，因此文件放在
/data/adb/ksu/cfg/ 里与内置模块的 0.cfg~4.cfg 完全同形。

用法：
    python tools/make_default_config.py                    # 写入 zygote/src/main/assets/9.cfg
    python tools/make_default_config.py --out /tmp/9.cfg
    python tools/make_default_config.py --dump /tmp/9.cfg  # 反解并打印明文
"""

import argparse
import json
import pathlib
import sys

MAGIC = b"BCFG"
KEY = b"xdcv1"
CONTAINER_VERSION = 1
ENTRY_NAME = "hide.json"
ENTRY_MODE = 0o644

# 默认配置：开箱即隐藏 root 管理器与 root 检测器。
# defaultRule 对「所有调用方」生效，scope 留空表示不额外针对单个应用定制；
# 需要更细的规则时改这里，或直接用管理器改写设备上的 9.cfg。
DEFAULT_CONFIG = {
    "version": 1,
    "enabled": True,
    "blockActivityLaunch": True,
    "spoofInstallSource": True,
    "defaultRule": {
        "whitelist": False,
        "excludeSystemApps": True,
        "hideInstallSource": False,
        "hideSystemInstallSource": False,
        "invertActivityGuard": False,
        "templates": [],
        "packages": [
            "com.xecpro.kernel",
            "me.weishu.kernelsu",
            "com.topjohnwu.magisk",
            "io.github.huskydg.magisk",
            "eu.chainfire.supersu",
            "com.scottyab.rootbeer",
            "com.joeykrim.rootcheck",
            "org.lsposed.manager",
            "moe.shizuku.privileged.api",
            "rikka.shizuku",
        ],
        "oppositePackages": [],
    },
    "templates": {},
    "scope": {},
}


def xor(data: bytes) -> bytes:
    return bytes(b ^ KEY[i % len(KEY)] for i, b in enumerate(data))


def pack(entries) -> bytes:
    buf = bytearray(MAGIC)
    buf.append(CONTAINER_VERSION)
    buf += len(entries).to_bytes(4, "little")
    for path, mode, data in entries:
        raw_path = path.encode("utf-8")
        buf += len(raw_path).to_bytes(2, "little")
        buf += raw_path
        buf += mode.to_bytes(4, "little")
        buf += len(data).to_bytes(4, "little")
        buf += data
    return xor(bytes(buf))


def unpack(blob: bytes):
    raw = xor(blob)
    if len(raw) < 9 or raw[:4] != MAGIC:
        raise ValueError("bad BCFG magic")
    count = int.from_bytes(raw[5:9], "little")
    off = 9
    out = []
    for _ in range(count):
        plen = int.from_bytes(raw[off:off + 2], "little")
        off += 2
        path = raw[off:off + plen].decode("utf-8")
        off += plen
        mode = int.from_bytes(raw[off:off + 4], "little")
        off += 4
        dlen = int.from_bytes(raw[off:off + 4], "little")
        off += 4
        out.append((path, mode, raw[off:off + dlen]))
        off += dlen
    return out


def encode_default() -> bytes:
    text = json.dumps(DEFAULT_CONFIG, separators=(",", ":"), ensure_ascii=False)
    return pack([(ENTRY_NAME, ENTRY_MODE, text.encode("utf-8"))])


def main() -> int:
    default_out = pathlib.Path(__file__).resolve().parent.parent / "zygote/src/main/assets/9.cfg"

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=pathlib.Path, default=default_out)
    parser.add_argument("--dump", type=pathlib.Path, help="反解已有的 blob 并打印明文")
    args = parser.parse_args()

    if args.dump:
        for path, mode, data in unpack(args.dump.read_bytes()):
            print(f"--- {path} (mode {mode:04o}, {len(data)} bytes)")
            print(json.dumps(json.loads(data.decode("utf-8")), indent=2, ensure_ascii=False))
        return 0

    blob = encode_default()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(blob)
    print(f"已写入 {args.out} ({len(blob)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())