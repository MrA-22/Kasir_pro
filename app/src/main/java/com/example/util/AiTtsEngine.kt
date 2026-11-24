package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class AiTtsEngine(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val idResult = tts?.setLanguage(Locale("id", "ID"))
            if (idResult != TextToSpeech.LANG_MISSING_DATA && idResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
            } else {
                val fallbackResult = tts?.setLanguage(Locale.getDefault())
                isInitialized = fallbackResult != TextToSpeech.LANG_MISSING_DATA &&
                    fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (isInitialized) {
                android.util.Log.d("AiTtsEngine", "TTS initialized successfully")
                pendingText?.let { queued ->
                    pendingText = null
                    tts?.speak(queued, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            } else {
                android.util.Log.e("AiTtsEngine", "Language not supported or data missing")
            }
        } else {
            android.util.Log.e("AiTtsEngine", "TTS initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        android.util.Log.d("AiTtsEngine", "Attempting to speak: $text, isInitialized: $isInitialized")
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            pendingText = text
            android.util.Log.w("AiTtsEngine", "TTS not initialized yet, queued speech")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
