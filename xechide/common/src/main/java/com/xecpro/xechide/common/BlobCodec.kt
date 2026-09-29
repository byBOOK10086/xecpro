package com.xecpro.xechide.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 与 ksud 内置模块存储完全一致的容器格式。
 *
 * 明文结构：
 * ```
 * "BCFG" | version:u8 | entryCount:u32le
 *   per entry: pathLen:u16le | path | mode:u32le | dataLen:u32le | data
 * ```
 * 整段再按 key `xdcv1` 循环异或。ksud 侧见 userspace/ksud/src/module.rs。
 *
 * 复用它的好处有两点：一是配置文件混在 /data/adb/ksu/cfg/ 的编号文件里不显眼，
 * 二是可以直接走内置模块的物化通道，不必另建一套读写代码。
 */
object BlobCodec {

    private val KEY = "xdcv1".toByteArray(Charsets.US_ASCII)
    private val MAGIC = "BCFG".toByteArray(Charsets.US_ASCII)
    private const val CONTAINER_VERSION = 1

    /** 配置在容器内的条目名，与模块 id 解耦，避免暴露用途 */
    const val DEFAULT_ENTRY = "hide.json"

    /** 配置文件的落盘位置：与内置模块 blob 同一目录，编号顺延 */
    const val CONFIG_PATH = "/data/adb/ksu/cfg/9.cfg"

    data class Entry(val path: String, val mode: Int, val data: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Entry && path == other.path && mode == other.mode && data.contentEquals(other.data)

        override fun hashCode(): Int = (path.hashCode() * 31 + mode) * 31 + data.contentHashCode()
    }

    fun xor(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        }
        return out
    }

    fun pack(entries: List<Entry>): ByteArray {
        var size = MAGIC.size + 1 + 4
        for (e in entries) {
            size += 2 + e.path.toByteArray(Charsets.UTF_8).size + 4 + 4 + e.data.size
        }

        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MAGIC)
        buf.put(CONTAINER_VERSION.toByte())
        buf.putInt(entries.size)

        for (e in entries) {
            val path = e.path.toByteArray(Charsets.UTF_8)
            buf.putShort(path.size.toShort())
            buf.put(path)
            buf.putInt(e.mode)
            buf.putInt(e.data.size)
            buf.put(e.data)
        }

        return xor(buf.array())
    }

    /** 解析失败一律返回 null，调用方据此回退到默认配置 */
    fun unpack(blob: ByteArray): List<Entry>? {
        val raw = xor(blob)
        if (raw.size < MAGIC.size + 5) return null
        for (i in MAGIC.indices) {
            if (raw[i] != MAGIC[i]) return null
        }

        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(MAGIC.size + 1)

        val count = buf.int
        if (count < 0 || count > 4096) return null

        val out = ArrayList<Entry>(count)
        repeat(count) {
            if (buf.remaining() < 2) return null
            val pathLen = buf.short.toInt() and 0xFFFF
            if (buf.remaining() < pathLen + 8) return null

            val pathBytes = ByteArray(pathLen)
            buf.get(pathBytes)
            val mode = buf.int
            val dataLen = buf.int
            if (dataLen < 0 || buf.remaining() < dataLen) return null

            val data = ByteArray(dataLen)
            buf.get(data)
            out.add(Entry(String(pathBytes, Charsets.UTF_8), mode, data))
        }

        return out
    }

    fun packJson(json: String, entryName: String = DEFAULT_ENTRY): ByteArray =
        pack(listOf(Entry(entryName, 0b110_100_100 /* 0644 */, json.toByteArray(Charsets.UTF_8))))

    fun unpackJson(blob: ByteArray, entryName: String = DEFAULT_ENTRY): String? {
        val entry = unpack(blob)?.firstOrNull { it.path == entryName } ?: return null
        return String(entry.data, Charsets.UTF_8)
    }
}