package com.example.data.sources.torrent

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.io.IOException

class TorrentStreamServer(private val stream: InputStream) : NanoHTTPD(0) {
    override fun serve(session: IHTTPSession): Response {
        return newChunkedResponse(Response.Status.OK, "video/mp4", stream)
    }
}
