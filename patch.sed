/var exoPlayerHolder by remember { mutableStateOf<ExoPlayer?>(null) }/a \
    val exoPlayer = remember {\n\
        val uri = Uri.parse(initialUrl)\n\
        val defaultHeaders = mutableMapOf(\n\
            "User-Agent" to NetworkModule.userAgent,\n\
            "Referer" to initialUrl,\n\
            "Origin" to (uri.scheme + "://" + uri.host)\n\
        )\n\
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)\n\
            .setDefaultRequestProperties(defaultHeaders)\n\
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {\n\
            setParameters(buildUponParameters().setPreferredTextLanguage("id"))\n\
        }\n\
        ExoPlayer.Builder(context)\n\
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))\n\
            .setTrackSelector(trackSelector)\n\
            .build().apply {\n\
                setPlaybackSpeed(currentSpeed)\n\
                exoPlayerHolder = this\n\
                addListener(object : androidx.media3.common.Player.Listener {\n\
                    override fun onPlaybackStateChanged(playbackState: Int) {\n\
                        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {\n\
                            currentOnNextEpisode()\n\
                        }\n\
                    }\n\
                })\n\
            }\n\
    }
