package com.example.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Foreground MediaSessionService that bridges ExoPlayer to Android Media3 system controls
 * (Notification Shade, Lock Screen, MediaStyle notifications, Android Auto / Bluetooth headsets).
 */
@OptIn(UnstableApi::class)
class MediaSessionService : MediaSessionService() {

    companion object {
        const val CUSTOM_ACTION_NEXT_EPISODE = "com.example.media.NEXT_EPISODE"
        const val CUSTOM_ACTION_PREVIOUS_EPISODE = "com.example.media.PREVIOUS_EPISODE"
        const val CUSTOM_ACTION_FORWARD_10S = "com.example.media.FORWARD_10S"
        const val CUSTOM_ACTION_REWIND_10S = "com.example.media.REWIND_10S"
        const val CUSTOM_ACTION_TOGGLE_AUDIO_ONLY = "com.example.media.TOGGLE_AUDIO_ONLY"

        @Volatile
        private var activeSession: MediaSession? = null

        @Volatile
        private var nextEpisodeAction: (() -> Unit)? = null

        @Volatile
        private var previousEpisodeAction: (() -> Unit)? = null

        @Volatile
        private var toggleAudioOnlyAction: (() -> Unit)? = null

        fun setActiveSession(
            session: MediaSession?,
            onNext: (() -> Unit)? = null,
            onPrevious: (() -> Unit)? = null,
            onToggleAudioOnly: (() -> Unit)? = null
        ) {
            activeSession = session
            nextEpisodeAction = onNext
            previousEpisodeAction = onPrevious
            toggleAudioOnlyAction = onToggleAudioOnly
        }

        fun triggerNextEpisode() {
            nextEpisodeAction?.invoke()
        }

        fun triggerPreviousEpisode() {
            previousEpisodeAction?.invoke()
        }

        fun triggerToggleAudioOnly() {
            toggleAudioOnlyAction?.invoke()
        }

        /**
         * Builds a standard MediaSession with full Bluetooth and lock-screen custom command handling.
         */
        fun createMediaSession(
            context: Context,
            player: Player,
            onNext: (() -> Unit)? = null,
            onPrevious: (() -> Unit)? = null,
            onToggleAudioOnly: (() -> Unit)? = null
        ): MediaSession {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val nextCommand = SessionCommand(CUSTOM_ACTION_NEXT_EPISODE, Bundle.EMPTY)
            val prevCommand = SessionCommand(CUSTOM_ACTION_PREVIOUS_EPISODE, Bundle.EMPTY)
            val forwardCommand = SessionCommand(CUSTOM_ACTION_FORWARD_10S, Bundle.EMPTY)
            val rewindCommand = SessionCommand(CUSTOM_ACTION_REWIND_10S, Bundle.EMPTY)
            val audioOnlyCommand = SessionCommand(CUSTOM_ACTION_TOGGLE_AUDIO_ONLY, Bundle.EMPTY)

            val customLayout = listOf(
                CommandButton.Builder()
                    .setDisplayName("Episode Sebelumnya")
                    .setIconResId(android.R.drawable.ic_media_previous)
                    .setSessionCommand(prevCommand)
                    .setEnabled(onPrevious != null)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Mundur 10 Detik")
                    .setIconResId(android.R.drawable.ic_media_rew)
                    .setSessionCommand(rewindCommand)
                    .setEnabled(true)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Maju 10 Detik")
                    .setIconResId(android.R.drawable.ic_media_ff)
                    .setSessionCommand(forwardCommand)
                    .setEnabled(true)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("Episode Berikutnya")
                    .setIconResId(android.R.drawable.ic_media_next)
                    .setSessionCommand(nextCommand)
                    .setEnabled(onNext != null)
                    .build()
            )

            val callback = object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(nextCommand)
                        .add(prevCommand)
                        .add(forwardCommand)
                        .add(rewindCommand)
                        .add(audioOnlyCommand)
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands)
                        .setCustomLayout(customLayout)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CUSTOM_ACTION_NEXT_EPISODE -> {
                            triggerNextEpisode()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        CUSTOM_ACTION_PREVIOUS_EPISODE -> {
                            triggerPreviousEpisode()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        CUSTOM_ACTION_FORWARD_10S -> {
                            val newPos = (player.currentPosition + 10000L).coerceAtMost(player.duration.coerceAtLeast(0L))
                            player.seekTo(newPos)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        CUSTOM_ACTION_REWIND_10S -> {
                            val newPos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                            player.seekTo(newPos)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        CUSTOM_ACTION_TOGGLE_AUDIO_ONLY -> {
                            triggerToggleAudioOnly()
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }

                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                if (nextEpisodeAction != null) {
                                    triggerNextEpisode()
                                    return true
                                }
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                if (previousEpisodeAction != null) {
                                    triggerPreviousEpisode()
                                    return true
                                }
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                                val newPos = (player.currentPosition + 10000L).coerceAtMost(player.duration.coerceAtLeast(0L))
                                player.seekTo(newPos)
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                                val newPos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                player.seekTo(newPos)
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            }

            return MediaSession.Builder(context, player)
                .setSessionActivity(pendingIntent)
                .setCallback(callback)
                .setCustomLayout(customLayout)
                .build()
        }

        fun startService(context: Context) {
            val intent = Intent(context, MediaSessionService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore service start restrictions if activity is in background
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, MediaSessionService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return activeSession
    }

    override fun onDestroy() {
        super.onDestroy()
        activeSession = null
        nextEpisodeAction = null
        previousEpisodeAction = null
        toggleAudioOnlyAction = null
    }
}
