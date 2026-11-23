package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceToTextParser(private val context: Context) : RecognitionListener {

    private val _state = MutableStateFlow(VoiceParserState())
    val state: StateFlow<VoiceParserState> = _state

    var onResultListener: ((String) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer?.setRecognitionListener(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startListening(languageCode: String = "id-ID") {
        _state.value = VoiceParserState(isSpeaking = true, spokenText = "", error = null)

        if (!SpeechRecognizer.isRecognitionAvailable(context) || recognizer == null) {
            _state.value = VoiceParserState(
                isSpeaking = false,
                error = "Layanan suara tidak tersedia di perangkat ini. Silakan ketik perintah secara manual."
            )
            return
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Mendengarkan perintah kasir...")
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            _state.value = VoiceParserState(
                isSpeaking = false,
                error = "Gagal memulai perekam suara: ${e.localizedMessage}"
            )
        }
    }

    fun stopListening() {
        _state.value = _state.value.copy(isSpeaking = false)
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearState() {
        _state.value = VoiceParserState()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _state.value = _state.value.copy(error = null)
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _state.value = _state.value.copy(isSpeaking = false)
    }

    override fun onError(error: Int) {
        if (error == SpeechRecognizer.ERROR_CLIENT) return
        
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Suara tidak terdeteksi. Coba bicara lebih dekat atau ketik perintah."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Waktu bicara habis. Silakan tekan ikon mikrofon lagi."
            SpeechRecognizer.ERROR_AUDIO -> "Gagal merekam audio mikrofon."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diberikan."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Koneksi internet bermasalah."
            else -> "Tidak dapat mengenali suara (Kode $error). Silakan coba ketik perintah."
        }

        _state.value = VoiceParserState(
            spokenText = "",
            isSpeaking = false,
            error = errorMsg
        )
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.getOrNull(0) ?: ""
        _state.value = VoiceParserState(
            spokenText = text,
            isSpeaking = false,
            error = if (text.isBlank()) "Suara tidak terdeteksi jelas." else null
        )

        if (text.isNotBlank()) {
            onResultListener?.invoke(text)
        }
    }

    override fun onPartialResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.getOrNull(0) ?: ""
        if (text.isNotBlank()) {
            _state.value = _state.value.copy(spokenText = text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}

data class VoiceParserState(
    val spokenText: String = "",
    val isSpeaking: Boolean = false,
    val error: String? = null
)
