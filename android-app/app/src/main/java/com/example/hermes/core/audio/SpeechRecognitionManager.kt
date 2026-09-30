package com.example.hermes.core.audio

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.hermes.core.model.TranscriptionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SpeechRecognitionManager(private val context: Context) {
    private val TAG = "SpeechRecognitionMgr"

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null

    private val _state = MutableStateFlow(TranscriptionState())
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(preferOffline: Boolean = false, language: String = Locale.getDefault().toLanguageTag()) {
        if (_state.value.isRecording) return

        try {
            // 1. Prepare Audio File Recorder
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioDir = File(context.filesDir, "audio_notes").apply { if (!exists()) mkdirs() }
            currentAudioFile = File(audioDir, "note_$timeStamp.m4a")

            startMediaRecorder(currentAudioFile!!)

            // 2. Prepare SpeechRecognizer
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(createListener())

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            speechRecognizer?.startListening(intent)

            _state.update {
                it.copy(
                    isRecording = true,
                    isTranscribing = true,
                    isOffline = preferOffline,
                    partialText = "",
                    errorMessage = null,
                    durationSec = 0,
                    audioFilePath = currentAudioFile?.absolutePath
                )
            }

            startTimer()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            _state.update {
                it.copy(
                    isRecording = false,
                    isTranscribing = false,
                    errorMessage = e.message ?: "Failed to start audio recording"
                )
            }
        }
    }

    fun stopListening() {
        if (!_state.value.isRecording) return

        timerJob?.cancel()
        timerJob = null

        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        }

        stopMediaRecorder()

        _state.update {
            it.copy(
                isRecording = false,
                isTranscribing = false,
                rmsDb = 0f
            )
        }
    }

    fun cancelListening() {
        timerJob?.cancel()
        timerJob = null

        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognizer", e)
        }

        stopMediaRecorder()

        // Delete discarded file
        currentAudioFile?.let {
            if (it.exists()) it.delete()
        }
        currentAudioFile = null

        _state.update {
            TranscriptionState()
        }
    }

    fun resetState() {
        _state.update { TranscriptionState() }
    }

    private fun startMediaRecorder(outputFile: File) {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder failed to initialize, continuing speech-only", e)
            mediaRecorder = null
        }
    }

    private fun stopMediaRecorder() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                _state.update { it.copy(durationSec = it.durationSec + 1) }
            }
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            _state.update { it.copy(rmsDb = (rmsdB.coerceIn(0f, 10f) / 10f)) }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission missing"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
                SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Recognition error ($error)"
            }
            Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")
            // Don't kill audio recording if speech had no match, just report
            _state.update {
                it.copy(
                    errorMessage = if (error == SpeechRecognizer.ERROR_NO_MATCH) null else errorMsg
                )
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull() ?: ""
            Log.d(TAG, "onResults: $recognizedText")

            _state.update { current ->
                val separator = if (current.finalText.isBlank() || current.finalText.endsWith(" ")) "" else " "
                current.copy(
                    finalText = (current.finalText + separator + recognizedText).trim(),
                    partialText = "",
                    isRecording = false,
                    isTranscribing = false
                )
            }
            stopListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull() ?: ""
            _state.update { it.copy(partialText = partial) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun destroy() {
        scope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        stopMediaRecorder()
    }
}
