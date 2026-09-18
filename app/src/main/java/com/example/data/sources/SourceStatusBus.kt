package com.example.data.sources

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import retrofit2.HttpException

/**
 * Lightweight in-memory error board so the UI can surface a clear,
 * actionable message ("domain X tidak dapat diakses, ganti URL di
 * pengaturan") instead of a silently empty list when a scraping source's
 * domain is dead, blocked, or behind bot-protection.
 *
 * Sources should call [report] on failure and [clear] on success. This is
 * intentionally process-scoped (not persisted) — it reflects the current
 * session's connectivity, which is what the user needs to act on.
 */
object SourceStatusBus {
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors

    fun report(sourceId: String, message: String) {
        _errors.value = _errors.value + (sourceId to message)
    }

    fun reportFromException(sourceId: String, sourceName: String, e: Throwable) {
        val message = describe(sourceName, e)
        report(sourceId, message)
    }

    fun clear(sourceId: String) {
        if (_errors.value.containsKey(sourceId)) {
            _errors.value = _errors.value - sourceId
        }
    }

    private fun describe(sourceName: String, e: Throwable): String = when (e) {
        is UnknownHostException ->
            "$sourceName: domain tidak dapat ditemukan (DNS gagal). Domain mungkin sudah mati atau diblokir ISP. Ganti URL di Pengaturan > Status Sumber."
        is ConnectException ->
            "$sourceName: tidak dapat terhubung ke server (Connection Refused). Coba lagi nanti atau periksa koneksi."
        is SocketTimeoutException ->
            "$sourceName: koneksi timeout (${e.message ?: "Waktu tunggu habis"}). Server target lambat atau memblokir request."
        is SSLHandshakeException ->
            "$sourceName: koneksi aman (SSL/TLS Handshake) gagal. Sertifikat domain mungkin tidak valid atau kedaluwarsa."
        is HttpException ->
            "$sourceName: HTTP ${e.code()} (${e.message()}). Server menolak permintaan."
        is IOException ->
            "$sourceName: I/O Error jaringan: ${e.message ?: "Koneksi terputus"}."
        else ->
            "$sourceName: ${e.message ?: e::class.java.simpleName}"
    }
}
