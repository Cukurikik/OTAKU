package com.example.network.server

data class SharedLanFile(
    val name: String,
    val path: String, // Absolute path on the server
    val size: Long,
    val type: String // MIME type
)
