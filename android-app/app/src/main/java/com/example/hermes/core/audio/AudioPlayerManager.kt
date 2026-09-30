package com.example.hermes.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPath: String? = null,
    val progress: Float = 0f,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class AudioPlayerManager(private val context: Context) {
    private val TAG = "AudioPlayerMgr"
    private var mediaPlayer: MediaPlayer? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun play(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "Audio file does not exist: $filePath")
            return
        }

        if (_state.value.currentPath == filePath && _state.value.isPlaying) {
            pause()
            return
        }

        if (_state.value.currentPath == filePath && mediaPlayer != null) {
            mediaPlayer?.start()
            _state.update { it.copy(isPlaying = true) }
            startProgressTracker()
            return
        }

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                val totalDuration = duration
                setOnCompletionListener {
                    _state.update { it.copy(isPlaying = false, progress = 0f, currentPositionMs = 0) }
                    progressJob?.cancel()
                }
                start()
                _state.update {
                    it.copy(
                        isPlaying = true,
                        currentPath = filePath,
                        durationMs = totalDuration,
                        currentPositionMs = 0,
                        progress = 0f
                    )
                }
            }
            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        progressJob?.cancel()
        _state.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
        _state.update { AudioPlayerState() }
    }

    fun seekTo(progress: Float) {
        mediaPlayer?.let { player ->
            val targetMs = (player.duration * progress.coerceIn(0f, 1f)).toInt()
            player.seekTo(targetMs)
            _state.update {
                it.copy(
                    progress = progress,
                    currentPositionMs = targetMs
                )
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying && player.duration > 0) {
                        val current = player.currentPosition
                        val dur = player.duration
                        _state.update {
                            it.copy(
                                currentPositionMs = current,
                                durationMs = dur,
                                progress = current.toFloat() / dur.toFloat()
                            )
                        }
                    }
                }
                delay(200)
            }
        }
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
