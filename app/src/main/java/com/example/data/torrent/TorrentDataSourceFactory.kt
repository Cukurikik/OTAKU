package com.example.data.torrent

import androidx.media3.datasource.DataSource
import androidx.media3.common.util.UnstableApi

@UnstableApi
class TorrentDataSourceFactory(private val torrentStreamManager: TorrentStreamManager) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return TorrentDataSource(torrentStreamManager)
    }
}
