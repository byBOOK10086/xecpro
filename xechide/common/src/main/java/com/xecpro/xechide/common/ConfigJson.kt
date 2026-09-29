package com.xecpro.xechide.common

import kotlinx.serialization.json.Json

/** 配置的 JSON 编解码入口，两端共用同一份设置，保证格式一致 */
object ConfigJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        allowStructuredMapKeys = false
    }

    fun encode(config: HideConfig): String = json.encodeToString(HideConfig.serializer(), config)

    fun decode(text: String): HideConfig = json.decodeFromString(HideConfig.serializer(), text)

    /** 从 BCFG 容器解出配置；任何异常都回退到空配置，避免 hook 端因坏配置崩溃 */
    fun decodeBlob(blob: ByteArray): HideConfig {
        val text = BlobCodec.unpackJson(blob) ?: return HideConfig()
        return runCatching { decode(text) }.getOrElse { HideConfig() }
    }

    fun encodeBlob(config: HideConfig): ByteArray = BlobCodec.packJson(encode(config))
}