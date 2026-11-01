package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "VoiceAssistant"

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data class Processing(val recognizedText: String) : VoiceState
    data class Speaking(val spokenText: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

class VoiceAssistantManager(
    private val context: Context,
    private val onFinalSpeechResult: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    init {
        try {
            initializeTts()
        } catch (e: Throwable) {
            Log.w(TAG, "TTS initialization deferred or failed: ${e.message}")
        }
        try {
            initializeSpeechRecognizer()
        } catch (e: Throwable) {
            Log.w(TAG, "SpeechRecognizer initialization deferred or failed: ${e.message}")
        }
    }

    private fun initializeTts() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        val result = textToSpeech?.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "US English TTS language not supported")
                        } else {
                            isTtsReady = true
                            textToSpeech?.setSpeechRate(1.05f)
                            textToSpeech?.setPitch(1.0f)
                        }
                    } else {
                        Log.w(TAG, "TTS status returned non-success: $status")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Error in TTS onInit callback", e)
                }
            }

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceState.value = VoiceState.Speaking(_partialTranscript.value)
                }

                override fun onDone(utteranceId: String?) {
                    _voiceState.value = VoiceState.Idle
                }

                override fun onError(utteranceId: String?) {
                    _voiceState.value = VoiceState.Idle
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to initialize TTS", e)
            textToSpeech = null
        }
    }

    private fun initializeSpeechRecognizer() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition is not available on this device")
                return
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = VoiceState.Listening
                        _partialTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                        _rmsLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceState.value = VoiceState.Processing(_partialTranscript.value)
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap to speak."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Tap to try again."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection issue."
                            else -> "Voice input error ($error)"
                        }
                        _rmsLevel.value = 0f
                        _voiceState.value = VoiceState.Error(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        _rmsLevel.value = 0f
                        if (text.isNotBlank()) {
                            _partialTranscript.value = text
                            _voiceState.value = VoiceState.Processing(text)
                            onFinalSpeechResult(text)
                        } else {
                            _voiceState.value = VoiceState.Idle
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            _partialTranscript.value = text
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Throwable) {
            Log.w(TAG, "SpeechRecognizer could not be created", e)
            speechRecognizer = null
        }
    }

    fun startListening() {
        stopSpeaking()
        try {
            if (speechRecognizer == null) {
                initializeSpeechRecognizer()
            }
            val recognizer = speechRecognizer
            if (recognizer == null) {
                _voiceState.value = VoiceState.Error("Voice recognition service not available on this device")
                return
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
            _voiceState.value = VoiceState.Listening
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start speech recognition", e)
            _voiceState.value = VoiceState.Error(e.localizedMessage ?: "Voice recognition failed")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping recognition", e)
        }
    }

    fun speak(text: String) {
        try {
            if (!isTtsReady || text.isBlank() || textToSpeech == null) return
            val cleanedText = text
                .replace(Regex("[#*_`~>\\[\\]()]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            _voiceState.value = VoiceState.Speaking(cleanedText)
            textToSpeech?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "SAMSED_VOICE_UTTERANCE")
        } catch (e: Throwable) {
            Log.w(TAG, "Error speaking text", e)
        }
    }

    fun stopSpeaking() {
        try {
            if (textToSpeech?.isSpeaking == true) {
                textToSpeech?.stop()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping speech", e)
        }
        if (_voiceState.value is VoiceState.Speaking) {
            _voiceState.value = VoiceState.Idle
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Throwable) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Throwable) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        textToSpeech = null
    }
}
