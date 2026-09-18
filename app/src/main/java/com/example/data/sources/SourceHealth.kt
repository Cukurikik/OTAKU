package com.example.data.sources

import android.content.Context

data class SourceHealthReport(
    val sourceId: String,
    val sourceName: String,
    val effectiveBaseUrl: String,
    val isOverridden: Boolean,
    val isKnownDead: Boolean,
    val label: String,
    val actionHint: String,
)

object SourceHealthCatalog {
    private val knownDeadSourceIds = setOf("drakor", "dramaindo")

    fun isKnownDead(sourceId: String): Boolean = sourceId in knownDeadSourceIds

    fun build(context: Context, source: ConfigurableSource, sourceName: String? = null): SourceHealthReport {
        val resolvedName = sourceName ?: source.sourceId
        val effectiveBaseUrl = source.effectiveBaseUrl(context)
        val overridden = SourceBaseUrlStore.isOverridden(context, source.sourceId)
        val knownDead = source.isDomainKnownDead || isKnownDead(source.sourceId)

        val label = when {
            knownDead && overridden -> "known-dead / overridden"
            knownDead -> "known-dead"
            overridden -> "overridden"
            else -> "active"
        }

        val actionHint = when {
            knownDead ->
                "Domain default tidak bisa dipakai. Buka Profil → Ganti URL Sumber, atau nonaktifkan source ini."
            overridden ->
                "Source memakai URL kustom. Refresh source untuk menguji domain baru."
            else ->
                "Source aktif. Jika gagal memuat, cek DNS, SSL, atau blokir ISP."
        }

        return SourceHealthReport(
            sourceId = source.sourceId,
            sourceName = resolvedName,
            effectiveBaseUrl = effectiveBaseUrl,
            isOverridden = overridden,
            isKnownDead = knownDead,
            label = label,
            actionHint = actionHint
        )
    }

    fun classifyFailure(throwable: Throwable): String = when {
        throwable is java.net.UnknownHostException ->
            "DNS gagal / domain tidak ditemukan"
        throwable is java.net.ConnectException ->
            "Tidak dapat terhubung ke server"
        throwable is javax.net.ssl.SSLHandshakeException ->
            "Handshake SSL gagal"
        throwable.message?.contains("404", ignoreCase = true) == true ||
            throwable.message?.contains("Not found", ignoreCase = true) == true ->
            "Halaman tidak ditemukan"
        throwable.message?.contains("timeout", ignoreCase = true) == true ->
            "Timeout saat memuat source"
        else -> throwable.message ?: throwable::class.simpleName.orEmpty()
    }
}
