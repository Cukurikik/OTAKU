package com.example.data.sources.tachiyomi

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object BooleanOrIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BooleanOrInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
    override fun deserialize(decoder: Decoder): Int {
        val input = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = input.decodeJsonElement()
        return if (element is JsonPrimitive) {
            if (element.isString) {
                element.content.toIntOrNull() ?: 0
            } else {
                val booleanValue = element.content.toBooleanStrictOrNull()
                if (booleanValue != null) {
                    if (booleanValue) 1 else 0
                } else {
                    element.content.toIntOrNull() ?: 0
                }
            }
        } else {
            0
        }
    }
}

@Serializable
data class AvailableExtension(
    val pkg: String,
    val name: String,
    val lang: String = "all",
    val apk: String = "",
    val version: String = "",
    val code: Long = 0L,
    @Serializable(with = BooleanOrIntSerializer::class) val nsfw: Int = 0,
    val icon: String = "",
    @Serializable(with = BooleanOrIntSerializer::class) @SerialName("hasReadme") val hasReadme: Int = 0,
    @Serializable(with = BooleanOrIntSerializer::class) @SerialName("hasChangelog") val hasChangelog: Int = 0,
    val sources: List<ExtensionSource> = emptyList(),
    // Internal fields (tidak dari JSON)
    @kotlinx.serialization.Transient var repoUrl: String = "" // Ditambahkan saat parsing untuk melacak asal repo
)

@Serializable
data class ExtensionSource(
    val id: Long = 0L,
    val lang: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val versionId: Int = 0
)

data class InstalledExtension(
    val pkgName: String,
    val name: String,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val isNsfw: Boolean,
    val pkgFactory: String?,
    val isObsolete: Boolean = false,
    val hasUpdate: Boolean = false
) {
    val pkg get() = pkgName
    val mainClass get() = pkgFactory
}

@Serializable
data class ExtensionRepo(
    val id: String,
    val name: String,
    val url: String,           // URL ke index.min.json
    val apkBaseUrl: String,    // Base URL untuk download APK
    val category: RepoCategory,
    val isDefault: Boolean = false,
    val description: String = ""
)

enum class RepoCategory { MANGA, ANIME, MIXED }
