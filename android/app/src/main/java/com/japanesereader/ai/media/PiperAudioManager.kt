package com.japanesereader.ai.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class PiperAudioManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                        _isPlaying.value = false
                        _currentlyPlayingText.value = null
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // Fall back to system TTS on streaming failure
                    val currentText = _currentlyPlayingText.value
                    if (!currentText.isNullOrBlank()) {
                        playWithNativeTts(currentText, currentSpeed)
                    } else {
                        _isPlaying.value = false
                        _currentlyPlayingText.value = null
                    }
                }
            })
        }
    }

    private var nativeTts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentSpeed: Float = 1.0f

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentlyPlayingText = MutableStateFlow<String?>(null)
    val currentlyPlayingText: StateFlow<String?> = _currentlyPlayingText.asStateFlow()

    init {
        nativeTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = nativeTts?.setLanguage(Locale.JAPANESE)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                nativeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            _isPlaying.value = true
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            _isPlaying.value = false
                            _currentlyPlayingText.value = null
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            _isPlaying.value = false
                            _currentlyPlayingText.value = null
                        }
                    }
                })
            }
        }
    }

    /**
     * Plays Japanese audio. Prioritizes the neural Piper TTS stream URL (from Cloudflare R2).
     * If the URL is unavailable, offline, or synthetic fallback, seamlessly plays via native speech engine.
     */
    fun playAudio(
        text: String,
        playbackSpeed: Float = 1.0f,
        audioUrl: String? = null
    ) {
        if (_currentlyPlayingText.value == text && _isPlaying.value) {
            stopPlayback()
            return
        }

        stopPlayback()
        _currentlyPlayingText.value = text
        _isPlaying.value = true
        currentSpeed = playbackSpeed

        val isHttpUrl = !audioUrl.isNullOrBlank() &&
                (audioUrl.startsWith("http://") || audioUrl.startsWith("https://"))

        if (isHttpUrl) {
            try {
                val mediaItem = MediaItem.fromUri(audioUrl!!)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                playWithNativeTts(text, playbackSpeed)
            }
        } else {
            playWithNativeTts(text, playbackSpeed)
        }
    }

    private fun playWithNativeTts(text: String, speed: Float) {
        try {
            nativeTts?.setSpeechRate(speed)
            val utteranceId = "piper_${System.currentTimeMillis()}"
            val res = nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (res == TextToSpeech.ERROR) {
                _isPlaying.value = false
                _currentlyPlayingText.value = null
            }
        } catch (e: Exception) {
            _isPlaying.value = false
            _currentlyPlayingText.value = null
        }
    }

    fun stopPlayback() {
        try {
            if (exoPlayer.isPlaying) {
                exoPlayer.stop()
            }
        } catch (_: Exception) {}

        try {
            nativeTts?.stop()
        } catch (_: Exception) {}

        _isPlaying.value = false
        _currentlyPlayingText.value = null
    }

    fun release() {
        stopPlayback()
        try {
            exoPlayer.release()
        } catch (_: Exception) {}

        try {
            nativeTts?.shutdown()
        } catch (_: Exception) {}
    }
}
